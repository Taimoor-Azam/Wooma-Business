package com.wooma.business.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.wooma.business.R
import com.wooma.business.model.Property

class DuplicatePropertySelectAdapter(
    private val originalList: MutableList<Property>,
    private val onSelectionChanged: (Property?) -> Unit
) : RecyclerView.Adapter<DuplicatePropertySelectAdapter.ViewHolder>() {

    private var filteredList = originalList.toMutableList()
    private var selectedPropertyId: String? = null

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAddress: TextView = view.findViewById(R.id.tvAddress)
        val cbSelect: CheckBox = view.findViewById(R.id.cbSelect)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_duplicate_property_select, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = filteredList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = filteredList[position]
        holder.tvAddress.text = item.address ?: ""
        holder.cbSelect.setOnCheckedChangeListener(null)
        holder.cbSelect.isChecked = item.id == selectedPropertyId

        val clickListener = View.OnClickListener {
            selectedPropertyId = item.id
            notifyDataSetChanged()
            onSelectionChanged(item)
        }

        holder.itemView.setOnClickListener(clickListener)
        holder.cbSelect.setOnClickListener(clickListener)
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
                (it.address ?: "").contains(query, true)
            }.toMutableList()
        }
        notifyDataSetChanged()
    }

    fun setSelectedProperty(property: Property?) {
        selectedPropertyId = property?.id
        notifyDataSetChanged()
    }
}
