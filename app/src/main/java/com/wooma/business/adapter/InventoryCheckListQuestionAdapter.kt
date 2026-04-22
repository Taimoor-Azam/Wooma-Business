package com.wooma.business.adapter

import android.content.Context
import android.net.Uri
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
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wooma.business.R
import com.wooma.business.data.network.ApiClient
import com.wooma.business.model.ImageItem
import com.wooma.business.model.Question

class InventoryCheckListQuestionAdapter(
    val context: Context,
    private val originalList: MutableList<Question>,
    val reportId: String,
    private val isReadOnly: Boolean = false,
    private val onAnswerSelected: (question: Question, answerOption: String) -> Unit,
    private val onNoteChanged: (question: Question, note: String, showLoading: Boolean) -> Unit,
    private val onCameraClick: (questionId: String) -> Unit
) : RecyclerView.Adapter<InventoryCheckListQuestionAdapter.ViewHolder>() {
    private var isHandlingEnter = false

    private var filteredList = originalList.toMutableList()
    private val expandedStates = mutableMapOf<String, Boolean>()
    private val localPhotos = mutableMapOf<String, MutableList<Uri>>()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvQuestion: TextView = view.findViewById(R.id.tvQuestion)
        val ivChevron: ImageView = view.findViewById(R.id.ivChevron)
        val btnYes: TextView = view.findViewById(R.id.btnYes)
        val btnNo: TextView = view.findViewById(R.id.btnNo)
        val btnNA: TextView = view.findViewById(R.id.btnNA)
        val expandedLayout: LinearLayout = view.findViewById(R.id.expandedLayout)
        val ivAddImage: ImageView = view.findViewById(R.id.ivAddImage)
        val rvImages: RecyclerView = view.findViewById(R.id.rvImages)
        val etNote: EditText = view.findViewById(R.id.etNote)
        val ivNoteSaveTick: ImageView = view.findViewById(R.id.ivNoteSaveTick)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_checklist_questions, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = filteredList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = filteredList[position]
        val questionId = item.checklist_question_id

        holder.tvQuestion.text = item.text
        updateAnswerButtons(holder, item.answer_option)

        val isExpanded = expandedStates[questionId] ?: false
        holder.expandedLayout.visibility = if (isExpanded) View.VISIBLE else View.GONE
        holder.ivChevron.rotation = if (isExpanded) 270f else 90f

        holder.ivChevron.setOnClickListener {
            val pos = holder.adapterPosition.takeIf { it != RecyclerView.NO_ID.toInt() }
                ?: return@setOnClickListener
            val qId =
                filteredList.getOrNull(pos)?.checklist_question_id ?: return@setOnClickListener
            val expanded = !(expandedStates[qId] ?: false)
            expandedStates[qId] = expanded
            notifyItemChanged(pos)
        }

        if (!isReadOnly) {
            holder.btnYes.setOnClickListener {
                val pos = holder.adapterPosition.takeIf { it != RecyclerView.NO_ID.toInt() }
                    ?: return@setOnClickListener
                updateAnswerButtons(holder, "yes")
                filteredList[pos] = filteredList[pos].copy(answer_option = "yes")
                onAnswerSelected(filteredList[pos], "yes")
            }
            holder.btnNo.setOnClickListener {
                val pos = holder.adapterPosition.takeIf { it != RecyclerView.NO_ID.toInt() }
                    ?: return@setOnClickListener
                updateAnswerButtons(holder, "no")
                filteredList[pos] = filteredList[pos].copy(answer_option = "no")
                onAnswerSelected(filteredList[pos], "no")
            }
            holder.btnNA.setOnClickListener {
                val pos = holder.adapterPosition.takeIf { it != RecyclerView.NO_ID.toInt() }
                    ?: return@setOnClickListener
                updateAnswerButtons(holder, "na")
                filteredList[pos] = filteredList[pos].copy(answer_option = "na")
                onAnswerSelected(filteredList[pos], "na")
            }
        }

        holder.etNote.onFocusChangeListener = null
        val existingWatcher = holder.etNote.tag as? TextWatcher
        if (existingWatcher != null) {
            holder.etNote.removeTextChangedListener(existingWatcher)
        }

        holder.etNote.setText(item.note ?: "")
        holder.etNote.isEnabled = !isReadOnly
        holder.ivNoteSaveTick.visibility = View.GONE

        if (!isReadOnly) {
            val textWatcher = object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

                    val fullText = s?.toString() ?: ""
                    if (isHandlingEnter) return

                    var newText = s?.toString() ?: ""
                    if (fullText.contains('\n')) {
                        isHandlingEnter = true
                        newText = fullText.replace("\n", " ")
                        holder.etNote.setText(newText)
                        holder.etNote.setSelection(newText.length)
                        isHandlingEnter = false
                        return
                    }
                    item.is_changed = true
                    item.note = newText
                }

                override fun afterTextChanged(s: Editable?) {}
            }
            holder.etNote.addTextChangedListener(textWatcher)
            holder.etNote.tag = textWatcher

            holder.etNote.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val text = holder.etNote.text.toString()
                    if (text != (item.original_note ?: "")) {
                        onNoteChanged(item, text, false)
                    }
                }
            }

            holder.etNote.setOnEditorActionListener { v, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
                ) {

                    val start = holder.etNote.selectionStart
                    val end = holder.etNote.selectionEnd
                    holder.etNote.text.replace(start, end, " ")

                    val text = holder.etNote.text.toString()
                    item.note = text
                    onNoteChanged(item, text, false)

                    holder.etNote.clearFocus()
                    val imm =
                        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                    true
                } else false
            }

            holder.etNote.setOnKeyListener { v, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                    val start = holder.etNote.selectionStart
                    val end = holder.etNote.selectionEnd
                    holder.etNote.text.replace(start, end, " ")

                    val text = holder.etNote.text.toString()
                    item.note = text
                    onNoteChanged(item, text, false)

                    holder.etNote.clearFocus()
                    val imm =
                        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                    true
                } else false
            }
        }

        holder.ivAddImage.visibility = if (isReadOnly) View.GONE else View.VISIBLE
        if (!isReadOnly) {
            holder.ivAddImage.setOnClickListener {
                val pos = holder.adapterPosition.takeIf { it != RecyclerView.NO_ID.toInt() }
                    ?: return@setOnClickListener
                val qId =
                    filteredList.getOrNull(pos)?.checklist_question_id ?: return@setOnClickListener
                onCameraClick(qId)
            }
        }

        val remoteImages = item.checklist_question_answer_attachment?.attachments
            ?.mapNotNull { att ->
                val id = att.id ?: return@mapNotNull null
                val url = att.url ?: att.storageKey?.let { "${ApiClient.IMAGE_BASE_URL}$it" }
                ?: return@mapNotNull null
                ImageItem.Remote(id, url)
            } ?: emptyList()
        val localUris = localPhotos[questionId] ?: emptyList<Uri>()
        val photoList = mutableListOf<ImageItem>().apply {
            addAll(remoteImages)
            addAll(localUris.map { ImageItem.Local(it) })
        }

        holder.rvImages.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        holder.rvImages.adapter =
            ImageAdapter(photoList, showDelete = !isReadOnly, title = item.text)
    }

    fun deliverPhotos(questionId: String, uris: List<Uri>) {
        localPhotos.getOrPut(questionId) { mutableListOf() }.addAll(uris)
        val position = filteredList.indexOfFirst { it.checklist_question_id == questionId }
        if (position != -1) notifyItemChanged(position)
    }

    private fun updateAnswerButtons(holder: ViewHolder, selectedOption: String?) {
        val greenBg = ContextCompat.getDrawable(context, R.drawable.bg_button_green)
        val defaultBg = ContextCompat.getDrawable(context, R.drawable.bg_border_info)
        val white = ContextCompat.getColor(context, R.color.white)
        val black = ContextCompat.getColor(context, R.color.black)

        val normalized = selectedOption?.lowercase()

        holder.btnYes.background =
            if (normalized == "yes") greenBg else defaultBg?.constantState?.newDrawable()
        holder.btnYes.setTextColor(if (normalized == "yes") white else black)

        holder.btnNo.background =
            if (normalized == "no") greenBg else defaultBg?.constantState?.newDrawable()
        holder.btnNo.setTextColor(if (normalized == "no") white else black)

        holder.btnNA.background =
            if (normalized == "na") greenBg else defaultBg?.constantState?.newDrawable()
        holder.btnNA.setTextColor(if (normalized == "na") white else black)
    }

    fun updateList(list: List<Question>) {
        filteredList = list.toMutableList()
        notifyDataSetChanged()
    }

    public fun getChangedQuestionItems(): ArrayList<Question> {
        return filteredList.filter { it.is_changed == true } as ArrayList<Question>
    }
}
