package com.wooma.business.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.wooma.business.databinding.ItemCheckoutReportBinding
import com.wooma.business.model.Report

class CheckoutReportAdapter(
    private val context: Context,
    private val propertyAddress: String,
    private var reportList: MutableList<Report>,
    private val onItemClick: (Report) -> Unit
) : RecyclerView.Adapter<CheckoutReportAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemCheckoutReportBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCheckoutReportBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount() = reportList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val report = reportList[position]
        with(holder.binding) {
            tvReportType.text = report.report_type?.display_name ?: "Inventory"
            tvAddress.text = propertyAddress
            tvDate.text = formatDate(report.created_at)
            itemLayout.setOnClickListener { onItemClick(report) }
        }
    }

    private fun formatDate(dateStr: String): String {
        return try {
            val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.getDefault())
            val outputFormat = java.text.SimpleDateFormat("dd MMM yy", java.util.Locale.getDefault())
            val date = inputFormat.parse(dateStr)
            if (date != null) outputFormat.format(date) else dateStr
        } catch (e: Exception) {
            dateStr
        }
    }

    fun updateList(list: List<Report>) {
        reportList = list.toMutableList()
        notifyDataSetChanged()
    }
}
