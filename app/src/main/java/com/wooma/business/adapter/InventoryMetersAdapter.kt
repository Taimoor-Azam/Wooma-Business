package com.wooma.business.adapter

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.wooma.business.R
import com.wooma.business.activities.report.otherItems.AddEditMeterActivity
import com.wooma.business.model.Meter

class InventoryMetersAdapter(
    val context: Context,
    private val originalList: MutableList<Meter>,
    val reportId: String,
) : RecyclerView.Adapter<InventoryMetersAdapter.ViewHolder>() {

    private var filteredList = originalList.toMutableList()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvItemName: TextView = view.findViewById(R.id.tvItemName)
        val tvSerialNum: TextView = view.findViewById(R.id.tvSerialNum)
        val tvReading: TextView = view.findViewById(R.id.tvReading)
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
        holder.tvSerialNum.text = item.serial_number ?: ""
        holder.tvReading.text = item.reading ?: ""
        holder.tvLocation.text = item.location ?: ""

        holder.itemView.setOnClickListener {
            context.startActivity(
                Intent(context, AddEditMeterActivity::class.java).putExtra(
                    "meterItem",
                    filteredList[position]
                ).putExtra("reportId", reportId)
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
