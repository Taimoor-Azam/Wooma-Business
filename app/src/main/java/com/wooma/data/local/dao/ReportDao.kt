package com.wooma.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wooma.data.local.entity.ReportEntity
import com.wooma.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDao {

    @Query("SELECT * FROM reports WHERE propertyId = :propertyId AND isDeleted = 0 ORDER BY createdAt DESC")
    fun observeByProperty(propertyId: String): Flow<List<ReportEntity>>

    @Query("SELECT * FROM reports WHERE id = :id")
    fun observeById(id: String): Flow<ReportEntity?>

    @Query("SELECT * FROM reports WHERE id = :id")
    suspend fun getById(id: String): ReportEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(report: ReportEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(reports: List<ReportEntity>)

    @Query("UPDATE reports SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus)

    @Query("UPDATE reports SET serverId = :newServerId, syncStatus = 'SYNCED' WHERE id = :localId")
    suspend fun promoteLocalId(localId: String, newServerId: String)

    @Query("UPDATE reports SET countRooms = countRooms + :delta WHERE id = :reportId")
    suspend fun incrementRoomCount(reportId: String, delta: Int)

    @Query("UPDATE reports SET countMeters = countMeters + :delta WHERE id = :reportId")
    suspend fun incrementMeterCount(reportId: String, delta: Int)

    @Query("UPDATE reports SET countKeys = countKeys + :delta WHERE id = :reportId")
    suspend fun incrementKeyCount(reportId: String, delta: Int)

    @Query("UPDATE reports SET countDetectors = countDetectors + :delta WHERE id = :reportId")
    suspend fun incrementDetectorCount(reportId: String, delta: Int)

    @Query("UPDATE reports SET status = :status WHERE id = :reportId")
    suspend fun updateStatus(reportId: String, status: String)

    @Query("UPDATE reports SET coverImageStorageKey = :key WHERE id = :reportId")
    suspend fun updateCoverImage(reportId: String, key: String?)

    @Query("SELECT * FROM reports WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSyncReports(): List<ReportEntity>

    @Query("DELETE FROM reports WHERE id = :id")
    suspend fun deleteById(id: String)
}
