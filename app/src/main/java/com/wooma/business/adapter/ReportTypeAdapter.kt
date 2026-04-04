package com.wooma.business.adapter

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.wooma.business.R
import com.wooma.business.activities.report.CheckoutCopyOptionActivity
import com.wooma.business.activities.report.ConfigureReportActivity
import com.wooma.business.model.ReportType

class ReportTypeAdapter(
    val context: Context,
    originalList: MutableList<ReportType>,
    val propertyId: String
) : RecyclerView.Adapter<ReportTypeAdapter.ViewHolder>() {

    private var filteredList = originalList.toMutableList()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val view: View = view.findViewById(R.id.view)
        val tvReportType: TextView = view.findViewById(R.id.tvReportType)
        val tvReportTypeDetail: TextView = view.findViewById(R.id.tvReportTypeDetail)
        val reportTypeLayout: ConstraintLayout = view.findViewById(R.id.reportTypeLayout)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_report_type, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = filteredList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.tvReportType.text = filteredList[position].display_name
        holder.tvReportTypeDetail.text = filteredList[position].description
        holder.reportTypeLayout.setOnClickListener {
            val reportType = filteredList[position]
            val isCheckout = reportType.type_code.contains("check_out", ignoreCase = true) ||
                    reportType.type_code.contains("checkout", ignoreCase = true)
            val targetActivity = if (isCheckout) CheckoutCopyOptionActivity::class.java
                                 else ConfigureReportActivity::class.java
            context.startActivity(
                Intent(context, targetActivity)
                    .putExtra("reportTypeId", reportType.id)
                    .putExtra("propertyId", propertyId)
            )
        }

        if (filteredList.size - 1 == position) {
            holder.view.visibility = View.GONE
        } else {
            holder.view.visibility = View.VISIBLE
        }
    }

    fun updateList(list: List<ReportType>) {
        filteredList = list.toMutableList()
        notifyDataSetChanged()
    }
}
