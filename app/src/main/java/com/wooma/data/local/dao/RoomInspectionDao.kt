package com.wooma.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wooma.data.local.entity.RoomInspectionEntity
import com.wooma.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomInspectionDao {

    @Query("SELECT * FROM room_inspections WHERE roomId = :roomId")
    fun observeByRoom(roomId: String): Flow<List<RoomInspectionEntity>>

    @Query("SELECT * FROM room_inspections WHERE roomId = :roomId")
    suspend fun getByRoom(roomId: String): List<RoomInspectionEntity>

    @Query("SELECT * FROM room_inspections WHERE id = :id")
    suspend fun getById(id: String): RoomInspectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(inspection: RoomInspectionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(inspections: List<RoomInspectionEntity>)

    @Query("UPDATE room_inspections SET serverId = :serverId, syncStatus = 'SYNCED' WHERE id = :localId")
    suspend fun promoteLocalId(localId: String, serverId: String)

    @Query("UPDATE room_inspections SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus)

    @Query("DELETE FROM room_inspections WHERE id = :id")
    suspend fun deleteById(id: String)
}
