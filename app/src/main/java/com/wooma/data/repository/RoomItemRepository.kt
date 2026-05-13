package com.wooma.data.repository

import android.content.Context
import com.google.gson.Gson
import com.wooma.data.local.WoomaDatabase
import com.wooma.data.local.entity.AttachmentEntity
import com.wooma.data.local.entity.RoomItemEntity
import com.wooma.data.local.entity.SyncQueueEntity
import com.wooma.data.local.entity.SyncStatus
import com.wooma.data.local.mapper.stableTenantReportItemId
import com.wooma.data.local.mapper.toRoomItem
import com.wooma.data.local.mapper.toEntity
import com.wooma.data.local.mapper.withStableIdIfMissing
import com.wooma.data.network.RetrofitClient
import androidx.room.withTransaction
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

    fun observeItems(reportId: String, navRoomId: String): Flow<List<RoomItem>> =
        db.roomItemDao().observeItemsForReportRoom(reportId, navRoomId).map { entities ->
            entities.map { it.toRoomItem() }
        }

    /**
     * Tenant-hydrated rows may use synthetic [RoomItemEntity.id] (`tr_item_*`) with null [RoomItemEntity.serverId].
     * Match the server item (stable key or same id) and persist [serverId] without changing [RoomItemEntity.syncStatus].
     */
    suspend fun resolveRoomItemServerIdIfMissing(roomItemLocalId: String): String? = withContext(Dispatchers.IO) {
        val entity = db.roomItemDao().getByLocalOrServerId(roomItemLocalId) ?: return@withContext null
        entity.serverId?.let { return@withContext it }
        val room = db.roomDao().getById(entity.roomId) ?: return@withContext null
        val roomServerId = room.serverId ?: return@withContext null
        val reportServerId = db.reportDao().getById(room.reportId)?.serverId ?: return@withContext null

        val resp = api.getRoomById(
            id = roomServerId,
            report_id = reportServerId,
            include_items = true,
            include_room_inspections = false
        ).execute()
        if (!resp.isSuccessful) return@withContext null
        val items = resp.body()?.data?.items ?: return@withContext null

        val matched = items.firstOrNull { serverItem ->
            val sid = serverItem.id?.trim().orEmpty()
            when {
                sid.isNotEmpty() && sid == entity.id -> true
                entity.id.startsWith("tr_item_") ->
                    stableTenantReportItemId(entity.roomId, serverItem.display_order, serverItem.name) == entity.id
                else -> false
            }
        } ?: return@withContext null

        val serverItemId = matched.id?.trim().orEmpty().ifEmpty { return@withContext null }
        if (serverItemId != entity.id) {
            db.roomItemDao().updateServerId(entity.id, serverItemId)
            db.syncQueueDao().updateServerEntityId(entity.id, "ROOM_ITEM", serverItemId)
            db.pendingUploadDao().updateEntityServerId(entity.id, "ROOM_ITEM", serverItemId)
        }
        serverItemId
    }

    suspend fun refreshItems(reportId: String, navRoomId: String) = withContext(Dispatchers.IO) {
        val localRoomId = db.roomDao().getById(navRoomId)?.id
            ?: db.roomDao().getByServerId(navRoomId)?.id
            ?: return@withContext
        replaceRoomItemsFromServer(reportId, localRoomId)
    }

    suspend fun reorderItem(localId: String, prevRank: String?, nextRank: String?) = withContext(Dispatchers.IO) {
        val item = db.roomItemDao().getByLocalOrServerId(localId) ?: return@withContext
        if (item.syncStatus != SyncStatus.SYNCED) return@withContext
        val room = db.roomDao().getById(item.roomId) ?: return@withContext
        val roomServerId = room.serverId ?: return@withContext
        val reportServerId = db.reportDao().getById(room.reportId)?.serverId ?: return@withContext
        val itemServerId = item.serverId ?: resolveRoomItemServerIdIfMissing(item.id) ?: return@withContext

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
            if (items.isEmpty()) return@let
            val normalized = items.map { it.withStableIdIfMissing(roomId) }
            val sortedItems = normalized.sortedWith(
                compareBy<RoomItem> { roomItemSortKey(it.display_order) }.thenBy { it.id }
            )
            db.withTransaction {
                val unsyncedServerItemIds = db.roomItemDao().getUnsyncedByRoom(roomId)
                    .mapNotNull { it.serverId ?: it.id.takeIf { localId -> !localId.startsWith("local_") } }
                    .toSet()

                val entities = sortedItems.map { it.toEntity(roomId) }
                val shadowServerIds = entities.map { it.id }.filter { it.isNotEmpty() }.distinct()
                if (shadowServerIds.isNotEmpty()) {
                    db.roomItemDao().deleteShadowRowsForServerIds(roomId, shadowServerIds)
                }

                val toUpsert = entities.filter { it.id !in unsyncedServerItemIds }
                db.roomItemDao().upsertAll(toUpsert)

                val upsertedIds = toUpsert.map { it.id }.toSet()
                val incomingIds = entities.map { it.id }
                if (incomingIds.isEmpty()) db.roomItemDao().deleteSyncedByRoom(roomId)
                else db.roomItemDao().deleteSyncedByRoomExcept(roomId, incomingIds)

                val existingAtts = db.attachmentDao().getByEntityType("ROOM_ITEM")
                val existingAttIds = existingAtts.map { it.id }.toSet()
                val existingAttServerIds = existingAtts.mapNotNull { it.serverId }.toSet()
                val roomItemAttachments = sortedItems.zip(entities).flatMap { (item, entity) ->
                    if (entity.id !in upsertedIds) return@flatMap emptyList()
                    (item.attachments ?: emptyList()).mapNotNull { att ->
                        if (att.storageKey.isNullOrEmpty()) return@mapNotNull null
                        val attId = att.id ?: return@mapNotNull null
                        if (attId !in existingAttIds && attId in existingAttServerIds) return@mapNotNull null
                        AttachmentEntity(
                            id = attId,
                            serverId = attId,
                            entityId = entity.id,
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
    }

    private fun roomItemSortKey(rank: String?): String {
        val value = rank?.trim().orEmpty()
        return if (value.isEmpty()) "\uFFFF" else value
    }

    /** Preferred [RatingEntity.typeCode] for new offline items, from cached getRatings rows. */
    private suspend fun defaultConditionAndCleanlinessTypeCodes(): Pair<String?, String?> {
        val conditionList = db.ratingDao().getByCategory("condition")
        val cleanlinessList = db.ratingDao().getByCategory("cleanliness")
        val conditionCode =
            conditionList.firstOrNull { it.isDefault }?.typeCode ?: conditionList.firstOrNull()?.typeCode
        val cleanlinessCode =
            cleanlinessList.firstOrNull { it.isDefault }?.typeCode ?: cleanlinessList.firstOrNull()?.typeCode
        return conditionCode to cleanlinessCode
    }

    suspend fun addItems(navRoomId: String, names: List<String>) {
        val canonicalRoomId = db.roomDao().getById(navRoomId)?.id
            ?: db.roomDao().getByServerId(navRoomId)?.id
            ?: return
        val now = System.currentTimeMillis().toString()
        val (defaultCondition, defaultCleanliness) = defaultConditionAndCleanlinessTypeCodes()
        names.forEach { name ->
            val localId = "local_${UUID.randomUUID().toString().replace("-", "")}"
            val entity = RoomItemEntity(
                id = localId, serverId = null, roomId = canonicalRoomId,
                name = name, isDeleted = false,
                generalCondition = defaultCondition,
                generalCleanliness = defaultCleanliness,
                createdAt = now, updatedAt = now,
                syncStatus = SyncStatus.PENDING_CREATE
            )
            db.roomItemDao().upsert(entity)
            val parentRoomCreateId = db.syncQueueDao().getPendingRoomCreateQueueId(canonicalRoomId)
            db.syncQueueDao().enqueue(
                SyncQueueEntity(
                    entityType = "ROOM_ITEM", operationType = "CREATE",
                    localEntityId = localId,
                    payload = gson.toJson(AddNewRoomItemsRequest(room_items = listOf(name))),
                    parentSyncId = parentRoomCreateId
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
            db.syncQueueDao().enqueue(
                SyncQueueEntity(
                    entityType = "ROOM_ITEM", operationType = "DELETE",
                    localEntityId = existing.id, payload = "{}"
                )
            )
        }
    }
}
