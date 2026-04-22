package com.wooma.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.wooma.R
import com.wooma.model.Room

class ReportRoomsAdapter(
    val context: Context,
    private val originalList: MutableList<Room>,
) : RecyclerView.Adapter<ReportRoomsAdapter.ViewHolder>() {

    private var filteredList = originalList.toMutableList()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val address: TextView = view.findViewById(R.id.tvAddress)
        val cbReport: ImageView = view.findViewById(R.id.cbReport)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_report_rooms, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = filteredList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = filteredList[position]

        holder.address.text = item.name

        holder.cbReport.isSelected = item.isSelected

        holder.cbReport.setOnClickListener {
//            holder.cbReport.isSelected = !holder.cbReport.isSelected
            item.isSelected = !holder.cbReport.isSelected
            notifyItemChanged(position)
        }

        /*   holder.propertyMainLayout.setOnClickListener {
               context.startActivity(Intent(context, ReportListingActivity::class.java))
           }*/
    }

    fun updateList(list: List<Room>) {
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

    fun getSelectedRooms(): List<Room> {
        return filteredList.filter { it.isSelected }
    }
}
