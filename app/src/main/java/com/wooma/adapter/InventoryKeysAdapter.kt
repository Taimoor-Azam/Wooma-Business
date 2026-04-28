package com.wooma.adapter

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wooma.activities.report.otherItems.AddEditKeysActivity
import com.wooma.R
import com.wooma.data.network.ApiClient
import com.wooma.model.ImageItem
import com.wooma.model.enums.TenantReportStatus
import com.wooma.model.KeyItem

class InventoryKeysAdapter(
    val context: Context,
    private val originalList: MutableList<KeyItem>,
    val reportId: String,
    val reportStatus: String = "",
    val showTimestamp: Boolean = true
) : RecyclerView.Adapter<InventoryKeysAdapter.ViewHolder>() {
    private var filteredList = originalList.toMutableList()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvItemName: TextView = view.findViewById(R.id.tvItemName)
        val tvQuantity: TextView = view.findViewById(R.id.tvQuantity)
        val qtyLayout: LinearLayout = view.findViewById(R.id.qtyLayout)
        val tvWhatForLabel: TextView = view.findViewById(R.id.tvWhatForLabel)
        val tvWhatFor: TextView = view.findViewById(R.id.tvWhatFor)
        val rvImages: RecyclerView = view.findViewById(R.id.rvImages)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_keys, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = filteredList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = filteredList[position]

        holder.tvItemName.text = item.name

        val qty = item.no_of_keys ?: 0
        holder.tvQuantity.visibility = if (qty > 0) View.VISIBLE else View.GONE
        holder.qtyLayout.visibility = if (qty > 0) View.VISIBLE else View.GONE
        holder.tvQuantity.text = "$qty"

        val noteVisible = !item.note.isNullOrEmpty()
        holder.tvWhatForLabel.visibility = if (noteVisible) View.VISIBLE else View.GONE
        holder.tvWhatFor.visibility = if (noteVisible) View.VISIBLE else View.GONE
        holder.tvWhatFor.text = item.note ?: ""

        val imageItems = item.attachments.map { ImageItem.Remote(it.id, "${ApiClient.IMAGE_BASE_URL}${it.storageKey}") }.toMutableList<ImageItem>()
        holder.rvImages.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        holder.rvImages.adapter = ImageAdapter(imageItems, showDelete = false, title = item.name)

        holder.itemView.setOnClickListener {
            if (reportStatus != TenantReportStatus.IN_PROGRESS.value) return@setOnClickListener
            context.startActivity(
                Intent(context, AddEditKeysActivity::class.java).putExtra(
                    "keyItem",
                    filteredList[position]
                ).putExtra("reportId", reportId)
                    .putExtra("showTimestamp", showTimestamp)
            )
        }
    }

    fun updateList(list: List<KeyItem>) {
        filteredList = list.toMutableList()
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        filteredList = if (query.isEmpty()) {
            originalList.toMutableList()
        } else {
            originalList.filter {
                it.name.contains(query, true)
            }.toMutableList()
        }
        notifyDataSetChanged()
    }
}
