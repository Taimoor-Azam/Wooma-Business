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

class ArchivePropertyAdapter(
    val context: Context,
    private val originalList: MutableList<Property>,
    val itemClick: OnItemClickInterface
) : RecyclerView.Adapter<ArchivePropertyAdapter.ViewHolder>() {

    private var filteredList = originalList.toMutableList()

    interface OnItemClickInterface {
        fun onItemClick(item: Property)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val address: TextView = view.findViewById(R.id.tvAddress)
        val ivDelete: ImageView = view.findViewById(R.id.ivDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_archive_property, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = filteredList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.address.text = filteredList[position].address

        val black = ContextCompat.getColor(context, R.color.black)
        val light_grey = ContextCompat.getColor(context, R.color.light_grey)

        val spannableBuilder = SpannableStringBuilder()

        spannableBuilder.append(filteredList[position].address + ", " + filteredList[position].postcode + " ")
        spannableBuilder.setSpan(
            ForegroundColorSpan(black),
            0,
            spannableBuilder.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        val start = spannableBuilder.length
        spannableBuilder.append("- " + filteredList[position].noOfReports + " reports")
        spannableBuilder.setSpan(
            ForegroundColorSpan(light_grey),
            start,
            spannableBuilder.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        holder.address.text = spannableBuilder

        holder.ivDelete.setOnClickListener {
            itemClick.onItemClick(filteredList[position])
        }
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
                it.address?.contains(query, true) == true
            }.toMutableList()
        }
        notifyDataSetChanged()
    }
}
