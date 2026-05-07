package com.wooma.data.repository

import android.content.Context
import com.google.gson.Gson
import com.wooma.data.local.WoomaDatabase
import com.wooma.data.local.entity.AttachmentEntity
import com.wooma.data.local.entity.RoomItemEntity
import com.wooma.data.local.entity.SyncQueueEntity
import com.wooma.data.local.entity.SyncStatus
import com.wooma.data.local.mapper.toRoomItem
import com.wooma.data.local.mapper.toEntity
import com.wooma.data.network.RetrofitClient
import com.wooma.model.AddNewRoomItemsRequest
import com.wooma.model.ReorderRoomRequest
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
        replaceRoomItemsFromServer(reportId, roomId)
    }

    suspend fun reorderItem(localId: String, prevRank: String?, nextRank: String?) = withContext(Dispatchers.IO) {
        val item = db.roomItemDao().getByLocalOrServerId(localId) ?: return@withContext
        if (item.syncStatus != SyncStatus.SYNCED) return@withContext
        val room = db.roomDao().getById(item.roomId) ?: return@withContext
        val roomServerId = room.serverId ?: return@withContext
        val reportServerId = db.reportDao().getById(room.reportId)?.serverId ?: return@withContext
        val itemServerId = item.serverId ?: return@withContext

        val reorderResp = api.reorderRoomItem(
            reportId = reportServerId,
            roomId = roomServerId,
            itemId = itemServerId,
            request = ReorderRoomRequest(prev_rank = prevRank, next_rank = nextRank)
        ).execute()
        if (!reorderResp.isSuccessful) throw Exception("Room item REORDER failed: ${reorderResp.code()}")
        replaceRoomItemsFromServer(room.reportId, item.roomId)
    }

    private suspend fun replaceRoomItemsFromServer(reportId: String, roomId: String) {
        val room = db.roomDao().getById(roomId) ?: return
        val roomServerId = room.serverId ?: return
        val reportServerId = db.reportDao().getById(reportId)?.serverId ?: return

        val resp = api.getInspectionRoomById(
            report_id = reportServerId,
            include_items = true,
            include_room_inspections = false
        ).execute()

        resp.body()?.data?.find { it.id == roomServerId }?.items?.let { items ->
            val sortedItems = items.sortedWith(compareBy<RoomItem> { roomItemSortKey(it.display_order) }.thenBy { it.id })
            val unsyncedServerItemIds = db.roomItemDao().getUnsyncedByRoom(roomId)
                .mapNotNull { it.serverId ?: it.id.takeIf { localId -> !localId.startsWith("local_") } }
                .toSet()

            val toUpsert = sortedItems.map { it.toEntity(roomId) }
                .filter { it.id !in unsyncedServerItemIds }
            db.roomItemDao().upsertAll(toUpsert)

            val incomingServerIds = sortedItems.mapNotNull { it.id }
            if (incomingServerIds.isEmpty()) db.roomItemDao().deleteSyncedByRoom(roomId)
            else db.roomItemDao().deleteSyncedByRoomExcept(roomId, incomingServerIds)

            val existingAtts = db.attachmentDao().getByEntityType("ROOM_ITEM")
            val existingAttIds = existingAtts.map { it.id }.toSet()
            val existingAttServerIds = existingAtts.mapNotNull { it.serverId }.toSet()
            val roomItemAttachments = sortedItems
                .filter { item -> toUpsert.any { it.id == item.id } }
                .flatMap { item ->
                    (item.attachments ?: emptyList()).mapNotNull { att ->
                        if (att.storageKey.isNullOrEmpty()) return@mapNotNull null
                        val attId = att.id ?: return@mapNotNull null
                        if (attId !in existingAttIds && attId in existingAttServerIds) return@mapNotNull null
                        AttachmentEntity(
                            id = attId,
                            serverId = attId,
                            entityId = item.id ?: return@mapNotNull null,
                            entityType = "ROOM_ITEM",
                            storageKey = att.storageKey,
                            link = att.url,
                            localUri = null,
                            isUploaded = true
                        )
                    }
                }
            if (roomItemAttachments.isNotEmpty()) db.attachmentDao().upsertAll(roomItemAttachments)
        }
    }

    private fun roomItemSortKey(rank: String?): String {
        val value = rank?.trim().orEmpty()
        return if (value.isEmpty()) "\uFFFF" else value
    }

    suspend fun addItems(roomId: String, names: List<String>) {
        val now = System.currentTimeMillis().toString()
        names.forEach { name ->
            val localId = "local_${UUID.randomUUID().toString().replace("-", "")}"
            val entity = RoomItemEntity(
                id = localId, serverId = null, roomId = roomId,
                name = name, isDeleted = false,
                createdAt = now, updatedAt = now,
                syncStatus = SyncStatus.PENDING_CREATE
            )
            db.roomItemDao().upsert(entity)
            db.syncQueueDao().enqueue(
                SyncQueueEntity(
                    entityType = "ROOM_ITEM", operationType = "CREATE",
                    localEntityId = localId,
                    payload = gson.toJson(AddNewRoomItemsRequest(room_items = listOf(name)))
                )
            )
        }
    }

    suspend fun updateItem(id: String, request: UpdateRoomItemRequest) {
        val existing = db.roomItemDao().getByLocalOrServerId(id) ?: return
        db.roomItemDao().upsert(
            existing.copy(
                name = request.name ?: existing.name,
                generalCondition = request.general_condition,
                generalCleanliness = request.general_cleanliness,
                description = request.description,
                note = request.note,
                updatedAt = System.currentTimeMillis().toString()
            )
        )
        if (existing.syncStatus == SyncStatus.SYNCED) {
            db.roomItemDao().updateSyncStatus(existing.id, SyncStatus.PENDING_UPDATE)
            db.syncQueueDao().enqueue(
                SyncQueueEntity(
                    entityType = "ROOM_ITEM", operationType = "UPDATE",
                    localEntityId = existing.id, payload = gson.toJson(request)
                )
            )
        }
    }

    suspend fun deleteItem(id: String) {
        val existing = db.roomItemDao().getByLocalOrServerId(id) ?: return
        if (existing.syncStatus == SyncStatus.PENDING_CREATE) {
            db.roomItemDao().deleteById(existing.id)
            db.syncQueueDao().cancelPendingCreate(existing.id, "ROOM_ITEM")
        } else {
            db.roomItemDao().softDelete(existing.id)
            val serverId = existing.serverId
            if (serverId != null && serverId != existing.id) {
                db.roomItemDao().deleteById(serverId)
            }
            db.syncQueueDao().enqueue(
                SyncQueueEntity(
                    entityType = "ROOM_ITEM", operationType = "DELETE",
                    localEntityId = existing.id, payload = "{}"
                )
            )
        }
    }
}
