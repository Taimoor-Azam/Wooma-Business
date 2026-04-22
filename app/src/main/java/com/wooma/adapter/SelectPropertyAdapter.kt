package com.wooma.adapter

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.wooma.activities.report.SelectReportTypeActivity
import com.wooma.R
import com.wooma.model.Property

class SelectPropertyAdapter(
    val context: Context,
    private val originalList: MutableList<Property>,
    val isFromProperty: Boolean,
    var itemClick: onPropertyClickInterface? = null
) : RecyclerView.Adapter<SelectPropertyAdapter.ViewHolder>() {

    private var filteredList = originalList.toMutableList()

    interface onPropertyClickInterface {
        fun onPropertyClick(item: Property)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val address: TextView = view.findViewById(R.id.tvAddress)
        val propertyMainLayout: ConstraintLayout = view.findViewById(R.id.propertyMainLayout)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_select_property, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = filteredList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.address.text = filteredList[position].address

        holder.propertyMainLayout.setOnClickListener {
            if (isFromProperty) {

                context.startActivity(
                  Intent(
                      context,
                      SelectReportTypeActivity::class.java
                  ).putExtra("propertyId", filteredList[position].id)
              )
                /*context.startActivity(
                    Intent(
                        context,
                        ReportListingActivity::class.java
                    ).putExtra("propertyId", filteredList[position].id)
                )*/
            } else {
                itemClick?.onPropertyClick(filteredList[position])

            }
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
