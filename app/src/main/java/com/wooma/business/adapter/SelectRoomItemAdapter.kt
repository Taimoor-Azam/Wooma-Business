package com.wooma.business.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.wooma.business.R

class SelectRoomItemAdapter(
    private val allItems: MutableList<String>,
    private val checkedItems: MutableSet<String>
) : RecyclerView.Adapter<SelectRoomItemAdapter.Holder>() {

    private val filteredItems = mutableListOf<String>().apply { addAll(allItems) }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox: ImageView = view.findViewById(R.id.checkbox)
        val tvItemName: TextView = view.findViewById(R.id.tvItemName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_select_room_item, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val name = filteredItems[position]
        holder.tvItemName.text = name
        holder.checkbox.isSelected = checkedItems.contains(name)

        holder.itemView.setOnClickListener {
            if (checkedItems.contains(name)) {
                checkedItems.remove(name)
            } else {
                checkedItems.add(name)
            }
            holder.checkbox.isSelected = checkedItems.contains(name)
        }
    }

    override fun getItemCount() = filteredItems.size

    fun addCustomItem(name: String) {
        if (!allItems.contains(name)) {
            allItems.add(0, name)
        }
        checkedItems.add(name)
        filter("")
    }

    fun filter(query: String) {
        filteredItems.clear()
        if (query.isBlank()) {
            filteredItems.addAll(allItems)
        } else {
            filteredItems.addAll(allItems.filter { it.contains(query, ignoreCase = true) })
        }
        notifyDataSetChanged()
    }
}
