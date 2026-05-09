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

    @Query(
        "SELECT * FROM room_items WHERE roomId = :roomId AND " +
            "(isDeleted = 0 OR syncStatus = 'PENDING_DELETE') " +
            "ORDER BY CASE WHEN displayOrder IS NULL OR displayOrder = '' THEN 1 ELSE 0 END, displayOrder ASC"
    )
    fun observeByRoom(roomId: String): Flow<List<RoomItemEntity>>

    @Query(
        "SELECT * FROM room_items WHERE roomId = :roomId AND " +
            "(isDeleted = 0 OR syncStatus = 'PENDING_DELETE') " +
            "ORDER BY CASE WHEN displayOrder IS NULL OR displayOrder = '' THEN 1 ELSE 0 END, displayOrder ASC"
    )
    suspend fun getByRoom(roomId: String): List<RoomItemEntity>

    @Query("SELECT * FROM room_items WHERE roomId = :roomId AND syncStatus != 'SYNCED'")
    suspend fun getUnsyncedByRoom(roomId: String): List<RoomItemEntity>

    @Query("SELECT * FROM room_items WHERE roomId = :roomId")
    suspend fun getAllByRoom(roomId: String): List<RoomItemEntity>

    @Query("SELECT * FROM room_items WHERE id = :id")
    suspend fun getById(id: String): RoomItemEntity?

    @Query("SELECT * FROM room_items WHERE id = :id OR serverId = :id ORDER BY CASE WHEN id LIKE 'local_%' THEN 0 ELSE 1 END LIMIT 1")
    suspend fun getByLocalOrServerId(id: String): RoomItemEntity?

    @Query("DELETE FROM room_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: RoomItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<RoomItemEntity>)

    @Query("DELETE FROM room_items WHERE roomId = :roomId AND syncStatus = 'SYNCED'")
    suspend fun deleteSyncedByRoom(roomId: String)

    @Query("DELETE FROM room_items WHERE roomId = :roomId AND syncStatus = 'SYNCED' AND id NOT IN (:serverItemIds)")
    suspend fun deleteSyncedByRoomExcept(roomId: String, serverItemIds: List<String>)

    @Query("UPDATE room_items SET serverId = :serverId, syncStatus = 'SYNCED' WHERE id = :localId")
    suspend fun promoteLocalId(localId: String, serverId: String)

    @Query("UPDATE room_items SET isDeleted = 1, syncStatus = 'PENDING_DELETE' WHERE id = :id")
    suspend fun softDelete(id: String)

    @Query("UPDATE room_items SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus)

    @Query("SELECT * FROM room_items WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSyncItems(): List<RoomItemEntity>

    @Query("SELECT COUNT(*) FROM room_items WHERE roomId = :roomId AND syncStatus != 'SYNCED'")
    fun observeUnsyncedCountByRoom(roomId: String): Flow<Int>

    @Query(
        "SELECT DISTINCT ri.roomId FROM room_items ri " +
            "INNER JOIN rooms r ON r.id = ri.roomId " +
            "WHERE r.reportId = :reportId AND ri.syncStatus != 'SYNCED'"
    )
    fun observePendingRoomIdsByReport(reportId: String): Flow<List<String>>
}
