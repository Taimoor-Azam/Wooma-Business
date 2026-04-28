package com.wooma.adapter

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wooma.activities.report.otherItems.AddEditMeterActivity
import com.wooma.R
import com.wooma.data.network.ApiClient
import com.wooma.model.ImageItem
import com.wooma.model.enums.TenantReportStatus
import com.wooma.model.Meter

class InventoryMetersAdapter(
    val context: Context,
    private val originalList: MutableList<Meter>,
    val reportId: String,
    val reportStatus: String = "",
    val showTimestamp: Boolean = true
) : RecyclerView.Adapter<InventoryMetersAdapter.ViewHolder>() {

    private var filteredList = originalList.toMutableList()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvItemName: TextView = view.findViewById(R.id.tvItemName)
        val tvSerialNum: TextView = view.findViewById(R.id.tvSerialNum)
        val tvSerialLabel: TextView = view.findViewById(R.id.tvSerialLabel)
        val tvReadingLabel: TextView = view.findViewById(R.id.tvReadingLabel)
        val tvReading: TextView = view.findViewById(R.id.tvReading)
        val tvLocationLabel: TextView = view.findViewById(R.id.tvLocationLabel)
        val tvLocation: TextView = view.findViewById(R.id.tvLocation)
        val rvImages: RecyclerView = view.findViewById(R.id.rvImages)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_meters, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = filteredList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = filteredList[position]

        holder.tvItemName.text = item.name

        val serialVisible = !item.serial_number.isNullOrEmpty()
        holder.tvSerialNum.visibility = if (serialVisible) View.VISIBLE else View.GONE
        holder.tvSerialLabel.visibility = if (serialVisible) View.VISIBLE else View.GONE
        holder.tvSerialNum.text = item.serial_number ?: ""

        val readingVisible = !item.reading.isNullOrEmpty()
        holder.tvReadingLabel.visibility = if (readingVisible) View.VISIBLE else View.GONE
        holder.tvReading.visibility = if (readingVisible) View.VISIBLE else View.GONE
        holder.tvReading.text = item.reading ?: ""

        val locationVisible = !item.location.isNullOrEmpty()
        holder.tvLocationLabel.visibility = if (locationVisible) View.VISIBLE else View.GONE
        holder.tvLocation.visibility = if (locationVisible) View.VISIBLE else View.GONE
        holder.tvLocation.text = item.location ?: ""

        val imageItems = item.attachments.map { ImageItem.Remote(it.id, "${ApiClient.IMAGE_BASE_URL}${it.storageKey}") }.toMutableList<ImageItem>()
        holder.rvImages.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        holder.rvImages.adapter = ImageAdapter(imageItems, showDelete = false, title = item.name)

        holder.itemView.setOnClickListener {
            if (reportStatus != TenantReportStatus.IN_PROGRESS.value) return@setOnClickListener
            context.startActivity(
                Intent(context, AddEditMeterActivity::class.java).putExtra(
                    "meterItem",
                    filteredList[position]
                ).putExtra("reportId", reportId)
                    .putExtra("showTimestamp", showTimestamp)
            )
        }
    }

    fun updateList(list: List<Meter>) {
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
