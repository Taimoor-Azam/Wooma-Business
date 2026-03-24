package com.wooma.business.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.wooma.business.R
import com.wooma.business.model.ConditionDAO
import com.wooma.business.model.Rooms

class ItemCondtionAdapter(
    val context: Context,
    private val originalList: MutableList<ConditionDAO>,
    private val selectedValue : String,
    private val onItemClick: (ConditionDAO?) -> Unit
) : RecyclerView.Adapter<ItemCondtionAdapter.ViewHolder>() {

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
        if (selectedPostion == position || selectedValue.lowercase() == filteredList[position].name.lowercase()) {
            holder.mainLayout.setBackgroundDrawable(
                ContextCompat.getDrawable(
                    context,
                    R.drawable.bg_white_selected_black_stroke
                )
            )
        } else {
            holder.mainLayout.setBackgroundDrawable(
                ContextCompat.getDrawable(
                    context,
                    R.drawable.bg_edittext
                )
            )
        }
        holder.itemView.setOnClickListener {
            selectedPostion = position
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
