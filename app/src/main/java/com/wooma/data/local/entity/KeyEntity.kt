package com.wooma.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "keys",
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
data class KeyEntity(
    @PrimaryKey val id: String,
    val serverId: String? = null,
    val reportId: String,
    val name: String = "",
    val noOfKeys: Int? = 1,
    val note: String? = null,
    val displayOrder: String? = null,
    val isDeleted: Boolean = false,
    val createdAt: String = "",
    val updatedAt: String = "",
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)
