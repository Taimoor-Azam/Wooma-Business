package com.wooma.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wooma.data.local.entity.RoomItemEntity
import com.wooma.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomItemDao {

    @Query("SELECT * FROM room_items WHERE roomId = :roomId AND isDeleted = 0 ORDER BY displayOrder ASC")
    fun observeByRoom(roomId: String): Flow<List<RoomItemEntity>>

    @Query("SELECT * FROM room_items WHERE roomId = :roomId AND isDeleted = 0 ORDER BY displayOrder ASC")
    suspend fun getByRoom(roomId: String): List<RoomItemEntity>

    @Query("SELECT * FROM room_items WHERE id = :id")
    suspend fun getById(id: String): RoomItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: RoomItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<RoomItemEntity>)

    @Query("UPDATE room_items SET serverId = :serverId, syncStatus = 'SYNCED' WHERE id = :localId")
    suspend fun promoteLocalId(localId: String, serverId: String)

    @Query("UPDATE room_items SET isDeleted = 1, syncStatus = 'PENDING_DELETE' WHERE id = :id")
    suspend fun softDelete(id: String)

    @Query("UPDATE room_items SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus)

    @Query("SELECT * FROM room_items WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSyncItems(): List<RoomItemEntity>
}
