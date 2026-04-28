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

    @Query("SELECT * FROM sync_queue WHERE id = :id")
    suspend fun getById(id: Long): SyncQueueEntity?

    @Insert
    suspend fun enqueue(entry: SyncQueueEntity): Long

    @Query("UPDATE sync_queue SET status = :status, errorMessage = :msg WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, msg: String? = null)

    @Query("UPDATE sync_queue SET status = 'PENDING', retryCount = retryCount + 1 WHERE id = :id")
    suspend fun requeueForRetry(id: Long)

    @Query("UPDATE sync_queue SET serverEntityId = :serverId WHERE localEntityId = :localId AND entityType = :type")
    suspend fun updateServerEntityId(localId: String, type: String, serverId: String)

    @Query("DELETE FROM sync_queue WHERE status = 'DONE'")
    suspend fun purgeDone()

    @Query("SELECT * FROM sync_queue WHERE status = 'FAILED'")
    suspend fun getFailed(): List<SyncQueueEntity>
}
