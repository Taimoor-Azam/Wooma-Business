package com.wooma.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rooms",
    foreignKeys = [
        ForeignKey(
            entity = ReportEntity::class,
            parentColumns = ["id"],
            childColumns = ["reportId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("reportId"), Index("syncStatus")]
)
data class RoomEntity(
    @PrimaryKey val id: String,
    val serverId: String? = null,
    val reportId: String,
    val templateId: String? = null,
    val name: String = "",
    val displayOrder: String? = null,
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)
