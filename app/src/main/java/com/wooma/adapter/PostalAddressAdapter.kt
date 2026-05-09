package com.wooma.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.wooma.R
import com.wooma.model.PostalAddress

class PostalAddressAdapter(
    val context: Context,
    private val originalList: MutableList<PostalAddress>,
    val itemClick: OnItemClickInterface
) : RecyclerView.Adapter<PostalAddressAdapter.ViewHolder>() {

    private var filteredList = originalList.toMutableList()

    interface OnItemClickInterface {
        fun onItemClick(item: PostalAddress)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAddress: TextView = view.findViewById(R.id.tvAddress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_postal_address, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = filteredList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = filteredList[position]
        val firstLine = item.line_1.trim().orEmpty()
        val secondLineParts = mutableListOf<String>()
        item.line_2?.trim()?.takeIf { it.isNotEmpty() }?.let { secondLineParts.add(it) }
        secondLineParts.add(item.post_town.trim().orEmpty())
        secondLineParts.add(item.postcode.trim().orEmpty())
        val secondLine = secondLineParts.joinToString(", ")

        holder.tvAddress.text = when {
            firstLine.isNotEmpty() && secondLine.isNotEmpty() -> "$firstLine $secondLine"
            firstLine.isNotEmpty() -> firstLine
            secondLine.isNotEmpty() -> secondLine
            else -> ""
        }

        holder.tvAddress.setOnClickListener {
            itemClick.onItemClick(item)
        }

    }

    fun updateList(list: List<PostalAddress>) {
        filteredList = list.toMutableList()
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        filteredList = if (query.isEmpty()) {
            originalList.toMutableList()
        } else {
            originalList.filter {
                it.line_1?.contains(query, true) == true
            }.toMutableList()
        }
        notifyDataSetChanged()
    }
}
