package com.wooma.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wooma.data.local.entity.ChecklistQuestionEntity
import com.wooma.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ChecklistQuestionDao {

    @Query("SELECT * FROM checklist_questions WHERE reportChecklistId = :checklistId ORDER BY displayOrder ASC")
    fun observeByChecklist(checklistId: String): Flow<List<ChecklistQuestionEntity>>

    @Query("SELECT * FROM checklist_questions WHERE reportChecklistId = :checklistId ORDER BY displayOrder ASC")
    suspend fun getByChecklist(checklistId: String): List<ChecklistQuestionEntity>

    @Query("SELECT * FROM checklist_questions WHERE localId = :localId")
    suspend fun getById(localId: Long): ChecklistQuestionEntity?

    @Query("SELECT * FROM checklist_questions WHERE reportChecklistId = :checklistId AND checklistQuestionId = :questionId LIMIT 1")
    suspend fun getByQuestionId(checklistId: String, questionId: String): ChecklistQuestionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(question: ChecklistQuestionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(questions: List<ChecklistQuestionEntity>)

    @Query("UPDATE checklist_questions SET syncStatus = :status WHERE localId = :localId")
    suspend fun updateSyncStatus(localId: Long, status: SyncStatus)

    @Query(
        "UPDATE checklist_questions SET answerAttachmentId = :attachmentId " +
            "WHERE reportChecklistId = :checklistId AND checklistQuestionId = :questionId"
    )
    suspend fun updateAnswerAttachmentId(checklistId: String, questionId: String, attachmentId: String)

    @Query("SELECT * FROM checklist_questions WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSync(): List<ChecklistQuestionEntity>

    @Query("SELECT * FROM checklist_questions WHERE reportChecklistId = :checklistId AND syncStatus != 'SYNCED'")
    suspend fun getPendingByChecklist(checklistId: String): List<ChecklistQuestionEntity>

    @Query(
        "SELECT COUNT(*) FROM checklist_questions q " +
            "INNER JOIN checklists c ON c.id = q.reportChecklistId " +
            "WHERE c.reportId = :reportId AND q.syncStatus != 'SYNCED'"
    )
    fun observePendingCountByReport(reportId: String): Flow<Int>

    @Query("DELETE FROM checklist_questions WHERE reportChecklistId = :checklistId")
    suspend fun deleteByChecklist(checklistId: String)
}
