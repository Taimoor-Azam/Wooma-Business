package com.wooma.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "room_inspections",
    foreignKeys = [
        ForeignKey(
            entity = RoomEntity::class,
            parentColumns = ["id"],
            childColumns = ["roomId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("roomId"), Index("syncStatus")]
)
data class RoomInspectionEntity(
    @PrimaryKey val id: String,
    val serverId: String? = null,
    val roomId: String,
    val isIssue: Boolean = false,
    val note: String? = null,
    val priority: String? = null,
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)
