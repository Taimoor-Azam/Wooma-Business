package com.wooma.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import androidx.room.withTransaction
import com.wooma.data.local.WoomaDatabase
import com.wooma.data.local.entity.RoomEntity
import com.wooma.data.local.entity.SyncStatus
import com.wooma.data.network.RetrofitClient
import com.wooma.data.repository.RoomItemRepository
import com.wooma.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    private val db = WoomaDatabase.getInstance(ctx)
    private val api = RetrofitClient.getApi(ctx)
    private val gson = Gson()

    override suspend fun doWork(): Result = runSync()

    private suspend fun runSync(): Result {
        var rounds = 0
        while (rounds++ < 25) {
            db.syncQueueDao().resetInProgressToPending()
            db.syncQueueDao().pruneDuplicatePendingChecklistStatus()
            val pending = db.syncQueueDao().getPendingInOrder()
            if (pending.isEmpty()) break

            var progressed = false
            for (entry in pending) {
                // Snapshot can include rows already completed by an earlier entry in this same pass
                // (e.g. ROOM CREATE bulk marks sibling room creates DONE). Skip stale items.
                val liveEntry = db.syncQueueDao().getById(entry.id) ?: continue
                if (liveEntry.status != "PENDING") continue

                // Check parent dependency — skip until parent is DONE
                if (liveEntry.parentSyncId != null) {
                    val parent = db.syncQueueDao().getById(liveEntry.parentSyncId)
                    if (parent?.status != "DONE") continue
                }

                db.syncQueueDao().updateStatus(liveEntry.id, "IN_PROGRESS", null)
                try {
                    processEntry(liveEntry)
                    db.syncQueueDao().updateStatus(liveEntry.id, "DONE", null)
                    progressed = true
                } catch (e: CancellationException) {
                    db.syncQueueDao().updateStatus(liveEntry.id, "PENDING", null)
                    Log.d("SyncWorker", "Sync cancelled for ${liveEntry.entityType}/${liveEntry.operationType}")
                    throw e
                } catch (e: Exception) {
                    Log.e("SyncWorker", "Failed to sync ${liveEntry.entityType}/${liveEntry.operationType}: ${e.message}")
                    if (liveEntry.retryCount >= 4) {
                        db.syncQueueDao().updateStatus(liveEntry.id, "FAILED", e.message)
                    } else {
                        db.syncQueueDao().requeueForRetry(liveEntry.id)
                    }
                    progressed = true
                }
            }
            db.syncQueueDao().purgeDone()
            if (!progressed) break
        }
        SyncScheduler.schedulePendingUploads(applicationContext)
        return Result.success()
    }

    /**
     * REST paths use the server report UUID. Prefer [com.wooma.data.local.entity.ReportEntity.serverId];
     * if empty (legacy row), fall back to a non-local primary id.
     */
    private suspend fun reportApiId(reportId: String): String {
        val r = db.reportDao().getById(reportId)
            ?: throw Exception("Report not found: $reportId")
        val sid = r.serverId?.trim()?.takeIf { it.isNotEmpty() }
        val pk = r.id.trim()
        return when {
            sid != null -> sid
            pk.isNotEmpty() && !pk.startsWith("local_") -> pk
            else -> throw Exception("Report serverId not yet available for report $reportId")
        }
    }

    private suspend fun reportApiIdOrNull(reportId: String): String? = try {
        reportApiId(reportId)
    } catch (_: Exception) {
        null
    }

    /** REST paths use the server room UUID. Prefer [RoomEntity.serverId]; else non-local primary key. */
    private fun roomServerIdForApi(room: RoomEntity): String? {
        val sid = room.serverId?.trim()?.takeIf { it.isNotEmpty() }
        if (sid != null) return sid
        val pk = room.id.trim()
        return if (pk.isNotEmpty() && !pk.startsWith("local_")) pk else null
    }

    private fun roomServerIdForApiOrThrow(room: RoomEntity): String =
        roomServerIdForApi(room) ?: throw Exception("Room serverId not yet available for room ${room.id}")

    /** True when this room still needs a bulk CREATE on the server (local temp id, no server row yet). */
    private fun roomNeedsBulkCreate(e: RoomEntity): Boolean {
        if (e.syncStatus == SyncStatus.PENDING_DELETE) return false
        if (!e.serverId.isNullOrBlank()) return false
        val pk = e.id.trim()
        return pk.isEmpty() || pk.startsWith("local_")
    }

    private suspend fun processEntry(entry: com.wooma.data.local.entity.SyncQueueEntity) {
        when (entry.entityType) {
            "METER"    -> processMeter(entry)
            "KEY"      -> processKey(entry)
            "DETECTOR" -> processDetector(entry)
            "ROOM"     -> processRoom(entry)
            "ROOM_ITEM" -> processRoomItem(entry)
            "ROOM_INSPECTION" -> processRoomInspection(entry)
            "CHECKLIST_QUESTION" -> processChecklistQuestion(entry)
            "CHECKLIST_INFO_FIELD" -> processChecklistInfoField(entry)
            "CHECKLIST_STATUS" -> processChecklistStatus(entry)
            "CHECKLIST_ANSWER_ATTACHMENT" -> processChecklistAnswerAttachment(entry)
            "ATTACHMENT"   -> processAttachment(entry)
            "REPORT_COVER" -> processReportCover(entry)
            "PROPERTY" -> processProperty(entry)
            "REPORT"          -> processReport(entry)
            "REPORT_ASSESSOR" -> processReportAssessor(entry)
            "REPORT_DATE" -> processReportDate(entry)
            "REPORT_TYPE" -> processReportType(entry)
        }
    }

    // ─── METER ───────────────────────────────────────────────────────────────

    private suspend fun processMeter(entry: com.wooma.data.local.entity.SyncQueueEntity) =
        withContext(Dispatchers.IO) {
            val localId = entry.localEntityId
            when (entry.operationType) {
                "CREATE" -> {
                    val req = gson.fromJson(entry.payload, AddMeterRequest::class.java)
                    val meterEntity = db.meterDao().getById(localId)
                        ?: return@withContext // Entity was deleted before sync; discard
                    if (meterEntity.isDeleted) {
                        db.meterDao().deleteById(localId)
                        return@withContext
                    }
                    val reportServerId = reportApiId(meterEntity.reportId)
                        ?: throw Exception("Report serverId not yet available for meter $localId")

                    val resp = api.addNewMeter(reportServerId, req).execute()
                    if (!resp.isSuccessful) throw Exception("Meter CREATE failed: ${resp.code()}")

                    // Re-fetch to find the newly assigned server ID
                    val metersResp = api.getReportMeters(reportServerId, include_attachments = false).execute()
                    val serverId = metersResp.body()?.data?.asReversed()
                        ?.firstOrNull { it.name == req.name && (it.reading ?: "") == (req.reading) }?.id
                        ?: throw Exception("Created meter not found in server list")

                    db.meterDao().promoteLocalId(localId, serverId)
                    db.syncQueueDao().updateServerEntityId(localId, "METER", serverId)
                    db.pendingUploadDao().updateEntityServerId(localId, "METER", serverId)
                    db.attachmentDao().reattachToNewEntityId(localId, serverId)
                }
                "UPDATE" -> {
                    val req = gson.fromJson(entry.payload, AddMeterRequest::class.java)
                    val meterEntity = db.meterDao().getById(localId)
                        ?: throw Exception("Meter $localId not found")
                    val serverId = meterEntity.serverId
                        ?: throw Exception("No serverId for METER UPDATE $localId")
                    val reportServerId = reportApiId(meterEntity.reportId)
                        ?: throw Exception("Report serverId not found for meter $localId")

                    val resp = api.updateMeter(reportServerId, serverId, req).execute()
                    if (!resp.isSuccessful) throw Exception("Meter UPDATE failed: ${resp.code()}")
                    db.meterDao().updateSyncStatus(localId, SyncStatus.SYNCED)
                }
                "DELETE" -> {
                    val meterEntity = db.meterDao().getById(localId) ?: return@withContext
                    val serverId = meterEntity.serverId
                    if (serverId == null) {
                        db.meterDao().deleteById(localId)
                        return@withContext
                    }
                    val reportServerId = reportApiId(meterEntity.reportId)
                        ?: throw Exception("Report serverId not found for meter $localId")

                    val resp = api.deleteMeter(reportServerId, serverId).execute()
                    if (!resp.isSuccessful && resp.code() != 404) {
                        throw Exception("Meter DELETE failed: ${resp.code()}")
                    }
                    db.meterDao().deleteById(localId)
                }
            }
        }

    // ─── KEY ─────────────────────────────────────────────────────────────────

    private suspend fun processKey(entry: com.wooma.data.local.entity.SyncQueueEntity) =
        withContext(Dispatchers.IO) {
            val localId = entry.localEntityId
            when (entry.operationType) {
                "CREATE" -> {
                    val req = gson.fromJson(entry.payload, AddKeyRequest::class.java)
                    val keyEntity = db.keyDao().getById(localId)
                        ?: return@withContext
                    if (keyEntity.isDeleted) {
                        db.keyDao().deleteById(localId)
                        return@withContext
                    }
                    val reportServerId = reportApiId(keyEntity.reportId)
                        ?: throw Exception("Report serverId not yet available for key $localId")

                    val resp = api.addNewKey(reportServerId, req).execute()
                    if (!resp.isSuccessful) throw Exception("Key CREATE failed: ${resp.code()}")

                    val keysResp = api.getReportKeys(reportServerId, include_attachments = false).execute()
                    val serverId = keysResp.body()?.data?.asReversed()
                        ?.firstOrNull { it.name == req.name }?.id
                        ?: throw Exception("Created key not found in server list")

                    db.keyDao().promoteLocalId(localId, serverId)
                    db.syncQueueDao().updateServerEntityId(localId, "KEY", serverId)
                    db.pendingUploadDao().updateEntityServerId(localId, "KEY", serverId)
                    db.attachmentDao().reattachToNewEntityId(localId, serverId)
                }
                "UPDATE" -> {
                    val req = gson.fromJson(entry.payload, AddKeyRequest::class.java)
                    val keyEntity = db.keyDao().getById(localId)
                        ?: throw Exception("Key $localId not found")
                    val serverId = keyEntity.serverId
                        ?: throw Exception("No serverId for KEY UPDATE $localId")
                    val reportServerId = reportApiId(keyEntity.reportId)
                        ?: throw Exception("Report serverId not found for key $localId")

                    val resp = api.updateKey(reportServerId, serverId, req).execute()
                    if (!resp.isSuccessful) throw Exception("Key UPDATE failed: ${resp.code()}")
                    db.keyDao().updateSyncStatus(localId, SyncStatus.SYNCED)
                }
                "DELETE" -> {
                    val keyEntity = db.keyDao().getById(localId) ?: return@withContext
                    val serverId = keyEntity.serverId
                    if (serverId == null) {
                        db.keyDao().deleteById(localId)
                        return@withContext
                    }
                    val reportServerId = reportApiId(keyEntity.reportId)
                        ?: throw Exception("Report serverId not found for key $localId")

                    val resp = api.deleteKey(reportServerId, serverId).execute()
                    if (!resp.isSuccessful && resp.code() != 404) {
                        throw Exception("Key DELETE failed: ${resp.code()}")
                    }
                    db.keyDao().deleteById(localId)
                }
            }
        }

    // ─── DETECTOR ────────────────────────────────────────────────────────────

    private suspend fun processDetector(entry: com.wooma.data.local.entity.SyncQueueEntity) =
        withContext(Dispatchers.IO) {
            val localId = entry.localEntityId
            when (entry.operationType) {
                "CREATE" -> {
                    val req = gson.fromJson(entry.payload, AddDetectorRequest::class.java)
                    val detEntity = db.detectorDao().getById(localId)
                        ?: return@withContext
                    if (detEntity.isDeleted) {
                        db.detectorDao().deleteById(localId)
                        return@withContext
                    }
                    val reportServerId = reportApiId(detEntity.reportId)
                        ?: throw Exception("Report serverId not yet available for detector $localId")

                    val resp = api.addNewDetector(reportServerId, req).execute()
                    if (!resp.isSuccessful) throw Exception("Detector CREATE failed: ${resp.code()}")

                    val detsResp = api.getReportDetector(reportServerId, include_attachments = false).execute()
                    val serverId = detsResp.body()?.data?.asReversed()
                        ?.firstOrNull { it.name == req.name }?.id
                        ?: throw Exception("Created detector not found in server list")

                    db.detectorDao().promoteLocalId(localId, serverId)
                    db.syncQueueDao().updateServerEntityId(localId, "DETECTOR", serverId)
                    db.pendingUploadDao().updateEntityServerId(localId, "DETECTOR", serverId)
                    db.attachmentDao().reattachToNewEntityId(localId, serverId)
                }
                "UPDATE" -> {
                    val req = gson.fromJson(entry.payload, AddDetectorRequest::class.java)
                    val detEntity = db.detectorDao().getById(localId)
                        ?: throw Exception("Detector $localId not found")
                    val serverId = detEntity.serverId
                        ?: throw Exception("No serverId for DETECTOR UPDATE $localId")
                    val reportServerId = reportApiId(detEntity.reportId)
                        ?: throw Exception("Report serverId not found for detector $localId")

                    val resp = api.updateDetector(reportServerId, serverId, req).execute()
                    if (!resp.isSuccessful) throw Exception("Detector UPDATE failed: ${resp.code()}")
                    db.detectorDao().updateSyncStatus(localId, SyncStatus.SYNCED)
                }
                "DELETE" -> {
                    val detEntity = db.detectorDao().getById(localId) ?: return@withContext
                    val serverId = detEntity.serverId
                    if (serverId == null) {
                        db.detectorDao().deleteById(localId)
                        return@withContext
                    }
                    val reportServerId = reportApiId(detEntity.reportId)
                        ?: throw Exception("Report serverId not found for detector $localId")

                    val resp = api.deleteDetector(reportServerId, serverId).execute()
                    if (!resp.isSuccessful && resp.code() != 404) {
                        throw Exception("Detector DELETE failed: ${resp.code()}")
                    }
                    db.detectorDao().deleteById(localId)
                }
            }
        }

    // ─── ROOM ────────────────────────────────────────────────────────────────

    /** One bulk POST covers every pending ROOM CREATE for this report — complete those rows so we don't retry POST. */
    private suspend fun markAllPendingRoomCreatesDone(reportId: String) {
        val pending = db.syncQueueDao().getPendingRoomCreatesByReport(reportId)
        for (q in pending) {
            db.syncQueueDao().updateStatus(q.id, "DONE", null)
        }
    }

    private suspend fun processRoom(entry: com.wooma.data.local.entity.SyncQueueEntity) =
        withContext(Dispatchers.IO) {
            val localId = entry.localEntityId
            when (entry.operationType) {
                "CREATE" -> {
                    val roomEntity = db.roomDao().getById(localId)
                        ?: throw Exception("Room $localId not found locally")
                    if (!roomNeedsBulkCreate(roomEntity)) {
                        if (roomEntity.serverId.isNullOrBlank() &&
                            roomEntity.id.trim().isNotEmpty() &&
                            !roomEntity.id.startsWith("local_")
                        ) {
                            db.roomDao().promoteLocalId(roomEntity.id, roomEntity.id.trim())
                        }
                        db.roomDao().updateSyncStatus(localId, SyncStatus.SYNCED)
                        return@withContext
                    }
                    val reportServerId = reportApiId(roomEntity.reportId)
                    val pendingCreates = db.syncQueueDao().getPendingRoomCreatesByReport(roomEntity.reportId)
                    val queueEntriesForBatch = (listOf(entry) + pendingCreates).distinctBy { it.id }
                    val batchCandidates = queueEntriesForBatch.mapNotNull { q ->
                        val e = db.roomDao().getById(q.localEntityId) ?: return@mapNotNull null
                        if (e.syncStatus == SyncStatus.PENDING_DELETE || !roomNeedsBulkCreate(e)) {
                            return@mapNotNull null
                        }
                        val payloadName = runCatching {
                            gson.fromJson(q.payload, AddNewRoomsRequest::class.java)
                                .rooms.firstOrNull()?.trim().orEmpty()
                        }.getOrDefault("")
                        val name = (payloadName.ifEmpty { e.name }).trim()
                        if (name.isEmpty()) return@mapNotNull null
                        e.id to name
                    }.distinctBy { it.first }
                    if (batchCandidates.isEmpty()) return@withContext

                    // Only use name-based pre-bind on retries to avoid duplicate reposts.
                    // For first attempt, send all pending rooms in bulk as user explicitly created them.
                    if (entry.retryCount > 0) {
                        val preMapped = bindRoomsFromServerByNames(
                            reportId = roomEntity.reportId,
                            reportServerId = reportServerId,
                            localRooms = batchCandidates
                        )
                        if (preMapped == batchCandidates.size) {
                            markAllPendingRoomCreatesDone(roomEntity.reportId)
                            return@withContext
                        }
                    }

                    val unresolvedBeforePost = batchCandidates.filter { (localRoomId, _) ->
                        val r = db.roomDao().getById(localRoomId) ?: return@filter false
                        roomNeedsBulkCreate(r)
                    }
                    val namesToCreate = unresolvedBeforePost.map { it.second }
                    if (namesToCreate.isNotEmpty()) {
                        val resp = api.addRomToReport(reportServerId, AddNewRoomsRequest(namesToCreate)).execute()
                        if (!resp.isSuccessful) throw Exception("Room CREATE failed: ${resp.code()}")
                    }

                    // Fetch a few times to survive eventual consistency and avoid duplicate POST on retry.
                    var mappedCount = 0
                    repeat(3) { attempt ->
                        mappedCount = bindRoomsFromServerByNames(
                            reportId = roomEntity.reportId,
                            reportServerId = reportServerId,
                            localRooms = batchCandidates
                        )
                        if (mappedCount == batchCandidates.size) return@repeat
                        if (attempt < 2) delay(350)
                    }
                    if (mappedCount != batchCandidates.size) {
                        throw Exception("Created room(s) not found in server list")
                    }
                    markAllPendingRoomCreatesDone(roomEntity.reportId)
                }
                "UPDATE" -> {
                    val req = gson.fromJson(entry.payload, UpdateRoomNameRequest::class.java)
                    val roomEntity = db.roomDao().getById(localId)
                        ?: throw Exception("Room $localId not found")
                    val serverId = roomServerIdForApiOrThrow(roomEntity)
                    val reportServerId = reportApiId(roomEntity.reportId)

                    val resp = api.updateRoomName(reportServerId, serverId, req).execute()
                    if (!resp.isSuccessful) throw Exception("Room UPDATE failed: ${resp.code()}")
                    db.roomDao().updateSyncStatus(localId, SyncStatus.SYNCED)
                }
                "DELETE" -> {
                    val roomEntity = db.roomDao().getById(localId) ?: return@withContext
                    val serverId = roomServerIdForApi(roomEntity) ?: run {
                        db.roomDao().deleteById(localId)
                        return@withContext
                    }
                    val reportServerId = reportApiId(roomEntity.reportId)

                    val resp = api.deleteRoom(reportServerId, serverId).execute()
                    if (!resp.isSuccessful && resp.code() != 404) {
                        throw Exception("Room DELETE failed: ${resp.code()}")
                    }
                    db.roomDao().deleteById(localId)
                }
                "REORDER" -> {
                    val req = gson.fromJson(entry.payload, ReorderRoomRequest::class.java)
                    val roomEntity = db.roomDao().getById(localId)
                        ?: throw Exception("Room $localId not found")
                    val serverId = roomServerIdForApiOrThrow(roomEntity)
                    val reportServerId = reportApiId(roomEntity.reportId)

                    val resp = api.reorderRoom(reportServerId, serverId, req).execute()
                    if (!resp.isSuccessful) throw Exception("Room REORDER failed: ${resp.code()}")
                    db.roomDao().updateSyncStatus(localId, SyncStatus.SYNCED)

                    // Re-fetch all rooms to get server-assigned displayOrder values
                    val roomsResp = api.getInspectionRoomById(
                        report_id = reportServerId,
                        include_items = false,
                        include_room_inspections = false
                    ).execute()
                    val pendingIds = db.roomDao().getPendingSyncRooms().map { it.id }.toSet()
                    roomsResp.body()?.data?.forEach { room ->
                        val rId = room.id ?: return@forEach
                        if (rId !in pendingIds) {
                            db.roomDao().updateFromServer(rId, room.name ?: "", room.templateId, room.displayOrder)
                        }
                    }
                }
            }
        }

    private suspend fun bindRoomsFromServerByNames(
        reportId: String,
        reportServerId: String,
        localRooms: List<Pair<String, String>>
    ): Int {
        val roomsResp = api.getInspectionRoomById(
            report_id = reportServerId,
            include_items = false,
            include_room_inspections = false
        ).execute()
        if (!roomsResp.isSuccessful) {
            throw Exception("Room list REFRESH failed: ${roomsResp.code()}")
        }

        val mappedServerIds = db.roomDao().getByReport(reportId)
            .mapNotNull { row ->
                row.serverId ?: row.id.takeIf { !it.startsWith("local_") }
            }
            .toSet()

        val availableByName = HashMap<String, ArrayDeque<String>>()
        roomsResp.body()?.data?.forEach { room ->
            val serverId = room.id ?: return@forEach
            val name = (room.name ?: "").trim()
            if (name.isEmpty() || serverId in mappedServerIds) return@forEach
            val queue = availableByName.getOrPut(name) { ArrayDeque() }
            queue.addLast(serverId)
        }

        var mappedCount = 0
        for ((localId, name) in localRooms) {
            val local = db.roomDao().getById(localId) ?: continue
            if (roomServerIdForApi(local) != null) {
                mappedCount++
                continue
            }
            val serverId = availableByName[name]?.removeFirstOrNull() ?: continue
            db.roomDao().promoteLocalId(localId, serverId)
            db.syncQueueDao().updateServerEntityId(localId, "ROOM", serverId)
            db.pendingUploadDao().updateEntityServerId(localId, "ROOM", serverId)
            db.attachmentDao().reattachToNewEntityId(localId, serverId)
            mappedCount++
        }
        return mappedCount
    }

    /**
     * Persists server PK + queue/pending migrations for one room-item CREATE after we know [serverId].
     * [itemLocalPk] is the current [RoomItemEntity.id] (local temp id) before rekey.
     */
    private suspend fun applyRoomItemCreateServerBinding(queueId: Long, itemLocalPk: String, serverId: String) {
        db.withTransaction {
            db.roomItemDao().deleteRoomItemPrimaryKeyCollisionForRekey(serverId, itemLocalPk)
            db.roomItemDao().rekeyRoomItemPrimaryKey(itemLocalPk, serverId)
        }
        db.attachmentDao().reattachToNewEntityId(itemLocalPk, serverId)
        db.syncQueueDao().updateServerEntityId(itemLocalPk, "ROOM_ITEM", serverId)
        db.pendingUploadDao().updateEntityServerId(itemLocalPk, "ROOM_ITEM", serverId)
        db.syncQueueDao().migrateRoomItemLocalEntityId(itemLocalPk, serverId)
        db.pendingUploadDao().migrateRoomItemEntityLocalId(itemLocalPk, serverId)
        db.syncQueueDao().updateStatus(queueId, "DONE", null)
    }

    /**
     * If the bulk POST already succeeded on a prior attempt but local finalize failed, match by name
     * and finish without calling [addRoomItemsToReport] again.
     */
    private suspend fun tryFinalizeRoomItemCreatesFromServerWithoutPost(
        reportServerId: String,
        roomServerId: String,
        pairs: List<Triple<Long, String, String>>,
    ): Boolean {
        val itemsResp = api.getRoomById(
            roomServerId,
            reportServerId,
            include_items = true,
            include_room_inspections = false
        ).execute()
        if (!itemsResp.isSuccessful) return false
        val serverItems = itemsResp.body()?.data?.items
        if (serverItems.isNullOrEmpty()) return false

        val usedServerIds = mutableSetOf<String>()
        val toBind = ArrayList<Triple<Long, String, String>>()

        for ((queueId, itemLocalId, name) in pairs) {
            val ent = db.roomItemDao().getById(itemLocalId)
                ?: db.roomItemDao().getByLocalOrServerId(itemLocalId)
                ?: return false
            if (ent.syncStatus == SyncStatus.SYNCED && ent.serverId != null) {
                db.syncQueueDao().updateStatus(queueId, "DONE", null)
                continue
            }
            if (ent.syncStatus != SyncStatus.PENDING_CREATE || ent.isDeleted) return false
            val pk = ent.id
            val serverId = serverItems.asReversed()
                .firstOrNull { item ->
                    val sid = item.id?.trim().orEmpty()
                    sid.isNotEmpty() && sid !in usedServerIds && item.name == name
                }?.id?.trim().orEmpty()
            if (serverId.isEmpty()) return false
            usedServerIds.add(serverId)
            toBind.add(Triple(queueId, pk, serverId))
        }

        for ((queueId, pk, serverId) in toBind) {
            applyRoomItemCreateServerBinding(queueId, pk, serverId)
        }
        return true
    }

    private suspend fun processRoomItem(entry: com.wooma.data.local.entity.SyncQueueEntity) =
        withContext(Dispatchers.IO) {
            val localId = entry.localEntityId
            when (entry.operationType) {
                "CREATE" -> {
                    val anchorEntity = db.roomItemDao().getById(localId)
                        ?: db.roomItemDao().getByLocalOrServerId(localId)
                    if (anchorEntity == null) {
                        return@withContext
                    }
                    if (anchorEntity.syncStatus == SyncStatus.SYNCED && anchorEntity.serverId != null) {
                        return@withContext
                    }
                    if (anchorEntity.isDeleted) {
                        db.roomItemDao().deleteById(anchorEntity.id)
                        return@withContext
                    }

                    val roomLocalId = anchorEntity.roomId
                    val batchEntries =
                        db.syncQueueDao().getPendingRoomItemCreatesForRoom(roomLocalId, entry.id)
                    val pairs = batchEntries.mapNotNull { q ->
                        val ent = db.roomItemDao().getById(q.localEntityId)
                            ?: db.roomItemDao().getByLocalOrServerId(q.localEntityId)
                            ?: return@mapNotNull null
                        if (ent.syncStatus != SyncStatus.PENDING_CREATE || ent.isDeleted) return@mapNotNull null
                        val req = gson.fromJson(q.payload, AddNewRoomItemsRequest::class.java)
                        val name = req.room_items.singleOrNull() ?: return@mapNotNull null
                        Triple(q.id, ent.id, name)
                    }
                    if (pairs.isEmpty()) return@withContext

                    val roomRowForBatch = db.roomDao().getById(roomLocalId)
                        ?: throw Exception("Room not found for room item batch: $roomLocalId")
                    val roomServerId = roomServerIdForApiOrThrow(roomRowForBatch)
                    val reportServerId = reportApiId(roomRowForBatch.reportId)

                    if (batchEntries.any { it.retryCount > 0 } &&
                        tryFinalizeRoomItemCreatesFromServerWithoutPost(reportServerId, roomServerId, pairs)
                    ) {
                        return@withContext
                    }

                    val mergedNames = pairs.map { it.third }
                    val mergedReq = AddNewRoomItemsRequest(room_items = mergedNames)
                    var bulkPosted = false
                    try {
                        val resp = api.addRoomItemsToReport(reportServerId, roomServerId, mergedReq).execute()
                        if (!resp.isSuccessful) throw Exception("Item CREATE (bulk) failed: ${resp.code()}")
                        bulkPosted = true

                        val itemsResp = api.getRoomById(
                            roomServerId,
                            reportServerId,
                            include_items = true,
                            include_room_inspections = false
                        ).execute()
                        if (!itemsResp.isSuccessful) {
                            throw Exception("Refetch after bulk create failed: ${itemsResp.code()}")
                        }
                        val serverItems = itemsResp.body()?.data?.items
                            ?: throw Exception("No items after bulk create")
                        val usedServerIds = mutableSetOf<String>()
                        for ((queueId, itemLocalPk, name) in pairs) {
                            val serverId = serverItems.asReversed()
                                .firstOrNull { item ->
                                    val sid = item.id?.trim().orEmpty()
                                    sid.isNotEmpty() && sid !in usedServerIds && item.name == name
                                }?.id?.trim().orEmpty()
                            if (serverId.isEmpty()) {
                                throw Exception("Created item not found on server: $name")
                            }
                            usedServerIds.add(serverId)
                            applyRoomItemCreateServerBinding(queueId, itemLocalPk, serverId)
                        }
                    } catch (e: Exception) {
                        if (bulkPosted) {
                            if (tryFinalizeRoomItemCreatesFromServerWithoutPost(
                                    reportServerId,
                                    roomServerId,
                                    pairs,
                                )
                            ) {
                                return@withContext
                            }
                            for ((queueId, _, _) in pairs) {
                                db.syncQueueDao().bumpRetryResetPendingIfNotDone(queueId)
                            }
                        }
                        throw e
                    }
                }
                "UPDATE" -> {
                    val req = gson.fromJson(entry.payload, UpdateRoomItemRequest::class.java)
                    val roomItemRepo = RoomItemRepository(applicationContext)
                    var itemEntity = db.roomItemDao().getById(localId)
                        ?: throw Exception("ROOM_ITEM UPDATE: item not found $localId")
                    if (itemEntity.serverId == null) {
                        roomItemRepo.resolveRoomItemServerIdIfMissing(localId)
                            ?: throw Exception("ROOM_ITEM UPDATE: cannot resolve server id for ${itemEntity.id}")
                        itemEntity = db.roomItemDao().getById(localId)
                            ?: throw Exception("ROOM_ITEM UPDATE: item missing after resolve $localId")
                    }
                    val serverId = itemEntity.serverId
                        ?: throw Exception("ROOM_ITEM UPDATE: serverId still null $localId")
                    val roomRow = db.roomDao().getById(itemEntity.roomId)
                        ?: throw Exception("ROOM_ITEM UPDATE: room not found")
                    val roomServerId = roomServerIdForApiOrThrow(roomRow)
                    val reportServerId = reportApiId(roomRow.reportId)

                    val resp = api.updateRoomItem(reportServerId, roomServerId, serverId, req).execute()
                    if (!resp.isSuccessful) throw Exception("Item UPDATE failed: ${resp.code()}")
                    db.roomItemDao().updateSyncStatus(localId, SyncStatus.SYNCED)
                }
                "DELETE" -> {
                    var itemEntity = db.roomItemDao().getById(localId) ?: return@withContext
                    if (itemEntity.serverId == null) {
                        RoomItemRepository(applicationContext).resolveRoomItemServerIdIfMissing(localId)
                        itemEntity = db.roomItemDao().getById(localId) ?: return@withContext
                    }
                    val serverId = itemEntity.serverId
                    if (serverId == null) {
                        db.roomItemDao().deleteById(localId)
                        return@withContext
                    }
                    val roomRowDel = db.roomDao().getById(itemEntity.roomId) ?: return@withContext
                    val roomServerId = roomServerIdForApi(roomRowDel) ?: return@withContext
                    val reportServerId = reportApiIdOrNull(roomRowDel.reportId) ?: return@withContext

                    val resp = api.deleteRoomItem(reportServerId, roomServerId, serverId).execute()
                    if (!resp.isSuccessful && resp.code() != 404) throw Exception("Item DELETE failed")
                    db.roomItemDao().deleteById(localId)
                }
            }
        }

    private suspend fun processRoomInspection(entry: com.wooma.data.local.entity.SyncQueueEntity) =
        withContext(Dispatchers.IO) {
            val localId = entry.localEntityId
            val req = gson.fromJson(entry.payload, UpsertRoomInspectionRequest::class.java)
            val inspection = db.roomInspectionDao().getById(localId) ?: return@withContext
            val roomRowInsp = db.roomDao().getById(inspection.roomId) ?: return@withContext
            val roomServerId = roomServerIdForApi(roomRowInsp) ?: return@withContext

            val updatedReq = req.copy(room_id = roomServerId)
            val resp = api.upsertRoomInspection(updatedReq).execute()
            if (!resp.isSuccessful) throw Exception("Inspection UPSERT failed")
            
            // Refetch to get server ID if it was CREATE
            if (inspection.serverId == null) {
                val reportServerId = reportApiIdOrNull(roomRowInsp.reportId) ?: return@withContext
                val roomsResp = api.getRoomById(roomServerId, reportServerId, false, true).execute()
                val serverId = roomsResp.body()?.data?.inspection?.firstOrNull()?.id
                if (serverId != null) {
                    db.roomInspectionDao().promoteLocalId(localId, serverId)
                    db.syncQueueDao().updateServerEntityId(localId, "ROOM_INSPECTION", serverId)
                    db.pendingUploadDao().updateEntityServerId(localId, "ROOM_INSPECTION", serverId)
                    db.attachmentDao().reattachToNewEntityId(localId, serverId)
                }
            } else {
                db.roomInspectionDao().updateSyncStatus(localId, SyncStatus.SYNCED)
            }
        }

    private suspend fun processAttachment(entry: com.wooma.data.local.entity.SyncQueueEntity) =
        withContext(Dispatchers.IO) {
            when (entry.operationType) {
                "DELETE" -> {
                    val resp = api.deleteAttachment(entry.localEntityId).execute()
                    if (!resp.isSuccessful && resp.code() != 404) {
                        throw Exception("Attachment DELETE failed: ${resp.code()}")
                    }
                }
            }
        }

    private suspend fun processChecklistQuestion(entry: com.wooma.data.local.entity.SyncQueueEntity) =
        withContext(Dispatchers.IO) {
            val req = gson.fromJson(entry.payload, UpsertQuestionAnswerRequest::class.java)
            val resp = api.upsertQuestionAnswer(req).execute()
            if (!resp.isSuccessful) throw Exception("Question UPSERT failed")
        }

    private suspend fun processChecklistInfoField(entry: com.wooma.data.local.entity.SyncQueueEntity) =
        withContext(Dispatchers.IO) {
            val req = gson.fromJson(entry.payload, UpsertFieldAnswerRequest::class.java)
            val resp = api.upsertFieldAnswer(req).execute()
            if (!resp.isSuccessful) throw Exception("Field UPSERT failed")
        }

    private suspend fun processChecklistStatus(entry: com.wooma.data.local.entity.SyncQueueEntity) =
        withContext(Dispatchers.IO) {
            val req = gson.fromJson(entry.payload, ChecklistStatusRequest::class.java)
            val resp = api.updateChecklistStatus(entry.localEntityId, req).execute()
            if (!resp.isSuccessful) throw Exception("Status UPDATE failed")
            db.checklistDao().updateSyncStatus(entry.localEntityId, SyncStatus.SYNCED)
        }

    private suspend fun processChecklistAnswerAttachment(entry: com.wooma.data.local.entity.SyncQueueEntity) =
        withContext(Dispatchers.IO) {
            val localId = entry.localEntityId
            val req = gson.fromJson(entry.payload, ChecklistAnswerAttachmentRequest::class.java)
            val resp = api.findOrCreateAnswerAttachment(req).execute()
            if (!resp.isSuccessful) throw Exception("ChecklistAnswerAttachment CREATE failed: ${resp.code()}")
            val serverId = resp.body()?.data?.id
                ?: throw Exception("ChecklistAnswerAttachment returned null id")
            db.pendingUploadDao().updateEntityServerId(localId, "CHECKLIST_ANSWER_ATTACHMENT", serverId)
            db.pendingUploadDao().migrateChecklistAnswerAttachmentEntityLocalId(localId, serverId)
            db.attachmentDao().promoteChecklistAnswerAttachmentEntity(localId, serverId)
            db.checklistQuestionDao().updateAnswerAttachmentId(
                req.report_checklist_id,
                req.checklist_question_id,
                serverId
            )
        }

    private suspend fun processReportCover(entry: com.wooma.data.local.entity.SyncQueueEntity) =
        withContext(Dispatchers.IO) {
            if (entry.operationType != "DELETE") return@withContext
            val serverId = reportApiIdOrNull(entry.localEntityId) ?: return@withContext
            val patchJson = """{"cover_image_storage_key":null}"""
            val resp = api.updateReport(
                serverId,
                patchJson.toRequestBody("application/json".toMediaTypeOrNull())
            ).execute()
            if (!resp.isSuccessful) throw Exception("Cover DELETE PATCH failed: ${resp.code()}")
            db.reportDao().updateCoverImage(entry.localEntityId, null)
        }

    // ─── PROPERTY ────────────────────────────────────────────────────────────

    private suspend fun processProperty(entry: com.wooma.data.local.entity.SyncQueueEntity) =
        withContext(Dispatchers.IO) {
            val localId = entry.localEntityId
            val propertyEntity = db.propertyDao().getById(localId) ?: return@withContext
            val serverId = propertyEntity.serverId ?: return@withContext

            when (entry.operationType) {
                "UPDATE" -> {
                    val req = gson.fromJson(entry.payload, com.wooma.model.PropertiesRequest::class.java)
                    val resp = api.updateProperty(serverId, req).execute()
                    if (!resp.isSuccessful) throw Exception("Property UPDATE failed: ${resp.code()}")
                    db.propertyDao().updateSyncStatus(localId, SyncStatus.SYNCED)
                }
                "ARCHIVE" -> {
                    val resp = api.archiveProperty(serverId).execute()
                    if (!resp.isSuccessful && resp.code() != 404) throw Exception("Property ARCHIVE failed: ${resp.code()}")
                    db.propertyDao().updateSyncStatus(localId, SyncStatus.SYNCED)
                }
                "RESTORE" -> {
                    val resp = api.restoreProperty(serverId).execute()
                    if (!resp.isSuccessful && resp.code() != 404) throw Exception("Property RESTORE failed: ${resp.code()}")
                    db.propertyDao().updateSyncStatus(localId, SyncStatus.SYNCED)
                }
            }
        }

    // ─── REPORT ──────────────────────────────────────────────────────────────

    private suspend fun processReport(entry: com.wooma.data.local.entity.SyncQueueEntity) =
        withContext(Dispatchers.IO) {
            val serverId = reportApiIdOrNull(entry.localEntityId) ?: return@withContext
            when (entry.operationType) {
                "ARCHIVE" -> {
                    val resp = api.archiveReport(serverId).execute()
                    if (!resp.isSuccessful && resp.code() != 404) {
                        throw Exception("Report ARCHIVE failed: ${resp.code()}")
                    }
                    db.reportDao().updateSyncStatus(entry.localEntityId, SyncStatus.SYNCED)
                }
            }
        }

    // ─── REPORT SETTINGS ─────────────────────────────────────────────────────

    private suspend fun processReportAssessor(entry: com.wooma.data.local.entity.SyncQueueEntity) =
        withContext(Dispatchers.IO) {
            val serverId = reportApiIdOrNull(entry.localEntityId) ?: return@withContext
            val req = gson.fromJson(entry.payload, com.wooma.model.ChangeAssessor::class.java)
            val resp = api.changeAssessor(serverId, req).execute()
            if (!resp.isSuccessful) throw Exception("Assessor UPDATE failed: ${resp.code()}")
            db.reportDao().updateSyncStatus(entry.localEntityId, SyncStatus.SYNCED)
        }

    private suspend fun processReportDate(entry: com.wooma.data.local.entity.SyncQueueEntity) =
        withContext(Dispatchers.IO) {
            val serverId = reportApiIdOrNull(entry.localEntityId) ?: return@withContext
            val req = gson.fromJson(entry.payload, com.wooma.model.ChangeDateRequest::class.java)
            val resp = api.changeDate(serverId, req).execute()
            if (!resp.isSuccessful) throw Exception("Date UPDATE failed: ${resp.code()}")
            db.reportDao().updateSyncStatus(entry.localEntityId, SyncStatus.SYNCED)
        }

    private suspend fun processReportType(entry: com.wooma.data.local.entity.SyncQueueEntity) =
        withContext(Dispatchers.IO) {
            val serverId = reportApiIdOrNull(entry.localEntityId) ?: return@withContext
            val req = gson.fromJson(entry.payload, com.wooma.model.changeReportType::class.java)
            val resp = api.changeReportType(serverId, req).execute()
            if (!resp.isSuccessful) throw Exception("Type UPDATE failed: ${resp.code()}")
            db.reportDao().updateSyncStatus(entry.localEntityId, SyncStatus.SYNCED)
        }
}
