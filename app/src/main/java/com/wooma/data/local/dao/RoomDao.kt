package com.wooma.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wooma.data.local.entity.RoomEntity
import com.wooma.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomDao {

    @Query("SELECT * FROM rooms WHERE reportId = :reportId ORDER BY displayOrder ASC")
    fun observeByReport(reportId: String): Flow<List<RoomEntity>>

    @Query("SELECT * FROM rooms WHERE reportId = :reportId ORDER BY displayOrder ASC")
    suspend fun getByReport(reportId: String): List<RoomEntity>

    @Query("SELECT * FROM rooms WHERE id = :id")
    suspend fun getById(id: String): RoomEntity?

    @Query("SELECT * FROM rooms WHERE serverId = :serverId")
    suspend fun getByServerId(serverId: String): RoomEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(room: RoomEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rooms: List<RoomEntity>)

    @Query("UPDATE rooms SET serverId = :serverId, syncStatus = 'SYNCED' WHERE id = :localId")
    suspend fun promoteLocalId(localId: String, serverId: String)

    @Query("UPDATE rooms SET name = :name, syncStatus = :status WHERE id = :id")
    suspend fun updateName(id: String, name: String, status: SyncStatus)

    @Query("UPDATE rooms SET displayOrder = :order WHERE id = :id")
    suspend fun updateOrder(id: String, order: String)

    @Query("UPDATE rooms SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus)

    @Query("DELETE FROM rooms WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM rooms WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSyncRooms(): List<RoomEntity>
}
