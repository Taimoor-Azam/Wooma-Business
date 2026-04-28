package com.wooma.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "checklist_info_fields",
    foreignKeys = [
        ForeignKey(
            entity = ChecklistEntity::class,
            parentColumns = ["id"],
            childColumns = ["reportChecklistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("reportChecklistId"), Index("syncStatus")]
)
data class ChecklistInfoFieldEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val reportChecklistId: String,
    val fieldId: String = "",
    val label: String = "",
    val type: String = "",
    val isRequired: Boolean = false,
    val answerId: String? = null,
    val answerText: String? = null,
    val originalAnswerText: String? = null,
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)
