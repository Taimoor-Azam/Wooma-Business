package com.wooma.business.adapter

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wooma.business.R
import com.wooma.business.activities.report.otherItems.AddEditDetectorActivity
import com.wooma.business.activities.report.otherItems.AddEditKeysActivity
import com.wooma.business.data.network.ApiClient
import com.wooma.business.model.DetectorItem
import com.wooma.business.model.ImageItem

class InventoryDetectorAdapter(
    val context: Context,
    private val originalList: MutableList<DetectorItem>,
    val reportId: String,
) : RecyclerView.Adapter<InventoryDetectorAdapter.ViewHolder>() {

    private var filteredList = originalList.toMutableList()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvItemName: TextView = view.findViewById(R.id.tvItemName)
        val tvLocation: TextView = view.findViewById(R.id.tvLocation)
        val tvResult: TextView = view.findViewById(R.id.tvResult)
        val rvImages: RecyclerView = view.findViewById(R.id.rvImages)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_detector, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = filteredList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = filteredList[position]

        holder.tvItemName.text = item.name
        holder.tvLocation.text = item.location.toString()
        holder.tvResult.text = item.note ?: ""

        val imageItems = item.attachments.map { ImageItem.Remote(it.id, "${ApiClient.IMAGE_BASE_URL}${it.storageKey}") }.toMutableList<ImageItem>()
        holder.rvImages.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        holder.rvImages.adapter = ImageAdapter(imageItems, showDelete = false)

        holder.itemView.setOnClickListener {
            context.startActivity(
                Intent(context, AddEditDetectorActivity::class.java).putExtra(
                    "detectorItem",
                    filteredList[position]
                ).putExtra("reportId", reportId)
            )
        }
    }

    fun updateList(list: List<DetectorItem>) {
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
