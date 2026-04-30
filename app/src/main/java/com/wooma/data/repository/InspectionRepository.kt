package com.wooma.data.repository

import android.content.Context
import com.google.gson.Gson
import com.wooma.data.local.WoomaDatabase
import com.wooma.data.local.entity.RoomInspectionEntity
import com.wooma.data.local.entity.SyncQueueEntity
import com.wooma.data.local.entity.SyncStatus
import com.wooma.data.network.RetrofitClient
import com.wooma.model.UpsertRoomInspectionRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class InspectionRepository(private val ctx: Context) {

    private val db = WoomaDatabase.getInstance(ctx)
    private val api by lazy { RetrofitClient.getApi(ctx) }
    private val gson = Gson()

    fun observeInspections(roomId: String): Flow<List<RoomInspectionEntity>> =
        db.roomInspectionDao().observeByRoom(roomId)

    suspend fun upsertInspection(reportId: String, roomId: String, request: UpsertRoomInspectionRequest) = withContext(Dispatchers.IO) {
        val existing = db.roomInspectionDao().getByRoom(roomId).firstOrNull()
        
        val localId = existing?.id ?: "local_${UUID.randomUUID().toString().replace("-", "")}"
        
        val entity = RoomInspectionEntity(
            id = localId,
            serverId = existing?.serverId,
            roomId = roomId,
            isIssue = request.is_issue,
            note = request.note,
            priority = request.priority,
            syncStatus = if (existing?.serverId == null) SyncStatus.PENDING_CREATE else SyncStatus.PENDING_UPDATE
        )
        
        db.roomInspectionDao().upsert(entity)
        
        db.syncQueueDao().enqueue(
            SyncQueueEntity(
                entityType = "ROOM_INSPECTION",
                operationType = "UPSERT",
                localEntityId = localId,
                payload = gson.toJson(request)
            )
        )
    }
}
