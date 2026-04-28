package com.wooma.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "room_items",
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
data class RoomItemEntity(
    @PrimaryKey val id: String,
    val serverId: String? = null,
    val roomId: String,
    val name: String = "",
    val generalCondition: String? = null,
    val generalCleanliness: String? = null,
    val description: String? = null,
    val note: String? = null,
    val displayOrder: String? = null,
    val isActive: Boolean = true,
    val isDeleted: Boolean = false,
    val createdAt: String = "",
    val updatedAt: String = "",
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)
