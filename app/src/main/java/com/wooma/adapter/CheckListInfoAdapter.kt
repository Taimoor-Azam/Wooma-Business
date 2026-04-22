package com.wooma.adapter

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.wooma.R
import com.wooma.model.InfoField

class CheckListInfoAdapter(
    val context: Context,
    private val originalList: MutableList<InfoField>,
    val reportId: String,
    private val isReadOnly: Boolean = false,
    private val onFieldAnswerChanged: (checklistFieldId: String?, answerText: String, showLoading: Boolean) -> Unit
) : RecyclerView.Adapter<CheckListInfoAdapter.ViewHolder>() {
    private var isHandlingEnter = false

    private var filteredList = originalList.toMutableList()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvInfoQuestion: TextView = view.findViewById(R.id.tvInfoQuestion)
        val etInfoAnswer: EditText = view.findViewById(R.id.etInfoAnswer)
        val ivSaveTick: ImageView = view.findViewById(R.id.ivSaveTick)
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

        // Clear existing listeners
        holder.etInfoAnswer.onFocusChangeListener = null
        val existingWatcher = holder.etInfoAnswer.tag as? TextWatcher
        if (existingWatcher != null) {
            holder.etInfoAnswer.removeTextChangedListener(existingWatcher)
        }

        holder.etInfoAnswer.setText(item.answer_text ?: "")
        holder.etInfoAnswer.isEnabled = !isReadOnly
        holder.ivSaveTick.visibility = View.GONE

        if (!isReadOnly) {
            val textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val fullText = s?.toString() ?: ""
                    if (isHandlingEnter) return

                    var newText = s?.toString() ?: ""
                    if (fullText.contains('\n')) {
                        isHandlingEnter = true
                        newText = fullText.replace("\n", " ")
                        holder.etInfoAnswer.setText(newText)
                        holder.etInfoAnswer.setSelection(newText.length)
                        isHandlingEnter = false
                        return
                    }

                    item.answer_text = newText
                }
                override fun afterTextChanged(s: Editable?) {}
            }
            holder.etInfoAnswer.addTextChangedListener(textWatcher)
            holder.etInfoAnswer.tag = textWatcher

            holder.etInfoAnswer.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val text = holder.etInfoAnswer.text.toString()
                    if (text != (item.original_answer_text ?: "")) {
                        onFieldAnswerChanged(item.checklist_field_id, text, false)
                    }
                }
            }

            holder.etInfoAnswer.setOnEditorActionListener { v, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_DONE || 
                    (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                    
                    val start = holder.etInfoAnswer.selectionStart
                    val end = holder.etInfoAnswer.selectionEnd
                    holder.etInfoAnswer.text.replace(start, end, " ")
                    
                    val text = holder.etInfoAnswer.text.toString()
                    item.answer_text = text
                    onFieldAnswerChanged(item.checklist_field_id, text, false)
                    
                    holder.etInfoAnswer.clearFocus()
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                    true
                } else false
            }

            holder.etInfoAnswer.setOnKeyListener { v, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                    val start = holder.etInfoAnswer.selectionStart
                    val end = holder.etInfoAnswer.selectionEnd
                    holder.etInfoAnswer.text.replace(start, end, " ")
                    
                    val text = holder.etInfoAnswer.text.toString()
                    item.answer_text = text
                    onFieldAnswerChanged(item.checklist_field_id, text, false)

                    holder.etInfoAnswer.clearFocus()
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                    true
                } else false
            }
        }
    }

    fun updateList(list: List<InfoField>) {
        filteredList = list.toMutableList()
        notifyDataSetChanged()
    }
}
