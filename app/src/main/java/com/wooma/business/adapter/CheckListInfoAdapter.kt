package com.wooma.business.adapter

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.wooma.business.R
import com.wooma.business.model.InfoField

class CheckListInfoAdapter(
    val context: Context,
    private val originalList: MutableList<InfoField>,
    val reportId: String,
    private val onFieldAnswerChanged: (checklistFieldId: String, answerText: String) -> Unit
) : RecyclerView.Adapter<CheckListInfoAdapter.ViewHolder>() {

    private var filteredList = originalList.toMutableList()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvInfoQuestion: TextView = view.findViewById(R.id.tvInfoQuestion)
        val etInfoAnswer: EditText = view.findViewById(R.id.etInfoAnswer)
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

        // Remove any existing watcher stored in the tag before setting new text
        val existingWatcher = holder.etInfoAnswer.tag as? TextWatcher
        if (existingWatcher != null) {
            holder.etInfoAnswer.removeTextChangedListener(existingWatcher)
        }
        holder.etInfoAnswer.setText(item.answerText ?: "")

        // Reset focus listener to avoid stale captures from recycled views
        holder.etInfoAnswer.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val text = holder.etInfoAnswer.text.toString()
                if (text != (item.answerText ?: "")) {
                    onFieldAnswerChanged(item.checklistFieldId, text)
                }
            }
        }
    }

    fun updateList(list: List<InfoField>) {
        filteredList = list.toMutableList()
        notifyDataSetChanged()
    }
}
