package com.wooma.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.wooma.data.local.entity.PendingUploadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingUploadDao {

    @Query("SELECT COUNT(*) FROM pending_uploads WHERE status = 'PENDING'")
    fun countPending(): Flow<Int>

    @Query("SELECT * FROM pending_uploads WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPending(): List<PendingUploadEntity>

    @Query(
        "SELECT * FROM pending_uploads WHERE status = 'PENDING' " +
            "AND entityType = 'REPORT_COVER' AND entityLocalId = :reportId " +
            "ORDER BY createdAt DESC LIMIT 1"
    )
    suspend fun getPendingReportCoverUpload(reportId: String): PendingUploadEntity?

    @Query("SELECT * FROM pending_uploads WHERE id = :id")
    suspend fun getById(id: Long): PendingUploadEntity?

    @Insert
    suspend fun enqueue(entry: PendingUploadEntity): Long

    @Query("UPDATE pending_uploads SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE pending_uploads SET entityServerId = :serverId WHERE entityLocalId = :localId AND entityType = :type")
    suspend fun updateEntityServerId(localId: String, type: String, serverId: String)

    @Query(
        "UPDATE pending_uploads SET entityLocalId = :newLocalId WHERE entityLocalId = :oldLocalId " +
            "AND entityType = 'CHECKLIST_ANSWER_ATTACHMENT'"
    )
    suspend fun migrateChecklistAnswerAttachmentEntityLocalId(oldLocalId: String, newLocalId: String)

    @Query("UPDATE pending_uploads SET status = 'DONE', storageKey = :key, attachmentServerId = :attachmentId WHERE id = :id")
    suspend fun markDone(id: Long, key: String, attachmentId: String)

    @Query("UPDATE pending_uploads SET status = 'PENDING', retryCount = retryCount + 1 WHERE id = :id")
    suspend fun requeueForRetry(id: Long)

    @Query("DELETE FROM pending_uploads WHERE status = 'DONE'")
    suspend fun purgeDone()

    @Query("SELECT * FROM pending_uploads WHERE status = 'FAILED'")
    suspend fun getFailed(): List<PendingUploadEntity>

    @Query("UPDATE pending_uploads SET status = 'PENDING' WHERE status = 'UPLOADING'")
    suspend fun resetUploadingToPending()

    @Query("DELETE FROM pending_uploads WHERE localUri = :localUri")
    suspend fun deleteByLocalUri(localUri: String)

    @Query("SELECT localUri FROM pending_uploads")
    suspend fun getAllLocalUris(): List<String>

    @Query(
        "SELECT COUNT(*) FROM pending_uploads pu WHERE pu.status = 'PENDING' " +
            "AND pu.entityType = 'CHECKLIST_ANSWER_ATTACHMENT' " +
            "AND EXISTS (" +
                "SELECT 1 FROM checklists c WHERE pu.entityLocalId LIKE c.id || '_%' AND c.reportId = :reportId" +
            ")"
    )
    fun observePendingChecklistAttachmentUploadsCountByReport(reportId: String): Flow<Int>
}
