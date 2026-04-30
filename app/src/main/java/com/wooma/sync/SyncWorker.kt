package com.wooma.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.wooma.data.local.WoomaDatabase
import com.wooma.data.local.entity.SyncStatus
import com.wooma.data.network.RetrofitClient
import com.wooma.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    private val db = WoomaDatabase.getInstance(ctx)
    private val api = RetrofitClient.getApi(ctx)
    private val gson = Gson()

    override suspend fun doWork(): Result {
        val pending = db.syncQueueDao().getPendingInOrder()
        for (entry in pending) {
            // Check parent dependency — skip until parent is DONE
            if (entry.parentSyncId != null) {
                val parent = db.syncQueueDao().getById(entry.parentSyncId)
                if (parent?.status != "DONE") continue
            }

            db.syncQueueDao().updateStatus(entry.id, "IN_PROGRESS", null)
            try {
                processEntry(entry)
                db.syncQueueDao().updateStatus(entry.id, "DONE", null)
            } catch (e: Exception) {
                Log.e("SyncWorker", "Failed to sync ${entry.entityType}/${entry.operationType}: ${e.message}")
                if (entry.retryCount >= 4) {
                    db.syncQueueDao().updateStatus(entry.id, "FAILED", e.message)
                } else {
                    db.syncQueueDao().requeueForRetry(entry.id)
                }
            }
        }
        db.syncQueueDao().purgeDone()
        return Result.success()
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
            "REPORT_COVER" -> processReportCover(entry)
            "PROPERTY" -> processProperty(entry)
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
                        ?: throw Exception("Meter $localId not found locally")
                    val reportServerId = db.reportDao().getById(meterEntity.reportId)?.serverId
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
                    val reportServerId = db.reportDao().getById(meterEntity.reportId)?.serverId
                        ?: throw Exception("Report serverId not found for meter $localId")

                    val resp = api.updateMeter(reportServerId, serverId, req).execute()
                    if (!resp.isSuccessful) throw Exception("Meter UPDATE failed: ${resp.code()}")
                    db.meterDao().updateSyncStatus(localId, SyncStatus.SYNCED)
                }
                "DELETE" -> {
                    val meterEntity = db.meterDao().getById(localId) ?: return@withContext
                    val serverId = meterEntity.serverId ?: return@withContext // Never synced; nothing to delete
                    val reportServerId = db.reportDao().getById(meterEntity.reportId)?.serverId
                        ?: throw Exception("Report serverId not found for meter $localId")

                    val resp = api.deleteMeter(reportServerId, serverId).execute()
                    if (!resp.isSuccessful && resp.code() != 404) {
                        throw Exception("Meter DELETE failed: ${resp.code()}")
                    }
                    db.meterDao().updateSyncStatus(localId, SyncStatus.SYNCED)
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
                        ?: throw Exception("Key $localId not found locally")
                    val reportServerId = db.reportDao().getById(keyEntity.reportId)?.serverId
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
                    val reportServerId = db.reportDao().getById(keyEntity.reportId)?.serverId
                        ?: throw Exception("Report serverId not found for key $localId")

                    val resp = api.updateKey(reportServerId, serverId, req).execute()
                    if (!resp.isSuccessful) throw Exception("Key UPDATE failed: ${resp.code()}")
                    db.keyDao().updateSyncStatus(localId, SyncStatus.SYNCED)
                }
                "DELETE" -> {
                    val keyEntity = db.keyDao().getById(localId) ?: return@withContext
                    val serverId = keyEntity.serverId ?: return@withContext
                    val reportServerId = db.reportDao().getById(keyEntity.reportId)?.serverId
                        ?: throw Exception("Report serverId not found for key $localId")

                    val resp = api.deleteKey(reportServerId, serverId).execute()
                    if (!resp.isSuccessful && resp.code() != 404) {
                        throw Exception("Key DELETE failed: ${resp.code()}")
                    }
                    db.keyDao().updateSyncStatus(localId, SyncStatus.SYNCED)
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
                        ?: throw Exception("Detector $localId not found locally")
                    val reportServerId = db.reportDao().getById(detEntity.reportId)?.serverId
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
                    val reportServerId = db.reportDao().getById(detEntity.reportId)?.serverId
                        ?: throw Exception("Report serverId not found for detector $localId")

                    val resp = api.updateDetector(reportServerId, serverId, req).execute()
                    if (!resp.isSuccessful) throw Exception("Detector UPDATE failed: ${resp.code()}")
                    db.detectorDao().updateSyncStatus(localId, SyncStatus.SYNCED)
                }
                "DELETE" -> {
                    val detEntity = db.detectorDao().getById(localId) ?: return@withContext
                    val serverId = detEntity.serverId ?: return@withContext
                    val reportServerId = db.reportDao().getById(detEntity.reportId)?.serverId
                        ?: throw Exception("Report serverId not found for detector $localId")

                    val resp = api.deleteDetector(reportServerId, serverId).execute()
                    if (!resp.isSuccessful && resp.code() != 404) {
                        throw Exception("Detector DELETE failed: ${resp.code()}")
                    }
                    db.detectorDao().updateSyncStatus(localId, SyncStatus.SYNCED)
                }
            }
        }

    // ─── ROOM ────────────────────────────────────────────────────────────────

    private suspend fun processRoom(entry: com.wooma.data.local.entity.SyncQueueEntity) =
        withContext(Dispatchers.IO) {
            val localId = entry.localEntityId
            when (entry.operationType) {
                "CREATE" -> {
                    val req = gson.fromJson(entry.payload, AddNewRoomsRequest::class.java)
                    val roomEntity = db.roomDao().getById(localId)
                        ?: throw Exception("Room $localId not found locally")
                    val reportServerId = db.reportDao().getById(roomEntity.reportId)?.serverId
                        ?: throw Exception("Report serverId not yet available for room $localId")

                    val resp = api.addRomToReport(reportServerId, req).execute()
                    if (!resp.isSuccessful) throw Exception("Room CREATE failed: ${resp.code()}")

                    // Fetch rooms list to find the new ID
                    val roomsResp = api.getInspectionRoomById(
                        report_id = reportServerId,
                        include_items = false,
                        include_room_inspections = false
                    ).execute()
                    val roomName = req.rooms.firstOrNull() ?: ""
                    val serverId = roomsResp.body()?.data?.asReversed()
                        ?.firstOrNull { it.name == roomName }?.id
                        ?: throw Exception("Created room not found in server list")

                    db.roomDao().promoteLocalId(localId, serverId)
                    db.syncQueueDao().updateServerEntityId(localId, "ROOM", serverId)
                    db.pendingUploadDao().updateEntityServerId(localId, "ROOM", serverId)
                    db.attachmentDao().reattachToNewEntityId(localId, serverId)
                }
                "UPDATE" -> {
                    val req = gson.fromJson(entry.payload, UpdateRoomNameRequest::class.java)
                    val roomEntity = db.roomDao().getById(localId)
                        ?: throw Exception("Room $localId not found")
                    val serverId = roomEntity.serverId
                        ?: throw Exception("No serverId for ROOM UPDATE $localId")
                    val reportServerId = db.reportDao().getById(roomEntity.reportId)?.serverId
                        ?: throw Exception("Report serverId not found for room $localId")

                    val resp = api.updateRoomName(reportServerId, serverId, req).execute()
                    if (!resp.isSuccessful) throw Exception("Room UPDATE failed: ${resp.code()}")
                    db.roomDao().updateSyncStatus(localId, SyncStatus.SYNCED)
                }
                "DELETE" -> {
                    val roomEntity = db.roomDao().getById(localId) ?: return@withContext
                    val serverId = roomEntity.serverId ?: run {
                        // Never synced — just delete from local DB
                        db.roomDao().deleteById(localId)
                        return@withContext
                    }
                    val reportServerId = db.reportDao().getById(roomEntity.reportId)?.serverId
                        ?: throw Exception("Report serverId not found for room $localId")

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
                    val serverId = roomEntity.serverId
                        ?: throw Exception("No serverId for ROOM REORDER $localId")
                    val reportServerId = db.reportDao().getById(roomEntity.reportId)?.serverId
                        ?: throw Exception("Report serverId not found for room $localId")

                    val resp = api.reorderRoom(reportServerId, serverId, req).execute()
                    if (!resp.isSuccessful) throw Exception("Room REORDER failed: ${resp.code()}")
                    db.roomDao().updateSyncStatus(localId, SyncStatus.SYNCED)
                }
            }
        }

    private suspend fun processRoomItem(entry: com.wooma.data.local.entity.SyncQueueEntity) =
        withContext(Dispatchers.IO) {
            val localId = entry.localEntityId
            when (entry.operationType) {
                "CREATE" -> {
                    val req = gson.fromJson(entry.payload, AddNewRoomItemsRequest::class.java)
                    val itemEntity = db.roomItemDao().getById(localId)
                        ?: throw Exception("Item $localId not found locally")
                    val roomServerId = db.roomDao().getById(itemEntity.roomId)?.serverId
                        ?: throw Exception("Room serverId not available for item $localId")
                    val reportServerId = db.reportDao().getById(db.roomDao().getById(itemEntity.roomId)!!.reportId)?.serverId
                        ?: throw Exception("Report serverId not found for item $localId")

                    val resp = api.addRoomItemsToReport(reportServerId, roomServerId, req).execute()
                    if (!resp.isSuccessful) throw Exception("Item CREATE failed: ${resp.code()}")

                    // Find server ID
                    val itemsResp = api.getRoomById(roomServerId, reportServerId, include_items = true, include_room_inspections = false).execute()
                    val serverId = itemsResp.body()?.data?.items?.asReversed()
                        ?.firstOrNull { it.name == itemEntity.name }?.id
                        ?: throw Exception("Created item not found on server")

                    db.roomItemDao().promoteLocalId(localId, serverId)
                    db.syncQueueDao().updateServerEntityId(localId, "ROOM_ITEM", serverId)
                    db.pendingUploadDao().updateEntityServerId(localId, "ROOM_ITEM", serverId)
                    db.attachmentDao().reattachToNewEntityId(localId, serverId)
                }
                "UPDATE" -> {
                    val req = gson.fromJson(entry.payload, UpdateRoomItemRequest::class.java)
                    val itemEntity = db.roomItemDao().getById(localId) ?: return@withContext
                    val serverId = itemEntity.serverId ?: return@withContext
                    val roomServerId = db.roomDao().getById(itemEntity.roomId)?.serverId ?: return@withContext
                    val reportServerId = db.reportDao().getById(db.roomDao().getById(itemEntity.roomId)!!.reportId)?.serverId ?: return@withContext

                    val resp = api.updateRoomItem(reportServerId, roomServerId, serverId, req).execute()
                    if (!resp.isSuccessful) throw Exception("Item UPDATE failed: ${resp.code()}")
                    db.roomItemDao().updateSyncStatus(localId, SyncStatus.SYNCED)
                }
                "DELETE" -> {
                    val itemEntity = db.roomItemDao().getById(localId) ?: return@withContext
                    val serverId = itemEntity.serverId ?: return@withContext
                    val roomServerId = db.roomDao().getById(itemEntity.roomId)?.serverId ?: return@withContext
                    val reportServerId = db.reportDao().getById(db.roomDao().getById(itemEntity.roomId)!!.reportId)?.serverId ?: return@withContext

                    val resp = api.deleteRoomItem(reportServerId, roomServerId, serverId).execute()
                    if (!resp.isSuccessful && resp.code() != 404) throw Exception("Item DELETE failed")
                    db.roomItemDao().updateSyncStatus(localId, SyncStatus.SYNCED)
                }
            }
        }

    private suspend fun processRoomInspection(entry: com.wooma.data.local.entity.SyncQueueEntity) =
        withContext(Dispatchers.IO) {
            val localId = entry.localEntityId
            val req = gson.fromJson(entry.payload, UpsertRoomInspectionRequest::class.java)
            val inspection = db.roomInspectionDao().getById(localId) ?: return@withContext
            val roomServerId = db.roomDao().getById(inspection.roomId)?.serverId ?: return@withContext
            
            val updatedReq = req.copy(room_id = roomServerId)
            val resp = api.upsertRoomInspection(updatedReq).execute()
            if (!resp.isSuccessful) throw Exception("Inspection UPSERT failed")
            
            // Refetch to get server ID if it was CREATE
            if (inspection.serverId == null) {
                val reportServerId = db.reportDao().getById(db.roomDao().getById(inspection.roomId)!!.reportId)?.serverId ?: return@withContext
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
        }

    private suspend fun processReportCover(entry: com.wooma.data.local.entity.SyncQueueEntity) =
        withContext(Dispatchers.IO) {
            // REPORT_COVER is handled primarily via ImageUploadWorker (it resolves the storageKey)
            // This processEntry for cover is just a placeholder if we need PATCH update separately.
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
            }
        }

    // ─── REPORT SETTINGS ─────────────────────────────────────────────────────

    private suspend fun processReportAssessor(entry: com.wooma.data.local.entity.SyncQueueEntity) =
        withContext(Dispatchers.IO) {
            val reportEntity = db.reportDao().getById(entry.localEntityId) ?: return@withContext
            val serverId = reportEntity.serverId ?: return@withContext
            val req = gson.fromJson(entry.payload, com.wooma.model.ChangeAssessor::class.java)
            val resp = api.changeAssessor(serverId, req).execute()
            if (!resp.isSuccessful) throw Exception("Assessor UPDATE failed: ${resp.code()}")
            db.reportDao().updateSyncStatus(entry.localEntityId, SyncStatus.SYNCED)
        }

    private suspend fun processReportDate(entry: com.wooma.data.local.entity.SyncQueueEntity) =
        withContext(Dispatchers.IO) {
            val reportEntity = db.reportDao().getById(entry.localEntityId) ?: return@withContext
            val serverId = reportEntity.serverId ?: return@withContext
            val req = gson.fromJson(entry.payload, com.wooma.model.ChangeDateRequest::class.java)
            val resp = api.changeDate(serverId, req).execute()
            if (!resp.isSuccessful) throw Exception("Date UPDATE failed: ${resp.code()}")
            db.reportDao().updateSyncStatus(entry.localEntityId, SyncStatus.SYNCED)
        }

    private suspend fun processReportType(entry: com.wooma.data.local.entity.SyncQueueEntity) =
        withContext(Dispatchers.IO) {
            val reportEntity = db.reportDao().getById(entry.localEntityId) ?: return@withContext
            val serverId = reportEntity.serverId ?: return@withContext
            val req = gson.fromJson(entry.payload, com.wooma.model.changeReportType::class.java)
            val resp = api.changeReportType(serverId, req).execute()
            if (!resp.isSuccessful) throw Exception("Type UPDATE failed: ${resp.code()}")
            db.reportDao().updateSyncStatus(entry.localEntityId, SyncStatus.SYNCED)
        }
}
