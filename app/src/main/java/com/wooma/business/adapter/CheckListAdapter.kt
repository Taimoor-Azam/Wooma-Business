package com.wooma.business.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.suke.widget.SwitchButton
import com.wooma.business.R
import com.wooma.business.model.Checklist

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
        holder.switchButton.setOnCheckedChangeListener(null)
        holder.switchButton.isChecked = item.is_active
        holder.switchButton.isEnabled = !isReadOnly

        holder.switchButton.setOnCheckedChangeListener { _, isChecked ->
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
        list = newList.toMutableList()
        notifyDataSetChanged()
    }

    fun updateItem(id: String, isActive: Boolean) {
        val index = list.indexOfFirst { it.id == id }
        if (index != -1) {
            list[index] = list[index].copy(is_active = isActive)
            notifyItemChanged(index)
        }
    }
}
