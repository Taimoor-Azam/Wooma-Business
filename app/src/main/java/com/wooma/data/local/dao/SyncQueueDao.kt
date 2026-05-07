package com.wooma.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.wooma.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' ORDER BY CASE WHEN parentSyncId IS NULL THEN 0 ELSE 1 END ASC, createdAt ASC")
    suspend fun getPendingInOrder(): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING'")
    fun countPending(): Flow<Int>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM sync_queue WHERE status = 'PENDING' " +
            "AND entityType = :entityType AND localEntityId = :localEntityId)"
    )
    suspend fun hasPendingForEntity(entityType: String, localEntityId: String): Boolean

    @Query("SELECT * FROM sync_queue WHERE id = :id")
    suspend fun getById(id: Long): SyncQueueEntity?

    @Query(
        "SELECT EXISTS(SELECT 1 FROM sync_queue WHERE status = 'PENDING' " +
            "AND entityType = 'REPORT_COVER' AND operationType = 'DELETE' AND localEntityId = :reportId)"
    )
    suspend fun hasPendingReportCoverDelete(reportId: String): Boolean

    @Insert
    suspend fun enqueue(entry: SyncQueueEntity): Long

    @Query("UPDATE sync_queue SET status = :status, errorMessage = :msg WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, msg: String? = null)

    @Query("UPDATE sync_queue SET status = 'PENDING', retryCount = retryCount + 1 WHERE id = :id")
    suspend fun requeueForRetry(id: Long)

    @Query("UPDATE sync_queue SET serverEntityId = :serverId WHERE localEntityId = :localId AND entityType = :type")
    suspend fun updateServerEntityId(localId: String, type: String, serverId: String)

    @Query(
        "DELETE FROM sync_queue WHERE status = 'PENDING' AND entityType = :entityType " +
            "AND operationType = :operationType AND localEntityId = :localEntityId"
    )
    suspend fun deletePendingByEntityOperation(
        entityType: String,
        operationType: String,
        localEntityId: String
    )

    @Query("DELETE FROM sync_queue WHERE localEntityId = :localId AND entityType = :entityType AND operationType = 'CREATE' AND status = 'PENDING'")
    suspend fun cancelPendingCreate(localId: String, entityType: String)

    @Query("DELETE FROM sync_queue WHERE status = 'DONE'")
    suspend fun purgeDone()

    @Query(
        "SELECT EXISTS(SELECT 1 FROM sync_queue WHERE status = 'PENDING' " +
            "AND entityType = 'CHECKLIST_ANSWER_ATTACHMENT' AND operationType = 'CREATE' " +
            "AND localEntityId = :localId)"
    )
    suspend fun hasPendingChecklistAttachmentCreate(localId: String): Boolean

    @Query("SELECT * FROM sync_queue WHERE status = 'FAILED'")
    suspend fun getFailed(): List<SyncQueueEntity>

    @Query("UPDATE sync_queue SET status = 'PENDING' WHERE status = 'IN_PROGRESS'")
    suspend fun resetInProgressToPending()

    @Query(
        "DELETE FROM sync_queue WHERE status = 'PENDING' AND entityType = 'CHECKLIST_STATUS' " +
            "AND id NOT IN (" +
                "SELECT MAX(id) FROM sync_queue " +
                "WHERE status = 'PENDING' AND entityType = 'CHECKLIST_STATUS' " +
                "GROUP BY localEntityId" +
            ")"
    )
    suspend fun pruneDuplicatePendingChecklistStatus()

    @Query(
        "SELECT COUNT(*) FROM sync_queue sq WHERE sq.status = 'PENDING' AND (" +
            "(sq.entityType = 'CHECKLIST_STATUS' AND EXISTS (" +
                "SELECT 1 FROM checklists c WHERE c.id = sq.localEntityId AND c.reportId = :reportId" +
            ")) OR " +
            "(sq.entityType IN ('CHECKLIST_QUESTION','CHECKLIST_INFO_FIELD','CHECKLIST_ANSWER_ATTACHMENT') AND EXISTS (" +
                "SELECT 1 FROM checklists c WHERE sq.localEntityId LIKE c.id || '_%' AND c.reportId = :reportId" +
            "))" +
        ")"
    )
    fun observePendingChecklistOpsCountByReport(reportId: String): Flow<Int>
}
