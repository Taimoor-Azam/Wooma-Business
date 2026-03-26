package com.wooma.business.adapter

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.wooma.business.R
import com.wooma.business.model.Question

class InventoryCheckListQuestionAdapter(
    val context: Context,
    private val originalList: MutableList<Question>,
    val reportId: String,
    private val isReadOnly: Boolean = false,
    private val onAnswerSelected: (question: Question, answerOption: String) -> Unit,
    private val onNoteChanged: (question: Question, note: String) -> Unit,
    private val onCameraClick: (questionId: String) -> Unit
) : RecyclerView.Adapter<InventoryCheckListQuestionAdapter.ViewHolder>() {

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

        // Restore answer button state
        updateAnswerButtons(holder, item.answer_option)

        // Restore expand/collapse state
        val isExpanded = expandedStates[questionId] ?: false
        holder.expandedLayout.visibility = if (isExpanded) View.VISIBLE else View.GONE
        holder.ivChevron.rotation = if (isExpanded) 270f else 90f

        holder.ivChevron.setOnClickListener {
            val pos = holder.adapterPosition.takeIf { it != RecyclerView.NO_ID.toInt() } ?: return@setOnClickListener
            val qId = filteredList.getOrNull(pos)?.checklist_question_id ?: return@setOnClickListener
            val expanded = !(expandedStates[qId] ?: false)
            expandedStates[qId] = expanded
            notifyItemChanged(pos)
        }

        // Yes / No / N/A click listeners
        if (!isReadOnly) {
            holder.btnYes.setOnClickListener {
                val pos = holder.adapterPosition.takeIf { it != RecyclerView.NO_ID.toInt() } ?: return@setOnClickListener
                updateAnswerButtons(holder, "yes")
                filteredList[pos] = filteredList[pos].copy(answer_option = "yes")
                onAnswerSelected(filteredList[pos], "yes")
            }
            holder.btnNo.setOnClickListener {
                val pos = holder.adapterPosition.takeIf { it != RecyclerView.NO_ID.toInt() } ?: return@setOnClickListener
                updateAnswerButtons(holder, "no")
                filteredList[pos] = filteredList[pos].copy(answer_option = "no")
                onAnswerSelected(filteredList[pos], "no")
            }
            holder.btnNA.setOnClickListener {
                val pos = holder.adapterPosition.takeIf { it != RecyclerView.NO_ID.toInt() } ?: return@setOnClickListener
                updateAnswerButtons(holder, "na")
                filteredList[pos] = filteredList[pos].copy(answer_option = "na")
                onAnswerSelected(filteredList[pos], "na")
            }
        } else {
            holder.btnYes.setOnClickListener(null)
            holder.btnNo.setOnClickListener(null)
            holder.btnNA.setOnClickListener(null)
        }

        // Note field — reset listener before setting text to avoid recycled-view interference
        holder.etNote.setOnFocusChangeListener(null)
        holder.etNote.setText(item.note ?: "")
        holder.etNote.isEnabled = !isReadOnly
        holder.etNote.isFocusable = !isReadOnly
        holder.etNote.isFocusableInTouchMode = !isReadOnly
        if (!isReadOnly) {
            holder.etNote.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val pos = holder.adapterPosition.takeIf { it != RecyclerView.NO_ID.toInt() } ?: return@setOnFocusChangeListener
                    val current = filteredList.getOrNull(pos) ?: return@setOnFocusChangeListener
                    val note = holder.etNote.text.toString()
                    if (note != (current.note ?: "")) {
                        onNoteChanged(current, note)
                    }
                }
            }
        }

        // Camera button
        holder.ivAddImage.visibility = if (isReadOnly) View.GONE else View.VISIBLE
        if (!isReadOnly) {
            holder.ivAddImage.setOnClickListener {
                val pos = holder.adapterPosition.takeIf { it != RecyclerView.NO_ID.toInt() } ?: return@setOnClickListener
                val qId = filteredList.getOrNull(pos)?.checklist_question_id ?: return@setOnClickListener
                onCameraClick(qId)
            }
        }

        // Photos — merge remote URLs from the model with locally captured URIs
        val remoteUrls = item.checklist_question_answer_attachment?.attachments
            ?.mapNotNull { it.url } ?: emptyList()
        val localUris = localPhotos[questionId] ?: emptyList<Uri>()
        val photoList = mutableListOf<Any>().apply {
            addAll(remoteUrls)
            addAll(localUris)
        }

        holder.rvImages.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        holder.rvImages.adapter = MixedPhotoAdapter(photoList)
    }

    /** Called by the Activity after CameraActivity returns photos for a specific question. */
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

    /** Read-only adapter for displaying a mixed list of remote URL strings and local Uris. */
    private inner class MixedPhotoAdapter(private val items: List<Any>) :
        RecyclerView.Adapter<MixedPhotoAdapter.PhotoHolder>() {

        inner class PhotoHolder(view: View) : RecyclerView.ViewHolder(view) {
            val image: ImageView = view.findViewById(R.id.image)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_image, parent, false)
            // Hide the delete button — photos here are display-only
            view.findViewById<ImageView>(R.id.delete).visibility = View.GONE
            return PhotoHolder(view)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: PhotoHolder, position: Int) {
            Glide.with(context).load(items[position]).into(holder.image)
        }
    }
}
