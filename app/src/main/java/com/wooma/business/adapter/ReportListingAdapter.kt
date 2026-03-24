package com.wooma.business.adapter

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.wooma.business.R
import com.wooma.business.activities.report.InventoryListingActivity
import com.wooma.business.customs.Utils
import com.wooma.business.model.Report
import com.wooma.business.model.enums.TenantReportStatus

class ReportListingAdapter(
    val context: Context,
    private val originalList: MutableList<Report>
) : RecyclerView.Adapter<ReportListingAdapter.ViewHolder>() {

    private var filteredList = originalList.toMutableList()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val tvReviewDate: TextView = view.findViewById(R.id.tvReviewDate)
        val tvReportType: TextView = view.findViewById(R.id.tvReportType)
        val tvStatusBtn: TextView = view.findViewById(R.id.tvStatusBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.report_listing_item, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = filteredList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = filteredList[position]

        if (item.report_type != null && item.report_type.display_name != null)
            holder.tvReportType.text = item.report_type.display_name

        holder.tvStatus.text = item.status

        holder.tvReviewDate.text = Utils.formatDate(item.created_at)

        holder.tvStatusBtn.setOnClickListener {
            context.startActivity(
                Intent(context, InventoryListingActivity::class.java)
                    .putExtra("reportStatus", item.status)
                    .putExtra("reportId", item.id)
                    .putExtra("reportTypeName", item.report_type?.display_name)
                    .putExtra("reportTypeId", item.report_type?.id)
            )
        }

        if (item.status == TenantReportStatus.IN_PROGRESS.value) {
            holder.tvStatus.text = "In Progress"
            holder.tvStatus.setBackgroundDrawable(
                ContextCompat.getDrawable(
                    context,
                    R.drawable.bg_report_status
                )
            )

            holder.tvStatus.setTextColor(
                ContextCompat.getColor(
                    context,
                    R.color.report_progress_clr
                )
            )
            holder.tvStatusBtn.text = "Continue"
        } else if (item.status == TenantReportStatus.COMPLETED.value) {
            holder.tvStatusBtn.text = "View Report"

            holder.tvStatus.setBackgroundDrawable(
                ContextCompat.getDrawable(
                    context,
                    R.drawable.bg_report_completed
                )
            )

            holder.tvStatus.setTextColor(
                ContextCompat.getColor(
                    context,
                    R.color.green
                )
            )
        } else {
            if (item.status == TenantReportStatus.TENANT_REVIEW.value) {
                holder.tvStatus.text = "Tenant Review"
                holder.tvStatusBtn.text = "View Report"

                holder.tvStatus.setBackgroundDrawable(
                    ContextCompat.getDrawable(
                        context,
                        R.drawable.bg_report_review
                    )
                )

                holder.tvStatus.setTextColor(
                    ContextCompat.getColor(
                        context,
                        R.color.blue
                    )
                )
            }
        }
    }

    fun updateList(list: List<Report>) {
        filteredList = list.toMutableList()
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        filteredList = if (query.isEmpty()) {
            originalList.toMutableList()
        } else {
            originalList.filter {
                it.report_type?.display_name?.contains(query, true) == true
            }.toMutableList()
        }
        notifyDataSetChanged()
    }
}
