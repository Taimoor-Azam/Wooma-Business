package com.wooma.data.repository

import android.content.Context
import com.google.gson.Gson
import com.wooma.data.local.WoomaDatabase
import com.wooma.data.local.mapper.toEntity
import com.wooma.data.local.entity.RoomEntity
import com.wooma.data.local.entity.SyncQueueEntity
import com.wooma.data.local.entity.SyncStatus
import com.wooma.data.network.RetrofitClient
import com.wooma.model.AddNewRoomsRequest
import com.wooma.model.ReportData
import com.wooma.model.ReorderRoomRequest
import com.wooma.model.RoomsResponse
import com.wooma.model.UpdateRoomNameRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class RoomRepository(private val ctx: Context) {

    private val db = WoomaDatabase.getInstance(ctx)
    private val api by lazy { RetrofitClient.getApi(ctx) }
    private val gson = Gson()

    fun observeRooms(reportId: String): Flow<List<RoomsResponse>> =
        db.roomDao().observeByReport(reportId).map { entities ->
            entities.filter { it.syncStatus != SyncStatus.PENDING_DELETE }.map { it.toResponse() }
        }

    suspend fun hydrateRoomsAndItemsFromReport(reportData: ReportData, reportId: String) =
        withContext(Dispatchers.IO) {
            val rooms = reportData.rooms ?: return@withContext
            mergeServerRoomsAndItems(reportId, rooms)
        }

    suspend fun refreshRooms(reportId: String) = withContext(Dispatchers.IO) {
        val serverId = db.reportDao().getById(reportId)?.serverId ?: return@withContext
        val resp = api.getInspectionRoomById(
            report_id = serverId,
            include_items = true,
            include_room_inspections = false,
            include_attachments = false
        ).execute()
        resp.body()?.data?.let { rooms ->
            mergeServerRoomsAndItems(reportId, rooms)
        }
    }

    private suspend fun mergeServerRoomsAndItems(reportId: String, rooms: List<RoomsResponse>) {
        val pendingRoomIds = db.roomDao().getPendingSyncRooms().map { it.id }.toSet()
        for (room in rooms) {
            val serverRoomId = room.id ?: continue

            val existingByServer = db.roomDao().getByServerId(serverRoomId)
            val localRoomId = existingByServer?.id ?: serverRoomId

            // Preserve local unsynced room rows (create/update/delete) and only refresh synced rows.
            if (localRoomId !in pendingRoomIds) {
                if (existingByServer != null) {
                    db.roomDao().updateFromServerByServerId(
                        serverId = serverRoomId,
                        name = room.name ?: "",
                        templateId = room.templateId,
                        displayOrder = room.displayOrder
                    )
                } else {
                    val updated = db.roomDao().updateFromServer(
                        id = serverRoomId,
                        name = room.name ?: "",
                        templateId = room.templateId,
                        displayOrder = room.displayOrder
                    )
                    if (updated == 0) {
                        db.roomDao().insertIgnore(
                            RoomEntity(
                                id = serverRoomId,
                                serverId = serverRoomId,
                                reportId = reportId,
                                templateId = room.templateId,
                                name = room.name ?: "",
                                displayOrder = room.displayOrder,
                                syncStatus = SyncStatus.SYNCED
                            )
                        )
                    }
                }
            }

            mergeRoomItemsForRoom(localRoomId, room)
        }
    }

    private suspend fun mergeRoomItemsForRoom(localRoomId: String, room: RoomsResponse) {
        val incomingItems = room.items ?: return
        val unsyncedServerItemIds = db.roomItemDao().getUnsyncedByRoom(localRoomId)
            .mapNotNull { entity ->
                entity.serverId ?: entity.id.takeIf { !it.startsWith("local_") }
            }
            .toSet()

        val incomingEntities = incomingItems.map { it.toEntity(localRoomId) }
        val entitiesToUpsert = incomingEntities.filter { it.id !in unsyncedServerItemIds }
        if (entitiesToUpsert.isNotEmpty()) {
            db.roomItemDao().upsertAll(entitiesToUpsert)
        }

        val serverItemIds = incomingEntities.map { it.id }
        if (serverItemIds.isEmpty()) {
            db.roomItemDao().deleteSyncedByRoom(localRoomId)
        } else {
            db.roomItemDao().deleteSyncedByRoomExcept(localRoomId, serverItemIds)
        }
    }

    suspend fun addRoom(reportId: String, roomName: String) {
        val localId = "local_${UUID.randomUUID().toString().replace("-", "")}"
        val entity = RoomEntity(
            id = localId, serverId = null, reportId = reportId,
            name = roomName, displayOrder = null,
            syncStatus = SyncStatus.PENDING_CREATE
        )
        db.roomDao().upsert(entity)
        db.reportDao().incrementRoomCount(reportId, 1)
        db.syncQueueDao().enqueue(
            SyncQueueEntity(
                entityType = "ROOM", operationType = "CREATE",
                localEntityId = localId, payload = gson.toJson(AddNewRoomsRequest(listOf(roomName)))
            )
        )
    }

    suspend fun updateRoomName(localId: String, newName: String) {
        val existing = db.roomDao().getById(localId) ?: return
        db.roomDao().updateName(localId, newName, 
            if (existing.syncStatus == SyncStatus.SYNCED) SyncStatus.PENDING_UPDATE else existing.syncStatus)
        
        if (existing.syncStatus == SyncStatus.SYNCED) {
            db.syncQueueDao().enqueue(
                SyncQueueEntity(
                    entityType = "ROOM", operationType = "UPDATE",
                    localEntityId = localId, payload = gson.toJson(UpdateRoomNameRequest(newName))
                )
            )
        }
    }

    suspend fun deleteRoom(localId: String) {
        val existing = db.roomDao().getById(localId) ?: return
        if (existing.syncStatus == SyncStatus.PENDING_CREATE) {
            db.roomDao().deleteById(localId)
        } else {
            db.roomDao().updateSyncStatus(localId, SyncStatus.PENDING_DELETE)
            db.syncQueueDao().enqueue(
                SyncQueueEntity(
                    entityType = "ROOM", operationType = "DELETE",
                    localEntityId = localId, payload = "{}"
                )
            )
        }
    }

    suspend fun reorderRoom(localId: String, prevRank: String?, nextRank: String?) {
        val existing = db.roomDao().getById(localId) ?: return

        if (existing.syncStatus == SyncStatus.SYNCED) {
            db.syncQueueDao().enqueue(
                SyncQueueEntity(
                    entityType = "ROOM", operationType = "REORDER",
                    localEntityId = localId,
                    payload = gson.toJson(ReorderRoomRequest(prev_rank = prevRank, next_rank = nextRank))
                )
            )
        }
    }

    private fun RoomEntity.toResponse() = RoomsResponse(
        id = id,
        templateId = templateId,
        name = name,
        displayOrder = displayOrder,
        isSelected = true,
        items = ArrayList(),
        inspection = ArrayList(),
        attachments = ArrayList()
    )
}
