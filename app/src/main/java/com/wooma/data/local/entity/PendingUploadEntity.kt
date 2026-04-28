package com.wooma.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pending_uploads",
    indices = [Index("status"), Index("entityLocalId")]
)
data class PendingUploadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val localUri: String,
    val entityLocalId: String,
    val entityServerId: String? = null,
    val entityType: String,
    val fileName: String,
    val mimeType: String,
    val fileSize: Long = 0L,
    val status: String = "PENDING",
    val storageKey: String? = null,
    val attachmentServerId: String? = null,
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
