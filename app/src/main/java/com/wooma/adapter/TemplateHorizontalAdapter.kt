package com.wooma.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.wooma.R
import com.wooma.model.Room
import com.wooma.model.Template

class TemplateHorizontalAdapter(
    val context: Context,
    private val originalList: MutableList<Template>,
    val itemClick: OnItemClickInterface

) : RecyclerView.Adapter<TemplateHorizontalAdapter.ViewHolder>() {
    interface OnItemClickInterface {
        fun onItemClick(item: ArrayList<Room>)
    }

    var selectedPosition = 0
    private var filteredList = originalList.toMutableList()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_report_template, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = filteredList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.tvName.text = filteredList[position].name

        if (selectedPosition == position) {
            holder.tvName.setBackgroundDrawable(
                ContextCompat.getDrawable(
                    context,
                    R.drawable.bg_button_green_disabled
                )
            )
        } else {
            holder.tvName.setBackgroundDrawable(
                ContextCompat.getDrawable(
                    context,
                    R.drawable.dialog_rounded_bg
                )
            )
        }

        holder.tvName.setOnClickListener {
            selectedPosition = position
            itemClick.onItemClick(filteredList[position].rooms)
            notifyDataSetChanged()
        }

        /*   holder.propertyMainLayout.setOnClickListener {
               context.startActivity(Intent(context, ReportListingActivity::class.java))
           }*/
    }

    fun updateList(list: List<Template>) {
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
