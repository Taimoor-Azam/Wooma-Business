package com.wooma.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wooma.data.local.entity.DetectorEntity
import com.wooma.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DetectorDao {

    @Query("SELECT * FROM detectors WHERE reportId = :reportId AND isDeleted = 0 ORDER BY displayOrder ASC")
    fun observeByReport(reportId: String): Flow<List<DetectorEntity>>

    @Query("SELECT * FROM detectors WHERE reportId = :reportId AND isDeleted = 0")
    suspend fun getByReport(reportId: String): List<DetectorEntity>

    @Query("SELECT * FROM detectors WHERE id = :id")
    suspend fun getById(id: String): DetectorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(detector: DetectorEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(detectors: List<DetectorEntity>)

    @Query("UPDATE detectors SET serverId = :serverId, syncStatus = 'SYNCED' WHERE id = :localId")
    suspend fun promoteLocalId(localId: String, serverId: String)

    @Query("UPDATE detectors SET isDeleted = 1, syncStatus = 'PENDING_DELETE' WHERE id = :id")
    suspend fun softDelete(id: String)

    @Query("UPDATE detectors SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus)

    @Query("SELECT * FROM detectors WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSync(): List<DetectorEntity>
}
