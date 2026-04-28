package com.wooma.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wooma.data.local.entity.AttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {

    @Query("SELECT * FROM attachments WHERE entityId = :entityId AND entityType = :entityType")
    fun observeByEntity(entityId: String, entityType: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE entityId = :entityId AND entityType = :entityType")
    suspend fun getByEntity(entityId: String, entityType: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun getById(id: String): AttachmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(attachment: AttachmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(attachments: List<AttachmentEntity>)

    @Query("UPDATE attachments SET serverId = :serverId, storageKey = :storageKey, isUploaded = 1 WHERE id = :localId")
    suspend fun markUploaded(localId: String, serverId: String, storageKey: String)

    @Query("UPDATE attachments SET entityId = :newEntityId WHERE entityId = :oldEntityId")
    suspend fun reattachToNewEntityId(oldEntityId: String, newEntityId: String)

    @Query("SELECT * FROM attachments WHERE isUploaded = 0")
    suspend fun getPendingUploads(): List<AttachmentEntity>

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteById(id: String)
}
