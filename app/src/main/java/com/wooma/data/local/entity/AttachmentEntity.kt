package com.wooma.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attachments",
    indices = [Index("entityId"), Index("entityType"), Index("isUploaded")]
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val serverId: String? = null,
    val entityId: String,
    val entityType: String,
    val originalName: String = "",
    val storageKey: String? = null,
    val link: String? = null,
    val mimeType: String = "",
    val fileSize: Long = 0L,
    val localUri: String? = null,
    val isUploaded: Boolean = false
)
