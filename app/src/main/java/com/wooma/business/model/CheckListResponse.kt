package com.wooma.business.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

data class ChecklistData(
    val id: String,
    val checklist_id: String,
    val info_fields: ArrayList<InfoField>,
    val questions: ArrayList<Question>
)

data class InfoField(
    val checklist_field_id: String?,
    val label: String,
    val type: String,
    val is_required: Boolean,
    val checklist_info_field_answer_id: String?,
    val answer_text: String?
)

@Parcelize
data class Question(
    val checklist_question_id: String?,
    val text: String,
    val type: String,
    val displayOrder: Int,
    val is_required: Boolean,
    val checklist_question_answer_id: String?,
    val answer_option: String?,
    val answer_text: String?,
    val note: String?,
    val checklist_question_answer_attachment: AnswerAttachment?
) : Parcelable

@Parcelize
data class AnswerAttachment(
    val id: String,
    val attachments: ArrayList<Attachment>?
) : Parcelable

data class CheckListActiveStatus(
    val reportId: String,
    val checklists: ArrayList<Checklist>?
)

data class Checklist(
    val id: String,
    val name: String,
    val is_active: Boolean
)