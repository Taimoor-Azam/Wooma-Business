package com.wooma.data.repository

import android.content.Context
import com.google.gson.Gson
import com.wooma.data.local.WoomaDatabase
import com.wooma.data.local.entity.RoomItemEntity
import com.wooma.data.local.entity.SyncQueueEntity
import com.wooma.data.local.entity.SyncStatus
import com.wooma.data.local.mapper.toRoomItem
import com.wooma.data.local.mapper.toEntity
import com.wooma.data.network.RetrofitClient
import com.wooma.model.RoomItem
import com.wooma.model.UpdateRoomItemRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class RoomItemRepository(private val ctx: Context) {

    private val db = WoomaDatabase.getInstance(ctx)
    private val api by lazy { RetrofitClient.getApi(ctx) }
    private val gson = Gson()

    fun observeItems(roomId: String): Flow<List<RoomItem>> =
        db.roomItemDao().observeByRoom(roomId).map { entities ->
            entities.map { it.toRoomItem() }
        }

    suspend fun refreshItems(reportId: String, roomId: String) = withContext(Dispatchers.IO) {
        val room = db.roomDao().getById(roomId) ?: return@withContext
        val roomServerId = room.serverId ?: return@withContext
        val reportServerId = db.reportDao().getById(reportId)?.serverId ?: return@withContext

        val resp = api.getInspectionRoomById(
            report_id = reportServerId,
            include_items = true,
            include_room_inspections = false
        ).execute()

        resp.body()?.data?.find { it.id == roomServerId }?.items?.let { items ->
            val pendingIds = db.roomItemDao().getPendingSyncItems().map { it.id }.toSet()
            val entities = items.map { it.toEntity(roomId) }.filter { it.id !in pendingIds }
            db.roomItemDao().upsertAll(entities)
        }
    }

    suspend fun updateItem(localId: String, request: UpdateRoomItemRequest) {
        val existing = db.roomItemDao().getById(localId) ?: return
        val updated = existing.copy(
            name = request.name ?: existing.name,
            generalCondition = request.general_condition,
            generalCleanliness = request.general_cleanliness,
            description = request.description,
            note = request.note,
            updatedAt = System.currentTimeMillis().toString()
        )
        db.roomItemDao().upsert(updated)

        if (existing.syncStatus == SyncStatus.SYNCED) {
            db.roomItemDao().updateSyncStatus(localId, SyncStatus.PENDING_UPDATE)
            db.syncQueueDao().enqueue(
                SyncQueueEntity(
                    entityType = "ROOM_ITEM", operationType = "UPDATE",
                    localEntityId = localId, payload = gson.toJson(request)
                )
            )
        }
    }

    suspend fun deleteItem(localId: String) {
        val existing = db.roomItemDao().getById(localId) ?: return
        db.roomItemDao().softDelete(localId)
        
        if (existing.syncStatus != SyncStatus.PENDING_CREATE) {
            db.syncQueueDao().enqueue(
                SyncQueueEntity(
                    entityType = "ROOM_ITEM", operationType = "DELETE",
                    localEntityId = localId, payload = "{}"
                )
            )
        }
    }
}
