package com.wooma.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_queue",
    indices = [Index("status"), Index("entityType"), Index("localEntityId")]
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,
    val operationType: String,
    val localEntityId: String,
    val serverEntityId: String? = null,
    val payload: String = "",
    val parentSyncId: Long? = null,
    val retryCount: Int = 0,
    val status: String = "PENDING",
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
