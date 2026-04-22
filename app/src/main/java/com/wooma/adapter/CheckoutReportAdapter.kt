package com.wooma.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.wooma.databinding.ItemCheckoutReportBinding
import com.wooma.model.Report
import java.text.SimpleDateFormat
import java.util.Locale

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
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd MMM yy", Locale.getDefault())
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
