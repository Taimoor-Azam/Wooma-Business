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

    @Query("SELECT * FROM detectors WHERE reportId = :reportId")
    suspend fun getAllByReport(reportId: String): List<DetectorEntity>

    @Query("SELECT * FROM detectors WHERE id = :id")
    suspend fun getById(id: String): DetectorEntity?

    @Query("SELECT * FROM detectors WHERE id = :id OR serverId = :id ORDER BY CASE WHEN id LIKE 'local_%' THEN 0 ELSE 1 END LIMIT 1")
    suspend fun getByLocalOrServerId(id: String): DetectorEntity?

    @Query("DELETE FROM detectors WHERE id = :id")
    suspend fun deleteById(id: String)

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

    @Query("DELETE FROM detectors WHERE reportId = :reportId AND syncStatus = 'SYNCED'")
    suspend fun deleteSyncedByReport(reportId: String)

    @Query("SELECT COUNT(*) FROM detectors WHERE reportId = :reportId AND isDeleted = 0 AND syncStatus != 'SYNCED'")
    fun observeUnsyncedCountByReport(reportId: String): Flow<Int>
}
