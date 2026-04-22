package com.wooma.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.wooma.R
import com.wooma.model.ConditionDAO

class ItemConditionAdapter(
    val context: Context,
    private val originalList: MutableList<ConditionDAO>,
    private var selectedValue : String,
    private val onItemClick: (ConditionDAO?) -> Unit
) : RecyclerView.Adapter<ItemConditionAdapter.ViewHolder>() {

    private var selectedPostion = -1
    private var filteredList = originalList.toMutableList()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivConditionIcon: ImageView = view.findViewById(R.id.ivConditionIcon)
        val tvCondition: TextView = view.findViewById(R.id.tvCondition)
        val mainLayout: LinearLayout = view.findViewById(R.id.mainLayout)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_condition, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = filteredList.size

    override fun onBindViewHolder(holder: ViewHolder, @SuppressLint("RecyclerView") position: Int) {
        holder.tvCondition.text = filteredList[position].name
        holder.ivConditionIcon.setImageDrawable(
            ContextCompat.getDrawable(
                context,
                filteredList[position].icon
            )
        )
        val isSelected = selectedPostion == position || selectedValue.lowercase() == filteredList[position].name.lowercase()
        holder.mainLayout.background = ContextCompat.getDrawable(
            context,
            if (isSelected) R.drawable.bg_condition_selected else R.drawable.bg_edittext
        )
        holder.itemView.setOnClickListener {
            selectedPostion = position
            selectedValue = filteredList[position].name
            onItemClick(filteredList[position])
            notifyDataSetChanged()
        }
    }

    fun updateList(list: List<ConditionDAO>) {
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
