package com.wooma.business.adapter

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.wooma.business.R
import com.wooma.business.activities.report.otherItems.CheckListListingActivity
import com.wooma.business.activities.report.otherItems.DetectorListingActivity
import com.wooma.business.activities.report.otherItems.KeysListingActivity
import com.wooma.business.activities.report.otherItems.MeterListingActivity
import com.wooma.business.model.CountItem
import com.wooma.business.model.Rooms

class InventoryOtherItemsAdapter(
    val context: Context,
    private val originalList: MutableList<CountItem>,
    private val reportId: String
) : RecyclerView.Adapter<InventoryOtherItemsAdapter.ViewHolder>() {
    private var filteredList = originalList.toMutableList()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvSub: TextView = view.findViewById(R.id.tvSub)
        val ivIcon: ImageView = view.findViewById(R.id.ivIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_other_grid, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = filteredList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.tvName.text = filteredList[position].label
        holder.tvSub.text = "${filteredList[position].value} recorded"

        holder.itemView.setOnClickListener {
            val intent = when (filteredList[position].label) {
                "Meters" -> Intent(context, MeterListingActivity::class.java)
                "Keys" -> Intent(context, KeysListingActivity::class.java)
                "Detectors" -> Intent(context, DetectorListingActivity::class.java)
                else -> Intent(context, CheckListListingActivity::class.java)
            }
            context.startActivity(intent.putExtra("reportId", reportId))
        }

        holder.ivIcon.setImageDrawable(
            ContextCompat.getDrawable(
                context,
                when (filteredList[position].label) {
                    "Meters" -> R.drawable.svg_meter
                    "Keys" -> R.drawable.svg_keys
                    "Detectors" -> R.drawable.svg_detector
                    else -> R.drawable.svg_checklist
                }
            )
        )
    }

    fun updateList(list: List<CountItem>) {
        filteredList = list.toMutableList()
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        filteredList = if (query.isEmpty()) {
            originalList.toMutableList()
        } else {
            originalList.filter {
                it.label.contains(query, true)
            }.toMutableList()
        }
        notifyDataSetChanged()
    }
}
