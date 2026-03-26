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
import com.wooma.business.activities.report.InspectionRoomActivity
import com.wooma.business.activities.report.InventoryRoomItemsListActivity
import com.wooma.business.model.PropertyReportType
import com.wooma.business.model.RoomsResponse
import com.wooma.business.model.enums.ReportTypes

class InventoryRoomsAdapter(
    val context: Context,
    private val originalList: MutableList<RoomsResponse>,
    val reportId: String,
    val reportStatus: String,
    val reportType: PropertyReportType? = null,
    private val onDeleteRoom: ((roomId: String?, position: Int) -> Unit)? = null,
) : RecyclerView.Adapter<InventoryRoomsAdapter.ViewHolder>() {

    private var filteredList = originalList.toMutableList()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val address: TextView = view.findViewById(R.id.tvAddress)
        val roomMainLayout: ConstraintLayout = view.findViewById(R.id.roomMainLayout)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_inventory_room, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = filteredList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val room = filteredList[position]
        holder.address.text = room.name

        holder.roomMainLayout.setOnClickListener {
            val isInspection = reportType?.type_code == ReportTypes.INSPECTION.value
            if (isInspection) {
                context.startActivity(
                    Intent(context, InspectionRoomActivity::class.java)
                        .putExtra("room", room)
                        .putExtra("reportId", reportId)
                        .putExtra("reportStatus", reportStatus)
                )
            } else {
                context.startActivity(
                    Intent(context, InventoryRoomItemsListActivity::class.java)
                        .putParcelableArrayListExtra("roomItems", room.items)
                        .putExtra("roomName", room.name ?: "")
                        .putExtra("roomId", room.id ?: "")
                        .putExtra("reportId", reportId)
                        .putExtra("reportStatus", reportStatus)
                )
            }
        }

        holder.roomMainLayout.setOnLongClickListener {
            onDeleteRoom?.invoke(room.id ?: "", holder.adapterPosition)
            true
        }
    }

    fun updateList(list: List<RoomsResponse>) {
        filteredList = list.toMutableList()
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        filteredList = if (query.isEmpty()) {
            originalList.toMutableList()
        } else {
            originalList.filter {
                it.name?.contains(query, true) == true
            }.toMutableList()
        }
        notifyDataSetChanged()
    }
}
