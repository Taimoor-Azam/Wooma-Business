package com.wooma.business.model

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
