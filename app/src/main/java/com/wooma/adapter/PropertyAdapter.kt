package com.wooma.adapter

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.wooma.activities.report.ReportListingActivity
import com.wooma.R
import com.wooma.model.Property

class PropertyAdapter(
    val context: Context,
    private val originalList: MutableList<Property>,
) : PagingDataAdapter<Property, PropertyAdapter.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Property>() {
            override fun areItemsTheSame(oldItem: Property, newItem: Property) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Property, newItem: Property) =
                oldItem == newItem
        }
    }

    private var filteredList = originalList.toMutableList()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAddress: TextView = view.findViewById(R.id.tvAddress)
        val tvAddressTwo: TextView = view.findViewById(R.id.tvAddressTwo)
        val tvTotalReports: TextView = view.findViewById(R.id.tvTotalReports)
        val propertyMainLayout: ConstraintLayout = view.findViewById(R.id.propertyMainLayout)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_property, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = filteredList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = filteredList[position]
        holder.tvAddress.text = item.address + " " + if (!item.addressLine2.isNullOrEmpty()) ", "+ item.addressLine2 else ""
        holder.tvAddressTwo.text = item.city+", "+ item.postcode
        holder.tvTotalReports.text = "${item.noOfReports} reports"

        if (item.noOfReports != 0) {
            holder.tvTotalReports.background =
                ContextCompat.getDrawable(context, R.drawable.bg_report_completed)
            holder.tvTotalReports.setTextColor(ContextCompat.getColor(context, R.color.green))
        } else {
            holder.tvTotalReports.background =
                ContextCompat.getDrawable(context, R.drawable.bg_border_info)
            holder.tvTotalReports.setTextColor(ContextCompat.getColor(context, R.color.black))
        }

        holder.propertyMainLayout.setOnClickListener {
            context.startActivity(
                Intent(
                    context,
                    ReportListingActivity::class.java
                ).putExtra("propertyId", item.id)
            )
        }
    }

    fun updateList(list: List<Property>) {
        filteredList = list.toMutableList()
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        filteredList = if (query.isEmpty()) {
            originalList.toMutableList()
        } else {
            originalList.filter {
                it.address?.contains(query, true) == true
            }.toMutableList()
        }
        notifyDataSetChanged()
    }
}
