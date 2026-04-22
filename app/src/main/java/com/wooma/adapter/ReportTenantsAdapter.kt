package com.wooma.adapter

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.wooma.R
import com.wooma.model.TenantReview
import com.wooma.model.enums.TenantReportStatus

class ReportTenantsAdapter(
    val context: Context,
    private val originalList: MutableList<TenantReview>,
    private val reportId: String = "",
    private val reportStatus: String = "",
    private val onTenantClick: ((TenantReview) -> Unit)? = null
) : RecyclerView.Adapter<ReportTenantsAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        //        val tvRoom: TextView = view.findViewById(R.id.tvRoom)
        val tenantLayout: LinearLayout = view.findViewById(R.id.tenantLayout)
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvTenantEmail: TextView = view.findViewById(R.id.tvTenantEmail)
        val tvTenantPhone: TextView = view.findViewById(R.id.tvTenantPhone)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val ivArrow: ImageView = view.findViewById(R.id.ivArrow)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_report_tenant, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = originalList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = originalList.get(position)
        if (reportStatus == TenantReportStatus.COMPLETED.value) {
            holder.tenantLayout.setBackgroundResource(R.drawable.bg_completed_tenant_item)
        } else {
            holder.tenantLayout.setBackgroundResource(R.drawable.bg_edittext)
        }

        holder.tvName.text = item.first_name + " " + item.last_name
        holder.tvTenantEmail.text = item.email_address
        if (!item.mobile_number.isNullOrEmpty()) {
            holder.tvTenantPhone.visibility = View.VISIBLE
            holder.tvTenantPhone.text = item.mobile_number
        } else {
            holder.tvTenantPhone.visibility = View.GONE
        }

        if (item.is_submitted) {
            holder.ivArrow.visibility = View.GONE
            holder.tvStatus.text = "Signed"
            ViewCompat.setBackgroundTintList(
                holder.tvStatus,
                ColorStateList.valueOf(ContextCompat.getColor(context, R.color.complete_clr))
            )
        } else if (reportStatus == "tenant_review") {
            holder.ivArrow.visibility = View.VISIBLE

            holder.tvStatus.text = "Pending"
            ViewCompat.setBackgroundTintList(
                holder.tvStatus,
                ColorStateList.valueOf(Color.parseColor("#FFF3CD"))
            )
        } else {
            holder.ivArrow.visibility = View.GONE
            holder.tvStatus.text = "Not Signed"
            ViewCompat.setBackgroundTintList(
                holder.tvStatus,
                ColorStateList.valueOf(ContextCompat.getColor(context, R.color.complete_clr))
            )
        }

        holder.tenantLayout.setOnClickListener {
            if (!item.is_submitted) {
                onTenantClick?.invoke(item)
            }
        }
    }
}
