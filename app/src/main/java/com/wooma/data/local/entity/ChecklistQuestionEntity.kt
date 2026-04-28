package com.wooma.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "checklist_questions",
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
data class ChecklistQuestionEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val reportChecklistId: String,
    val checklistQuestionId: String = "",
    val text: String = "",
    val type: String = "",
    val displayOrder: Int = 0,
    val isRequired: Boolean = false,
    val answerId: String? = null,
    val answerOption: String? = null,
    val answerText: String? = null,
    val note: String? = null,
    val originalNote: String? = null,
    val answerAttachmentId: String? = null,
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)
