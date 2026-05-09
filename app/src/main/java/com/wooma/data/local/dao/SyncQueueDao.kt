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

    @Query(
        "SELECT sq.* FROM sync_queue sq " +
            "INNER JOIN rooms r ON r.id = sq.localEntityId " +
            "WHERE sq.status = 'PENDING' " +
            "AND sq.entityType = 'ROOM' " +
            "AND sq.operationType = 'CREATE' " +
            "AND r.reportId = :reportId " +
            "ORDER BY sq.createdAt ASC"
    )
    suspend fun getPendingRoomCreatesByReport(reportId: String): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING'")
    fun countPending(): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM sync_queue sq WHERE sq.status = 'PENDING' AND (" +
            "(sq.entityType IN ('REPORT','REPORT_ASSESSOR','REPORT_DATE','REPORT_TYPE','REPORT_COVER') " +
                "AND sq.localEntityId = :reportId) OR " +
            "(sq.entityType = 'ROOM' AND EXISTS (" +
                "SELECT 1 FROM rooms r WHERE r.id = sq.localEntityId AND r.reportId = :reportId" +
            ")) OR " +
            "(sq.entityType = 'ROOM_ITEM' AND EXISTS (" +
                "SELECT 1 FROM room_items ri INNER JOIN rooms r ON r.id = ri.roomId " +
                "WHERE ri.id = sq.localEntityId AND r.reportId = :reportId" +
            ")) OR " +
            "(sq.entityType = 'ROOM_INSPECTION' AND EXISTS (" +
                "SELECT 1 FROM room_inspections ri INNER JOIN rooms r ON r.id = ri.roomId " +
                "WHERE ri.id = sq.localEntityId AND r.reportId = :reportId" +
            ")) OR " +
            "(sq.entityType = 'METER' AND EXISTS (" +
                "SELECT 1 FROM meters m WHERE m.id = sq.localEntityId AND m.reportId = :reportId" +
            ")) OR " +
            "(sq.entityType = 'KEY' AND EXISTS (" +
                "SELECT 1 FROM `keys` k WHERE k.id = sq.localEntityId AND k.reportId = :reportId" +
            ")) OR " +
            "(sq.entityType = 'DETECTOR' AND EXISTS (" +
                "SELECT 1 FROM detectors d WHERE d.id = sq.localEntityId AND d.reportId = :reportId" +
            ")) OR " +
            "(sq.entityType IN ('CHECKLIST_STATUS') AND EXISTS (" +
                "SELECT 1 FROM checklists c WHERE c.id = sq.localEntityId AND c.reportId = :reportId" +
            ")) OR " +
            "(sq.entityType IN ('CHECKLIST_QUESTION','CHECKLIST_INFO_FIELD','CHECKLIST_ANSWER_ATTACHMENT') AND EXISTS (" +
                "SELECT 1 FROM checklists c WHERE sq.localEntityId LIKE c.id || '_%' AND c.reportId = :reportId" +
            ")) OR " +
            "(sq.entityType = 'ATTACHMENT' AND EXISTS (" +
                "SELECT 1 FROM attachments a " +
                "LEFT JOIN meters m ON a.entityType = 'METER' AND m.id = a.entityId " +
                "LEFT JOIN `keys` k ON a.entityType = 'KEY' AND k.id = a.entityId " +
                "LEFT JOIN detectors d ON a.entityType = 'DETECTOR' AND d.id = a.entityId " +
                "LEFT JOIN room_items ri ON a.entityType = 'ROOM_ITEM' AND ri.id = a.entityId " +
                "LEFT JOIN rooms rr ON rr.id = ri.roomId " +
                "WHERE a.id = sq.localEntityId AND (" +
                    "(a.entityType = 'METER' AND m.reportId = :reportId) OR " +
                    "(a.entityType = 'KEY' AND k.reportId = :reportId) OR " +
                    "(a.entityType = 'DETECTOR' AND d.reportId = :reportId) OR " +
                    "(a.entityType = 'ROOM_ITEM' AND rr.reportId = :reportId)" +
                ")" +
            "))" +
        ")"
    )
    fun observePendingCountByReport(reportId: String): Flow<Int>

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
