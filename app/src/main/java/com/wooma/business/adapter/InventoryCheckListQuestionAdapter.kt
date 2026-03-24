package com.wooma.business.adapter

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.wooma.business.R
import com.wooma.business.activities.report.otherItems.AddEditDetectorActivity
import com.wooma.business.model.Question

class InventoryCheckListQuestionAdapter(
    val context: Context,
    private val originalList: MutableList<Question>,
    val reportId: String,
) : RecyclerView.Adapter<InventoryCheckListQuestionAdapter.ViewHolder>() {

    private var filteredList = originalList.toMutableList()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvQuestion: TextView = view.findViewById(R.id.tvQuestion)
        val tvAnswer: TextView = view.findViewById(R.id.tvAnswer)
        val tvNote: TextView = view.findViewById(R.id.tvNote)
        val rvImages: RecyclerView = view.findViewById(R.id.rvImages)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_checklist_questions, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = filteredList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = filteredList[position]

        holder.tvQuestion.text = item.text
        holder.tvAnswer.text = item.answer_option ?: ""
        holder.tvNote.text = item.note ?: ""

     /*   holder.itemView.setOnClickListener {
            context.startActivity(
                Intent(context, AddEditDetectorActivity::class.java).putExtra(
                    "checkListItem",
                    filteredList[position]
                ).putExtra("reportId", reportId)
            )
        }*/
    }

    fun updateList(list: List<Question>) {
        filteredList = list.toMutableList()
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        filteredList = if (query.isEmpty()) {
            originalList.toMutableList()
        } else {
            originalList.filter {
                it.text.contains(query, true)
            }.toMutableList()
        }
        notifyDataSetChanged()
    }
}
