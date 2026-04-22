package com.wooma.model

data class CreateAttachmentRequest(
    val entityId: String,
    val entityType: String,
    val originalName: String,
    val storageKey: String,
    val mimeType: String,
    val fileSize: Long
)

data class AttachmentRecord(
    val id: String,
    val entityId: String,
    val entityType: String,
    val originalName: String,
    val storageKey: String,
    val link: String?,
    val mimeType: String,
    val fileSize: Long
)

data class ChecklistStatusRequest(
    val is_active: Boolean
)

data class UpsertQuestionAnswerRequest(
    val report_checklist_id: String,
    val checklist_question_id: String,
    val answer_option: String? = null,
    val note: String? = null
)

data class UpsertFieldAnswerRequest(
    val report_checklist_id: String,
    val checklist_field_id: String,
    val answer_text: String? = null
)

data class ChecklistAnswerAttachmentRequest(
    val report_checklist_id: String,
    val checklist_question_id: String
)

data class ChecklistAnswerAttachmentResponse(
    val id: String,
    val report_checklist_id: String,
    val checklist_question_id: String,
    val attachments: ArrayList<AttachmentRecord>
)
