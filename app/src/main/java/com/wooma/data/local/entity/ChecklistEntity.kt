package com.wooma.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "checklists",
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
data class ChecklistEntity(
    @PrimaryKey val id: String,
    val checklistTemplateId: String = "",
    val reportId: String,
    val name: String = "",
    val isActive: Boolean = true,
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)
