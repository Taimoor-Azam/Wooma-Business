package com.wooma.business.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.wooma.business.R
import com.wooma.business.model.PostalAddress

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
        holder.tvAddress.text =
            item.line_1 + ", " + if (item.line_2 != null) item.line_2 + ", " else "" + item.post_town + ", " + item.postcode

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
