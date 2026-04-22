package com.wooma.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.wooma.R
import com.wooma.model.Rooms

class AddRoomAdapter(
    val context: Context,
    private val originalList: MutableList<Rooms>,
) : RecyclerView.Adapter<AddRoomAdapter.ViewHolder>() {

    private var filteredList = originalList.toMutableList()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvRoom: TextView = view.findViewById(R.id.tvRoom)
        val propertyMainLayout: ConstraintLayout = view.findViewById(R.id.propertyMainLayout)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_add_rooms, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = filteredList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.tvRoom.text = filteredList[position].address

        holder.propertyMainLayout.setOnClickListener {
        }
    }

    fun updateList(list: List<Rooms>) {
        filteredList = list.toMutableList()
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        filteredList = if (query.isEmpty()) {
            originalList.toMutableList()
        } else {
            originalList.filter {
                it.address.contains(query, true)
            }.toMutableList()
        }
        notifyDataSetChanged()
    }
}
