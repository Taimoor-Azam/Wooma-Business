package com.wooma.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wooma.data.local.entity.KeyEntity
import com.wooma.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface KeyDao {

    @Query("SELECT * FROM `keys` WHERE reportId = :reportId AND isDeleted = 0 ORDER BY displayOrder ASC")
    fun observeByReport(reportId: String): Flow<List<KeyEntity>>

    @Query("SELECT * FROM `keys` WHERE reportId = :reportId AND isDeleted = 0")
    suspend fun getByReport(reportId: String): List<KeyEntity>

    @Query("SELECT * FROM `keys` WHERE id = :id")
    suspend fun getById(id: String): KeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(key: KeyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(keys: List<KeyEntity>)

    @Query("UPDATE `keys` SET serverId = :serverId, syncStatus = 'SYNCED' WHERE id = :localId")
    suspend fun promoteLocalId(localId: String, serverId: String)

    @Query("UPDATE `keys` SET isDeleted = 1, syncStatus = 'PENDING_DELETE' WHERE id = :id")
    suspend fun softDelete(id: String)

    @Query("UPDATE `keys` SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus)

    @Query("SELECT * FROM `keys` WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSync(): List<KeyEntity>
}
