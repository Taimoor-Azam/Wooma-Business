package com.wooma.business.adapter

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.wooma.business.R
import com.wooma.business.model.InfoField

class CheckListInfoAdapter(
    val context: Context,
    private val originalList: MutableList<InfoField>,
    val reportId: String,
    private val isReadOnly: Boolean = false,
    private val onFieldAnswerChanged: (checklistFieldId: String?, answerText: String) -> Unit
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
        holder.etInfoAnswer.setText(item.answer_text ?: "")
        holder.etInfoAnswer.isEnabled = !isReadOnly
        holder.etInfoAnswer.isFocusable = !isReadOnly
        holder.etInfoAnswer.isFocusableInTouchMode = !isReadOnly

        // Configure multiline input but handle Enter as Done (not newline)
        holder.etInfoAnswer.setRawInputType(android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        holder.etInfoAnswer.setHorizontallyScrolling(false)
        holder.etInfoAnswer.maxLines = Int.MAX_VALUE

        holder.etInfoAnswer.setOnEditorActionListener(null)
        if (!isReadOnly) {
            holder.etInfoAnswer.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                    val text = holder.etInfoAnswer.text.toString()
                    onFieldAnswerChanged(item.checklist_field_id, text)
                    holder.etInfoAnswer.clearFocus()
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(holder.etInfoAnswer.windowToken, 0)
                    true
                } else false
            }
            // Reset focus listener to avoid stale captures from recycled views
            holder.etInfoAnswer.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val text = holder.etInfoAnswer.text.toString()
                    if (text != (item.answer_text ?: "")) {
                        onFieldAnswerChanged(item.checklist_field_id, text)
                    }
                }
            }
        } else {
            holder.etInfoAnswer.setOnFocusChangeListener(null)
        }
    }

    fun updateList(list: List<InfoField>) {
        filteredList = list.toMutableList()
        notifyDataSetChanged()
    }
}
