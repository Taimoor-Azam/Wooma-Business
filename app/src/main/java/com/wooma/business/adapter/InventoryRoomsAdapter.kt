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
import com.wooma.business.activities.report.InventoryRoomItemsListActivity
import com.wooma.business.model.RoomsResponse

class InventoryRoomsAdapter(
    val context: Context,
    private val originalList: MutableList<RoomsResponse>,
    val reportId: String,
    val reportStatus: String,
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
        holder.address.text = filteredList[position].name

        holder.roomMainLayout.setOnClickListener {
            context.startActivity(
                Intent(
                    context,
                    InventoryRoomItemsListActivity::class.java
                ).putParcelableArrayListExtra("roomItems", filteredList[position].items)
                    .putExtra("roomName", filteredList[position].name)
                    .putExtra("roomId", filteredList[position].id)
                    .putExtra("reportId", reportId)
                    .putExtra("reportStatus", reportStatus)
            )
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
                it.name.contains(query, true)
            }.toMutableList()
        }
        notifyDataSetChanged()
    }
}
