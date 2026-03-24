package com.wooma.business.adapter

import android.content.Context
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.wooma.business.R
import com.wooma.business.model.Property

class SuggestionsAdapter(
    val context: Context,
    private val originalList: MutableList<String>,
    val itemClick: OnItemClickInterface
) : RecyclerView.Adapter<SuggestionsAdapter.ViewHolder>() {

    private var filteredList = originalList.toMutableList()

    interface OnItemClickInterface {
        fun onItemClick(item: String)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSuggestion: TextView = view.findViewById(R.id.tvSuggestion)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_suggestions, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = filteredList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.tvSuggestion.text = filteredList[position]

        holder.tvSuggestion.setOnClickListener {
            itemClick.onItemClick(filteredList[position])
        }
    }

    fun updateList(list: List<String>) {
        filteredList = list.toMutableList()
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        filteredList = if (query.isEmpty()) {
            originalList.toMutableList()
        } else {
            originalList.filter {
                it.contains(query, true)
            }.toMutableList()
        }
        notifyDataSetChanged()
    }
}
