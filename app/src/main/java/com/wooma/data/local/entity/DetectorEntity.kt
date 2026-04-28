package com.wooma.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "detectors",
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
data class DetectorEntity(
    @PrimaryKey val id: String,
    val serverId: String? = null,
    val reportId: String,
    val name: String = "",
    val location: String? = null,
    val note: String? = null,
    val displayOrder: String? = null,
    val isDeleted: Boolean = false,
    val createdAt: String = "",
    val updatedAt: String = "",
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)
