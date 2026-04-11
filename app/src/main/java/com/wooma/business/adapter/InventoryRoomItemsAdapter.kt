package com.wooma.business.adapter

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wooma.business.R
import com.wooma.business.activities.report.InventoryRoomItemActivity
import com.wooma.business.data.network.ApiClient
import com.wooma.business.model.ImageItem
import com.wooma.business.model.RoomItem
import com.wooma.business.model.PropertyReportType
import com.wooma.business.model.enums.TenantReportStatus
import java.util.Locale

class InventoryRoomItemsAdapter(
    val context: Context,
    private val originalList: MutableList<RoomItem>,
    val reportId: String,
    val roomId: String,
    val reportStatus: String,
    val reportType: PropertyReportType? = null,
) : RecyclerView.Adapter<InventoryRoomItemsAdapter.ViewHolder>() {

    private var filteredList = originalList.toMutableList()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvItemName: TextView = view.findViewById(R.id.tvItemName)
        val tvDescription: TextView = view.findViewById(R.id.tvDescription)
        val tvCondition: TextView = view.findViewById(R.id.tvCondition)
        val tvCleanliness: TextView = view.findViewById(R.id.tvCleanliness)
        val tvNotes: TextView = view.findViewById(R.id.tvNotes)
        val ivCleanliness: ImageView = view.findViewById(R.id.ivCleanliness)
        val ivConditionIcon: ImageView = view.findViewById(R.id.ivConditionIcon)
        val rvImages: RecyclerView = view.findViewById(R.id.rvImages)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_room_items, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = filteredList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = filteredList[position]

        holder.tvItemName.text = item.name
        holder.tvDescription.text = item.description ?: ""
        holder.tvNotes.text = item.note ?: ""

        val imageItems = item.attachments
            ?.mapNotNull { att ->
                att.id?.let { id ->
                    att.storageKey?.let { key ->
                        ImageItem.Remote(
                            id,
                            "${ApiClient.IMAGE_BASE_URL}$key"
                        )
                    }
                }
            }
            ?.toMutableList<ImageItem>() ?: mutableListOf()
        holder.rvImages.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        holder.rvImages.adapter =
            ImageAdapter(imageItems, showDelete = false, title = item.name ?: "")


        if (item.general_condition?.equals("poor") == true || item.general_condition?.equals("unacceptable") == true) {
            holder.ivConditionIcon.setImageDrawable(
                ContextCompat.getDrawable(
                    context,
                    R.drawable.svg_poor
                )
            )
        } else if (item.general_condition?.equals("n/a") == true
            || item.general_condition?.equals("N/A") == true
        ) {
            holder.ivConditionIcon.setImageDrawable(
                ContextCompat.getDrawable(
                    context,
                    R.drawable.svg_n_a
                )
            )
        } else {
            holder.ivConditionIcon.setImageDrawable(
                ContextCompat.getDrawable(
                    context,
                    R.drawable.svg_excellent
                )
            )
        }

        if (item.general_cleanliness?.equals("poor") == true || item.general_cleanliness?.equals("unacceptable") == true
        ) {
            holder.ivCleanliness.setImageDrawable(
                ContextCompat.getDrawable(
                    context,
                    R.drawable.svg_poor
                )
            )
        } else if (item.general_cleanliness?.equals("N/A") == true) {
            holder.ivCleanliness.setImageDrawable(
                ContextCompat.getDrawable(
                    context,
                    R.drawable.svg_n_a
                )
            )
        } else {
            holder.ivCleanliness.setImageDrawable(
                ContextCompat.getDrawable(
                    context,
                    R.drawable.svg_excellent
                )
            )
        }
        holder.tvCleanliness.text = item.general_cleanliness?.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(
                Locale.ROOT
            ) else it.toString()
        } ?: "Excellent"

        holder.tvCondition.text = item.general_condition?.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(
                Locale.ROOT
            ) else it.toString()
        } ?: "Excellent"

        holder.itemView.setOnClickListener {
            if (reportStatus == TenantReportStatus.IN_PROGRESS.value) {
                context.startActivity(
                    Intent(context, InventoryRoomItemActivity::class.java).putExtra(
                        "roomItem",
                        filteredList[position]
                    ).putExtra("roomId", roomId)
                        .putExtra("reportId", reportId)
                        .putExtra("reportType", reportType)
                )
            }
        }
    }

    fun updateList(list: List<RoomItem>) {
        filteredList = list.toMutableList()
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        filteredList = if (query.isEmpty()) {
            originalList.toMutableList()
        } else {
            originalList.filter {
                it.name?.contains(query, true) == true
            }.toMutableList()
        }
        notifyDataSetChanged()
    }
}
