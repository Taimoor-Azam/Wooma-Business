package com.wooma.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.wooma.data.local.entity.PendingUploadEntity

@Dao
interface PendingUploadDao {

    @Query("SELECT * FROM pending_uploads WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPending(): List<PendingUploadEntity>

    @Query("SELECT * FROM pending_uploads WHERE id = :id")
    suspend fun getById(id: Long): PendingUploadEntity?

    @Insert
    suspend fun enqueue(entry: PendingUploadEntity): Long

    @Query("UPDATE pending_uploads SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE pending_uploads SET entityServerId = :serverId WHERE entityLocalId = :localId AND entityType = :type")
    suspend fun updateEntityServerId(localId: String, type: String, serverId: String)

    @Query("UPDATE pending_uploads SET status = 'DONE', storageKey = :key, attachmentServerId = :attachmentId WHERE id = :id")
    suspend fun markDone(id: Long, key: String, attachmentId: String)

    @Query("UPDATE pending_uploads SET status = 'PENDING', retryCount = retryCount + 1 WHERE id = :id")
    suspend fun requeueForRetry(id: Long)

    @Query("DELETE FROM pending_uploads WHERE status = 'DONE'")
    suspend fun purgeDone()

    @Query("SELECT * FROM pending_uploads WHERE status = 'FAILED'")
    suspend fun getFailed(): List<PendingUploadEntity>
}
