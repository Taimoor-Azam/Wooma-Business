package com.wooma.data.repository

import android.content.Context
import com.google.gson.Gson
import com.wooma.data.local.WoomaDatabase
import com.wooma.data.local.entity.*
import com.wooma.data.local.mapper.toDetectorItem
import com.wooma.data.local.mapper.toKeyItem
import com.wooma.data.local.mapper.toMeter
import com.wooma.data.local.mapper.toEntity
import com.wooma.data.network.RetrofitClient
import com.wooma.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class OtherItemsRepository(private val ctx: Context) {

    private val db = WoomaDatabase.getInstance(ctx)
    private val api by lazy { RetrofitClient.getApi(ctx) }
    private val gson = Gson()

    // ═══════════════════════════════════════════════════════════════════════════
    //  METERS
    // ═══════════════════════════════════════════════════════════════════════════

    fun observeMeters(reportId: String): Flow<List<Meter>> =
        db.meterDao().observeByReport(reportId)
            .map { list -> list.filter { !it.isDeleted }.map { it.toMeter() } }

    suspend fun refreshMeters(reportId: String) = withContext(Dispatchers.IO) {
        val serverId = db.reportDao().getById(reportId)?.serverId ?: return@withContext
        val resp = api.getReportMeters(serverId, include_attachments = true).execute()
        resp.body()?.data?.let { meters ->
            replaceMetersFromServer(reportId, meters)
        }
    }

    suspend fun reorderMeter(localId: String, prevRank: String?, nextRank: String?) = withContext(Dispatchers.IO) {
        val meter = db.meterDao().getByLocalOrServerId(localId) ?: return@withContext
        if (meter.syncStatus != SyncStatus.SYNCED) return@withContext
        val reportServerId = db.reportDao().getById(meter.reportId)?.serverId ?: return@withContext
        val meterServerId = meter.serverId ?: return@withContext
        val reorderResp = api.reorderMeter(
            reportServerId,
            meterServerId,
            ReorderRoomRequest(prev_rank = prevRank, next_rank = nextRank)
        ).execute()
        if (!reorderResp.isSuccessful) throw Exception("Meter REORDER failed: ${reorderResp.code()}")

        val refreshResp = api.getReportMeters(reportServerId, include_attachments = true).execute()
        if (!refreshResp.isSuccessful) throw Exception("Meter REFRESH after reorder failed: ${refreshResp.code()}")
        replaceMetersFromServer(meter.reportId, refreshResp.body()?.data ?: emptyList())
    }

    suspend fun addMeter(reportId: String, request: AddMeterRequest): MeterEntity {
        val localId = "local_${UUID.randomUUID().toString().replace("-", "")}"
        val now = System.currentTimeMillis().toString()
        val entity = MeterEntity(
            id = localId, serverId = null, reportId = reportId,
            name = request.name, reading = request.reading.ifEmpty { null },
            location = request.location.ifEmpty { null },
            serialNumber = request.serial_number.ifEmpty { null },
            displayOrder = null, isDeleted = false,
            createdAt = now, updatedAt = now,
            syncStatus = SyncStatus.PENDING_CREATE
        )
        db.meterDao().upsert(entity)
        db.reportDao().incrementMeterCount(reportId, 1)
        db.syncQueueDao().enqueue(
            SyncQueueEntity(
                entityType = "METER", operationType = "CREATE",
                localEntityId = localId, payload = gson.toJson(request)
            )
        )
        return entity
    }

    suspend fun updateMeter(id: String, request: AddMeterRequest) {
        val existing = db.meterDao().getByLocalOrServerId(id) ?: return
        db.meterDao().upsert(
            existing.copy(
                name = request.name,
                reading = request.reading.ifEmpty { null },
                location = request.location.ifEmpty { null },
                serialNumber = request.serial_number.ifEmpty { null },
                updatedAt = System.currentTimeMillis().toString()
            )
        )
        if (existing.syncStatus == SyncStatus.SYNCED) {
            db.meterDao().updateSyncStatus(existing.id, SyncStatus.PENDING_UPDATE)
            db.syncQueueDao().enqueue(
                SyncQueueEntity(
                    entityType = "METER", operationType = "UPDATE",
                    localEntityId = existing.id, payload = gson.toJson(request)
                )
            )
        }
    }

    suspend fun deleteMeter(id: String) {
        val existing = db.meterDao().getByLocalOrServerId(id) ?: return
        db.reportDao().incrementMeterCount(existing.reportId, -1)
        if (existing.syncStatus == SyncStatus.PENDING_CREATE) {
            db.meterDao().deleteById(existing.id)
            db.syncQueueDao().cancelPendingCreate(existing.id, "METER")
        } else {
            db.meterDao().softDelete(existing.id)
            val serverId = existing.serverId
            if (serverId != null && serverId != existing.id) {
                db.meterDao().deleteById(serverId)
            }
            db.syncQueueDao().enqueue(
                SyncQueueEntity(
                    entityType = "METER", operationType = "DELETE",
                    localEntityId = existing.id, payload = "{}"
                )
            )
        }
    }

    private fun meterSortKey(rank: String?): String {
        val value = rank?.trim().orEmpty()
        return if (value.isEmpty()) "\uFFFF" else value
    }

    private suspend fun replaceMetersFromServer(reportId: String, meters: List<Meter>) {
        val sortedMeters = meters.sortedWith(compareBy<Meter> { meterSortKey(it.display_order) }.thenBy { it.id })
        val pendingLocals = db.meterDao().getAllByReport(reportId).filter { it.syncStatus != SyncStatus.SYNCED }
        db.meterDao().deleteSyncedByReport(reportId)
        db.attachmentDao().deleteUploadedByEntityType("METER")
        db.meterDao().upsertAll(sortedMeters.map { it.toEntity(reportId) })
        if (pendingLocals.isNotEmpty()) db.meterDao().upsertAll(pendingLocals)

        val meterAttachments = sortedMeters.flatMap { meter ->
            meter.attachments.map { att ->
                AttachmentEntity(
                    id = att.id,
                    serverId = att.id,
                    entityId = meter.id,
                    entityType = "METER",
                    originalName = att.originalName,
                    storageKey = att.storageKey,
                    link = att.link,
                    mimeType = att.mimeType,
                    fileSize = att.fileSize.toLongOrNull() ?: 0L,
                    localUri = null,
                    isUploaded = true
                )
            }
        }
        if (meterAttachments.isNotEmpty()) db.attachmentDao().upsertAll(meterAttachments)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  KEYS
    // ═══════════════════════════════════════════════════════════════════════════

    fun observeKeys(reportId: String): Flow<List<KeyItem>> =
        db.keyDao().observeByReport(reportId)
            .map { list -> list.filter { !it.isDeleted }.map { it.toKeyItem() } }

    suspend fun refreshKeys(reportId: String) = withContext(Dispatchers.IO) {
        val serverId = db.reportDao().getById(reportId)?.serverId ?: return@withContext
        val resp = api.getReportKeys(serverId, include_attachments = true).execute()
        resp.body()?.data?.let { keys ->
            replaceKeysFromServer(reportId, keys)
        }
    }

    suspend fun reorderKey(localId: String, prevRank: String?, nextRank: String?) = withContext(Dispatchers.IO) {
        val key = db.keyDao().getByLocalOrServerId(localId) ?: return@withContext
        if (key.syncStatus != SyncStatus.SYNCED) return@withContext
        val reportServerId = db.reportDao().getById(key.reportId)?.serverId ?: return@withContext
        val keyServerId = key.serverId ?: return@withContext
        val reorderResp = api.reorderKey(
            reportServerId,
            keyServerId,
            ReorderRoomRequest(prev_rank = prevRank, next_rank = nextRank)
        ).execute()
        if (!reorderResp.isSuccessful) throw Exception("Key REORDER failed: ${reorderResp.code()}")

        val refreshResp = api.getReportKeys(reportServerId, include_attachments = true).execute()
        if (!refreshResp.isSuccessful) throw Exception("Key REFRESH after reorder failed: ${refreshResp.code()}")
        replaceKeysFromServer(key.reportId, refreshResp.body()?.data ?: emptyList())
    }

    suspend fun addKey(reportId: String, request: AddKeyRequest): KeyEntity {
        val localId = "local_${UUID.randomUUID().toString().replace("-", "")}"
        val now = System.currentTimeMillis().toString()
        val entity = KeyEntity(
            id = localId, serverId = null, reportId = reportId,
            name = request.name, noOfKeys = request.no_of_keys,
            note = request.note.ifEmpty { null },
            displayOrder = null, isDeleted = false,
            createdAt = now, updatedAt = now,
            syncStatus = SyncStatus.PENDING_CREATE
        )
        db.keyDao().upsert(entity)
        db.reportDao().incrementKeyCount(reportId, 1)
        db.syncQueueDao().enqueue(
            SyncQueueEntity(
                entityType = "KEY", operationType = "CREATE",
                localEntityId = localId, payload = gson.toJson(request)
            )
        )
        return entity
    }

    suspend fun updateKey(id: String, request: AddKeyRequest) {
        val existing = db.keyDao().getByLocalOrServerId(id) ?: return
        db.keyDao().upsert(
            existing.copy(
                name = request.name,
                noOfKeys = request.no_of_keys,
                note = request.note.ifEmpty { null },
                updatedAt = System.currentTimeMillis().toString()
            )
        )
        if (existing.syncStatus == SyncStatus.SYNCED) {
            db.keyDao().updateSyncStatus(existing.id, SyncStatus.PENDING_UPDATE)
            db.syncQueueDao().enqueue(
                SyncQueueEntity(
                    entityType = "KEY", operationType = "UPDATE",
                    localEntityId = existing.id, payload = gson.toJson(request)
                )
            )
        }
    }

    suspend fun deleteKey(id: String) {
        val existing = db.keyDao().getByLocalOrServerId(id) ?: return
        db.reportDao().incrementKeyCount(existing.reportId, -1)
        if (existing.syncStatus == SyncStatus.PENDING_CREATE) {
            db.keyDao().deleteById(existing.id)
            db.syncQueueDao().cancelPendingCreate(existing.id, "KEY")
        } else {
            db.keyDao().softDelete(existing.id)
            val serverId = existing.serverId
            if (serverId != null && serverId != existing.id) {
                db.keyDao().deleteById(serverId)
            }
            db.syncQueueDao().enqueue(
                SyncQueueEntity(
                    entityType = "KEY", operationType = "DELETE",
                    localEntityId = existing.id, payload = "{}"
                )
            )
        }
    }

    private suspend fun replaceKeysFromServer(reportId: String, keys: List<KeyItem>) {
        val sortedKeys = keys.sortedWith(compareBy<KeyItem> { keySortKey(it.display_order) }.thenBy { it.id })
        val pendingLocals = db.keyDao().getAllByReport(reportId).filter { it.syncStatus != SyncStatus.SYNCED }
        db.keyDao().deleteSyncedByReport(reportId)
        db.attachmentDao().deleteUploadedByEntityType("KEY")
        db.keyDao().upsertAll(sortedKeys.map { it.toEntity(reportId) })
        if (pendingLocals.isNotEmpty()) db.keyDao().upsertAll(pendingLocals)

        val keyAttachments = sortedKeys.flatMap { key ->
            key.attachments.map { att ->
                AttachmentEntity(
                    id = att.id,
                    serverId = att.id,
                    entityId = key.id,
                    entityType = "KEY",
                    originalName = att.originalName,
                    storageKey = att.storageKey,
                    link = att.link,
                    mimeType = att.mimeType,
                    fileSize = att.fileSize.toLongOrNull() ?: 0L,
                    localUri = null,
                    isUploaded = true
                )
            }
        }
        if (keyAttachments.isNotEmpty()) db.attachmentDao().upsertAll(keyAttachments)
    }

    private fun keySortKey(rank: String?): String {
        val value = rank?.trim().orEmpty()
        return if (value.isEmpty()) "\uFFFF" else value
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  DETECTORS
    // ═══════════════════════════════════════════════════════════════════════════

    fun observeDetectors(reportId: String): Flow<List<DetectorItem>> =
        db.detectorDao().observeByReport(reportId)
            .map { list -> list.filter { !it.isDeleted }.map { it.toDetectorItem() } }

    suspend fun refreshDetectors(reportId: String) = withContext(Dispatchers.IO) {
        val serverId = db.reportDao().getById(reportId)?.serverId ?: return@withContext
        val resp = api.getReportDetector(serverId, include_attachments = true).execute()
        resp.body()?.data?.let { dets ->
            replaceDetectorsFromServer(reportId, dets)
        }
    }

    suspend fun reorderDetector(localId: String, prevRank: String?, nextRank: String?) = withContext(Dispatchers.IO) {
        val detector = db.detectorDao().getByLocalOrServerId(localId) ?: return@withContext
        if (detector.syncStatus != SyncStatus.SYNCED) return@withContext
        val reportServerId = db.reportDao().getById(detector.reportId)?.serverId ?: return@withContext
        val detectorServerId = detector.serverId ?: return@withContext
        val reorderResp = api.reorderDetector(
            reportServerId,
            detectorServerId,
            ReorderRoomRequest(prev_rank = prevRank, next_rank = nextRank)
        ).execute()
        if (!reorderResp.isSuccessful) throw Exception("Detector REORDER failed: ${reorderResp.code()}")

        val refreshResp = api.getReportDetector(reportServerId, include_attachments = true).execute()
        if (!refreshResp.isSuccessful) throw Exception("Detector REFRESH after reorder failed: ${refreshResp.code()}")
        replaceDetectorsFromServer(detector.reportId, refreshResp.body()?.data ?: emptyList())
    }

    suspend fun addDetector(reportId: String, request: AddDetectorRequest): DetectorEntity {
        val localId = "local_${UUID.randomUUID().toString().replace("-", "")}"
        val now = System.currentTimeMillis().toString()
        val entity = DetectorEntity(
            id = localId, serverId = null, reportId = reportId,
            name = request.name, location = request.location.ifEmpty { null },
            note = request.note.ifEmpty { null },
            displayOrder = null, isDeleted = false,
            createdAt = now, updatedAt = now,
            syncStatus = SyncStatus.PENDING_CREATE
        )
        db.detectorDao().upsert(entity)
        db.reportDao().incrementDetectorCount(reportId, 1)
        db.syncQueueDao().enqueue(
            SyncQueueEntity(
                entityType = "DETECTOR", operationType = "CREATE",
                localEntityId = localId, payload = gson.toJson(request)
            )
        )
        return entity
    }

    suspend fun updateDetector(id: String, request: AddDetectorRequest) {
        val existing = db.detectorDao().getByLocalOrServerId(id) ?: return
        db.detectorDao().upsert(
            existing.copy(
                name = request.name,
                location = request.location.ifEmpty { null },
                note = request.note.ifEmpty { null },
                updatedAt = System.currentTimeMillis().toString()
            )
        )
        if (existing.syncStatus == SyncStatus.SYNCED) {
            db.detectorDao().updateSyncStatus(existing.id, SyncStatus.PENDING_UPDATE)
            db.syncQueueDao().enqueue(
                SyncQueueEntity(
                    entityType = "DETECTOR", operationType = "UPDATE",
                    localEntityId = existing.id, payload = gson.toJson(request)
                )
            )
        }
    }

    suspend fun deleteDetector(id: String) {
        val existing = db.detectorDao().getByLocalOrServerId(id) ?: return
        db.reportDao().incrementDetectorCount(existing.reportId, -1)
        if (existing.syncStatus == SyncStatus.PENDING_CREATE) {
            db.detectorDao().deleteById(existing.id)
            db.syncQueueDao().cancelPendingCreate(existing.id, "DETECTOR")
        } else {
            db.detectorDao().softDelete(existing.id)
            val serverId = existing.serverId
            if (serverId != null && serverId != existing.id) {
                db.detectorDao().deleteById(serverId)
            }
            db.syncQueueDao().enqueue(
                SyncQueueEntity(
                    entityType = "DETECTOR", operationType = "DELETE",
                    localEntityId = existing.id, payload = "{}"
                )
            )
        }
    }

    private suspend fun replaceDetectorsFromServer(reportId: String, dets: List<DetectorItem>) {
        val sortedDetectors = dets.sortedWith(compareBy<DetectorItem> { detectorSortKey(it.display_order) }.thenBy { it.id })
        val pendingLocals = db.detectorDao().getAllByReport(reportId).filter { it.syncStatus != SyncStatus.SYNCED }
        db.detectorDao().deleteSyncedByReport(reportId)
        db.attachmentDao().deleteUploadedByEntityType("DETECTOR")
        db.detectorDao().upsertAll(sortedDetectors.map { it.toEntity(reportId) })
        if (pendingLocals.isNotEmpty()) db.detectorDao().upsertAll(pendingLocals)

        val detectorAttachments = sortedDetectors.flatMap { det ->
            det.attachments.map { att ->
                AttachmentEntity(
                    id = att.id,
                    serverId = att.id,
                    entityId = det.id,
                    entityType = "DETECTOR",
                    originalName = att.originalName,
                    storageKey = att.storageKey,
                    link = att.link,
                    mimeType = att.mimeType,
                    fileSize = att.fileSize.toLongOrNull() ?: 0L,
                    localUri = null,
                    isUploaded = true
                )
            }
        }
        if (detectorAttachments.isNotEmpty()) db.attachmentDao().upsertAll(detectorAttachments)
    }

    private fun detectorSortKey(rank: String?): String {
        val value = rank?.trim().orEmpty()
        return if (value.isEmpty()) "\uFFFF" else value
    }
}
