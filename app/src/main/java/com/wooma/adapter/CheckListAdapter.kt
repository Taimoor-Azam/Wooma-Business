package com.wooma.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.suke.widget.SwitchButton
import com.wooma.R
import com.wooma.model.Checklist

class CheckListAdapter(
    private var list: MutableList<Checklist>,
    private val isReadOnly: Boolean = false,
    private val onToggle: (id: String, isActive: Boolean) -> Unit,
    private val onClick: (checklist: Checklist) -> Unit
) : RecyclerView.Adapter<CheckListAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvChecklistName: TextView = view.findViewById(R.id.tvChecklistName)
        val ivArrow: ImageView = view.findViewById(R.id.ivArrow)
        val switchButton: SwitchButton = view.findViewById(R.id.switchButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_checklist_row, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.tvChecklistName.text = item.name
        holder.ivArrow.visibility = if (item.is_active) View.VISIBLE else View.GONE

        // Set switch without triggering listener
        holder.switchButton.tag = true
        holder.switchButton.setOnCheckedChangeListener(null)
        holder.switchButton.isChecked = item.is_active
        holder.switchButton.isEnabled = !isReadOnly
        holder.switchButton.tag = false

        holder.switchButton.setOnCheckedChangeListener { _, isChecked ->
            if (holder.switchButton.tag as? Boolean == true) return@setOnCheckedChangeListener
            if (isChecked == item.is_active) return@setOnCheckedChangeListener
            onToggle(item.id, isChecked)
        }

        holder.itemView.setOnClickListener {
            if (item.is_active) onClick(item)
        }
        holder.ivArrow.setOnClickListener {
            if (item.is_active) onClick(item)
        }
    }

    fun updateList(newList: List<Checklist>) {
        if (list.isEmpty()) {
            list = newList.toMutableList()
            notifyDataSetChanged()
            return
        }

        // Keep UI stable: only notify rows that actually changed.
        val oldById = list.associateBy { it.id }
        val newById = newList.associateBy { it.id }

        // Fallback to full refresh if structure changed (add/remove/reorder).
        if (list.size != newList.size || list.map { it.id } != newList.map { it.id }) {
            list = newList.toMutableList()
            notifyDataSetChanged()
            return
        }

        list = newList.toMutableList()
        newList.forEachIndexed { index, item ->
            val old = oldById[item.id]
            if (old == null || old.is_active != item.is_active || old.name != item.name) {
                notifyItemChanged(index)
            }
        }
    }

    fun updateItem(id: String, isActive: Boolean) {
        val index = list.indexOfFirst { it.id == id }
        if (index != -1) {
            list[index] = list[index].copy(is_active = isActive)
            notifyItemChanged(index)
        }
    }
}
