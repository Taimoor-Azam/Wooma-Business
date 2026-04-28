package com.wooma.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wooma.data.local.entity.MeterEntity
import com.wooma.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MeterDao {

    @Query("SELECT * FROM meters WHERE reportId = :reportId AND isDeleted = 0 ORDER BY displayOrder ASC")
    fun observeByReport(reportId: String): Flow<List<MeterEntity>>

    @Query("SELECT * FROM meters WHERE reportId = :reportId AND isDeleted = 0")
    suspend fun getByReport(reportId: String): List<MeterEntity>

    @Query("SELECT * FROM meters WHERE id = :id")
    suspend fun getById(id: String): MeterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meter: MeterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(meters: List<MeterEntity>)

    @Query("UPDATE meters SET serverId = :serverId, syncStatus = 'SYNCED' WHERE id = :localId")
    suspend fun promoteLocalId(localId: String, serverId: String)

    @Query("UPDATE meters SET isDeleted = 1, syncStatus = 'PENDING_DELETE' WHERE id = :id")
    suspend fun softDelete(id: String)

    @Query("UPDATE meters SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus)

    @Query("SELECT * FROM meters WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSync(): List<MeterEntity>
}
