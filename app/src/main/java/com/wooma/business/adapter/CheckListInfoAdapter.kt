package com.wooma.business.adapter

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.wooma.business.R
import com.wooma.business.activities.report.otherItems.AddEditMeterActivity
import com.wooma.business.model.InfoField

class CheckListInfoAdapter(
    val context: Context,
    private val originalList: MutableList<InfoField>,
    val reportId: String,
) : RecyclerView.Adapter<CheckListInfoAdapter.ViewHolder>() {

    private var filteredList = originalList.toMutableList()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvInfoQuestion: TextView = view.findViewById(R.id.tvInfoQuestion)
        val tvInfoAnswer: TextView = view.findViewById(R.id.tvInfoAnswer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_checklist_info, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = filteredList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = filteredList[position]

        holder.tvInfoQuestion.text = item.label
        holder.tvInfoAnswer.text = item.answerText ?: ""

        holder.itemView.setOnClickListener {
            /*context.startActivity(
                Intent(context, AddEditMeterActivity::class.java).putExtra(
                    "checkListInfoItem",
                    filteredList[position]
                ).putExtra("reportId", reportId)
            )*/
        }
    }

    fun updateList(list: List<InfoField>) {
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
