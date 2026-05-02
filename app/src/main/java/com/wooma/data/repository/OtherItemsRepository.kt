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
            val localEntities = db.meterDao().getByReport(reportId)
            val pendingIds = localEntities
                .filter { it.syncStatus != SyncStatus.SYNCED }
                .map { it.id }
                .toSet()
            val existingIds = localEntities.map { it.id }.toSet()
            val existingServerIds = localEntities.mapNotNull { it.serverId }.toSet()
            val toUpsert = meters.map { it.toEntity(reportId) }
                .filter { it.id !in pendingIds && (it.id in existingIds || it.id !in existingServerIds) }
            db.meterDao().upsertAll(toUpsert)
            val existingAtts = db.attachmentDao().getByEntityType("METER")
            val existingAttIds = existingAtts.map { it.id }.toSet()
            val existingAttServerIds = existingAtts.mapNotNull { it.serverId }.toSet()
            val meterAttachments = meters
                .filter { it.id !in pendingIds && (it.id in existingIds || it.id !in existingServerIds) }
                .flatMap { meter ->
                    meter.attachments
                        .filter { att -> att.id in existingAttIds || att.id !in existingAttServerIds }
                        .map { att ->
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

    suspend fun updateMeter(localId: String, request: AddMeterRequest) {
        val existing = db.meterDao().getById(localId) ?: return
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
            db.meterDao().updateSyncStatus(localId, SyncStatus.PENDING_UPDATE)
            db.syncQueueDao().enqueue(
                SyncQueueEntity(
                    entityType = "METER", operationType = "UPDATE",
                    localEntityId = localId, payload = gson.toJson(request)
                )
            )
        }
        // If PENDING_CREATE: entity updated locally; the CREATE payload will reflect new values
    }

    suspend fun deleteMeter(localId: String) {
        val existing = db.meterDao().getById(localId) ?: return
        db.meterDao().softDelete(localId)
        db.reportDao().incrementMeterCount(existing.reportId, -1)
        if (existing.syncStatus != SyncStatus.PENDING_CREATE) {
            db.syncQueueDao().enqueue(
                SyncQueueEntity(
                    entityType = "METER", operationType = "DELETE",
                    localEntityId = localId, payload = "{}"
                )
            )
        }
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
            val localEntities = db.keyDao().getByReport(reportId)
            val pendingIds = localEntities
                .filter { it.syncStatus != SyncStatus.SYNCED }
                .map { it.id }
                .toSet()
            val existingIds = localEntities.map { it.id }.toSet()
            val existingServerIds = localEntities.mapNotNull { it.serverId }.toSet()
            val toUpsert = keys.map { it.toEntity(reportId) }
                .filter { it.id !in pendingIds && (it.id in existingIds || it.id !in existingServerIds) }
            db.keyDao().upsertAll(toUpsert)
            val existingAtts = db.attachmentDao().getByEntityType("KEY")
            val existingAttIds = existingAtts.map { it.id }.toSet()
            val existingAttServerIds = existingAtts.mapNotNull { it.serverId }.toSet()
            val keyAttachments = keys
                .filter { it.id !in pendingIds && (it.id in existingIds || it.id !in existingServerIds) }
                .flatMap { key ->
                    key.attachments
                        .filter { att -> att.id in existingAttIds || att.id !in existingAttServerIds }
                        .map { att ->
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

    suspend fun updateKey(localId: String, request: AddKeyRequest) {
        val existing = db.keyDao().getById(localId) ?: return
        db.keyDao().upsert(
            existing.copy(
                name = request.name,
                noOfKeys = request.no_of_keys,
                note = request.note.ifEmpty { null },
                updatedAt = System.currentTimeMillis().toString()
            )
        )
        if (existing.syncStatus == SyncStatus.SYNCED) {
            db.keyDao().updateSyncStatus(localId, SyncStatus.PENDING_UPDATE)
            db.syncQueueDao().enqueue(
                SyncQueueEntity(
                    entityType = "KEY", operationType = "UPDATE",
                    localEntityId = localId, payload = gson.toJson(request)
                )
            )
        }
    }

    suspend fun deleteKey(localId: String) {
        val existing = db.keyDao().getById(localId) ?: return
        db.keyDao().softDelete(localId)
        db.reportDao().incrementKeyCount(existing.reportId, -1)
        if (existing.syncStatus != SyncStatus.PENDING_CREATE) {
            db.syncQueueDao().enqueue(
                SyncQueueEntity(
                    entityType = "KEY", operationType = "DELETE",
                    localEntityId = localId, payload = "{}"
                )
            )
        }
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
            val localEntities = db.detectorDao().getByReport(reportId)
            val pendingIds = localEntities
                .filter { it.syncStatus != SyncStatus.SYNCED }
                .map { it.id }
                .toSet()
            val existingIds = localEntities.map { it.id }.toSet()
            val existingServerIds = localEntities.mapNotNull { it.serverId }.toSet()
            val toUpsert = dets.map { it.toEntity(reportId) }
                .filter { it.id !in pendingIds && (it.id in existingIds || it.id !in existingServerIds) }
            db.detectorDao().upsertAll(toUpsert)
            val existingAtts = db.attachmentDao().getByEntityType("DETECTOR")
            val existingAttIds = existingAtts.map { it.id }.toSet()
            val existingAttServerIds = existingAtts.mapNotNull { it.serverId }.toSet()
            val detectorAttachments = dets
                .filter { it.id !in pendingIds && (it.id in existingIds || it.id !in existingServerIds) }
                .flatMap { det ->
                    det.attachments
                        .filter { att -> att.id in existingAttIds || att.id !in existingAttServerIds }
                        .map { att ->
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

    suspend fun updateDetector(localId: String, request: AddDetectorRequest) {
        val existing = db.detectorDao().getById(localId) ?: return
        db.detectorDao().upsert(
            existing.copy(
                name = request.name,
                location = request.location.ifEmpty { null },
                note = request.note.ifEmpty { null },
                updatedAt = System.currentTimeMillis().toString()
            )
        )
        if (existing.syncStatus == SyncStatus.SYNCED) {
            db.detectorDao().updateSyncStatus(localId, SyncStatus.PENDING_UPDATE)
            db.syncQueueDao().enqueue(
                SyncQueueEntity(
                    entityType = "DETECTOR", operationType = "UPDATE",
                    localEntityId = localId, payload = gson.toJson(request)
                )
            )
        }
    }

    suspend fun deleteDetector(localId: String) {
        val existing = db.detectorDao().getById(localId) ?: return
        db.detectorDao().softDelete(localId)
        db.reportDao().incrementDetectorCount(existing.reportId, -1)
        if (existing.syncStatus != SyncStatus.PENDING_CREATE) {
            db.syncQueueDao().enqueue(
                SyncQueueEntity(
                    entityType = "DETECTOR", operationType = "DELETE",
                    localEntityId = localId, payload = "{}"
                )
            )
        }
    }
}
