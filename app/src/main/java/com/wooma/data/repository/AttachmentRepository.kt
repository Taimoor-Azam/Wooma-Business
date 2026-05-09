package com.wooma.data.repository

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.wooma.customs.Utils
import com.wooma.data.local.WoomaDatabase
import com.wooma.data.local.entity.AttachmentEntity
import com.wooma.data.local.entity.SyncQueueEntity
import com.wooma.data.local.entity.PendingUploadEntity
import com.wooma.data.network.RetrofitClient
import com.wooma.model.ImageItem
import com.wooma.model.OtherItemsAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.UUID

class AttachmentRepository(private val ctx: Context) {

    private val db = WoomaDatabase.getInstance(ctx)
    private val api by lazy { RetrofitClient.getApi(ctx) }

    private suspend fun rowsForChecklistAnswerContainer(containerId: String): List<AttachmentEntity> =
        db.attachmentDao().getByEntity(containerId, "CHECKLIST_ANSWER_ATTACHMENT")

    private suspend fun findChecklistAnswerImageRow(containerId: String, item: ImageItem): AttachmentEntity? {
        val rows = rowsForChecklistAnswerContainer(containerId)
        return when (item) {
            is ImageItem.Local -> {
                val target = item.uri.path ?: item.uri.toString()
                rows.firstOrNull { a ->
                    val path = a.localUri ?: return@firstOrNull false
                    path == target ||
                        path == item.uri.toString() ||
                        runCatching {
                            File(path).canonicalPath == File(target).canonicalPath
                        }.getOrDefault(false)
                }
            }
            is ImageItem.Remote -> rows.firstOrNull {
                it.serverId == item.id || it.id == item.id
            }
        }
    }

    /**
     * Removes a checklist answer image from local DB (and file if local). When online, deletes on the server first.
     */
    suspend fun deleteChecklistAnswerImage(answerContainerId: String, item: ImageItem) = withContext(Dispatchers.IO) {
        val att = findChecklistAnswerImageRow(answerContainerId, item) ?: return@withContext
        when (item) {
            is ImageItem.Local -> {
                att.localUri?.let { db.pendingUploadDao().deleteByLocalUri(it) }
                db.syncQueueDao().deletePendingByEntityOperation(
                    entityType = "ATTACHMENT",
                    operationType = "DELETE",
                    localEntityId = att.id
                )
                db.attachmentDao().deleteById(att.id)
                att.localUri?.let { File(it).delete() }
            }
            is ImageItem.Remote -> {
                if (Utils.isOnline(ctx)) {
                    val resp = api.deleteAttachment(item.id).execute()
                    if (!resp.isSuccessful && resp.code() != 404) {
                        throw IOException("deleteAttachment failed: ${resp.code()}")
                    }
                    db.syncQueueDao().deletePendingByEntityOperation(
                        entityType = "ATTACHMENT",
                        operationType = "DELETE",
                        localEntityId = item.id
                    )
                } else {
                    db.syncQueueDao().enqueue(
                        SyncQueueEntity(
                            entityType = "ATTACHMENT",
                            operationType = "DELETE",
                            localEntityId = item.id
                        )
                    )
                }
                db.attachmentDao().deleteById(att.id)
            }
        }
    }

    /**
     * Copies [uri] to internal storage and enqueues it for background upload.
     * Returns an [AttachmentEntity] immediately so the UI can display a local preview.
     *
     * @param uri           Content URI of the image to save
     * @param entityLocalId The stable local ID of the owning entity (meter/key/detector)
     * @param entityServerId Server ID of the entity, if already synced (may be null for new entities)
     * @param entityType    "METER", "KEY", "DETECTOR", etc.
     */
    suspend fun saveLocalAttachment(
        uri: Uri,
        entityLocalId: String,
        entityServerId: String?,
        entityType: String
    ): AttachmentEntity = withContext(Dispatchers.IO) {
        val localId = "local_${UUID.randomUUID().toString().replace("-", "")}"
        val mimeType = resolveMimeType(uri)
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
        val fileName = "$localId.$ext"

        // Copy to internal storage so it survives cache clears
        val dir = File(ctx.filesDir, "attachments").also { it.mkdirs() }
        val dest = File(dir, fileName)
        openStream(uri).use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        val fileSize = dest.length()
        if (fileSize == 0L) throw IOException("Copied file is empty — source may be missing: $uri")

        val entity = AttachmentEntity(
            id = localId,
            serverId = null,
            entityId = entityLocalId,
            entityType = entityType,
            originalName = fileName,
            storageKey = null,
            link = null,
            mimeType = mimeType,
            fileSize = fileSize,
            localUri = dest.absolutePath,
            isUploaded = false
        )
        db.attachmentDao().upsert(entity)

        db.pendingUploadDao().enqueue(
            PendingUploadEntity(
                localUri = dest.absolutePath,
                entityLocalId = entityLocalId,
                entityServerId = entityServerId,
                entityType = entityType,
                fileName = fileName,
                mimeType = mimeType,
                fileSize = fileSize,
                status = "PENDING"
            )
        )

        entity
    }

    /**
     * Persists server-fetched attachments to local DB so they are visible offline.
     * Only touches rows with SYNCED state — never overwrites pending local uploads.
     * Also removes DB rows for server attachments that no longer exist on the server.
     */
    suspend fun saveServerAttachments(
        serverAttachments: List<OtherItemsAttachment>,
        entityId: String,
        entityType: String
    ) = withContext(Dispatchers.IO) {
        val existing = db.attachmentDao().getByEntity(entityId, entityType)
        val existingServerIds = existing.mapNotNull { it.serverId }.toSet()
        val liveServerIds = serverAttachments.filter { !it.is_deleted }.map { it.id }.toSet()

        for (att in serverAttachments) {
            if (att.is_deleted || att.id in existingServerIds) continue
            db.attachmentDao().upsert(
                AttachmentEntity(
                    id = att.id,
                    serverId = att.id,
                    entityId = entityId,
                    entityType = entityType,
                    originalName = att.originalName,
                    storageKey = att.storageKey,
                    mimeType = att.mimeType,
                    fileSize = att.fileSize.toLongOrNull() ?: 0L,
                    isUploaded = true
                )
            )
        }

        // Remove DB rows for attachments deleted on the server (only uploaded ones)
        for (entity in existing) {
            if (entity.isUploaded && entity.serverId != null && entity.serverId !in liveServerIds) {
                db.attachmentDao().deleteById(entity.id)
            }
        }
    }

    /**
     * Deletes a local (not-yet-uploaded) attachment: cancels its pending upload, removes
     * the DB row, and deletes the copied file. Safe to call online or offline — there is
     * no server record to worry about.
     */
    suspend fun deleteLocalAttachment(
        item: ImageItem.Local,
        entityId: String,
        entityType: String
    ) = withContext(Dispatchers.IO) {
        val att = db.attachmentDao().getByEntity(entityId, entityType)
            .firstOrNull { it.localUri == item.uri.path }
        if (att != null) {
            att.localUri?.let { db.pendingUploadDao().deleteByLocalUri(it) }
            db.attachmentDao().deleteById(att.id)
            att.localUri?.let { File(it).delete() }
        }
    }

    /**
     * Deletes an already-uploaded remote attachment from the local DB and queues the
     * server DELETE. Call this when the device is online so the image disappears
     * immediately from the UI; the sync worker sends the DELETE to the server in background.
     */
    suspend fun deleteRemoteAttachment(
        item: ImageItem.Remote,
        entityId: String,
        entityType: String
    ) = withContext(Dispatchers.IO) {
        val att = db.attachmentDao().getByEntity(entityId, entityType)
            .firstOrNull { it.serverId == item.id || it.id == item.id }
        if (att != null) db.attachmentDao().deleteById(att.id)
        db.syncQueueDao().enqueue(
            SyncQueueEntity(
                entityType = "ATTACHMENT",
                operationType = "DELETE",
                localEntityId = item.id
            )
        )
    }

    /**
     * Queues a server DELETE for a remote attachment WITHOUT removing it from the local DB.
     * Call this when the device is offline so the image remains visible until the server
     * confirms the deletion (via saveServerAttachments on the next online refresh).
     */
    suspend fun scheduleRemoteAttachmentDelete(serverId: String) = withContext(Dispatchers.IO) {
        db.syncQueueDao().enqueue(
            SyncQueueEntity(
                entityType = "ATTACHMENT",
                operationType = "DELETE",
                localEntityId = serverId
            )
        )
    }

    /** Kept for callers outside InspectionRoomActivity that rely on the old combined behaviour. */
    suspend fun deleteAttachmentOffline(
        item: ImageItem,
        entityId: String,
        entityType: String
    ) = withContext(Dispatchers.IO) {
        val existing = db.attachmentDao().getByEntity(entityId, entityType)
        when (item) {
            is ImageItem.Local -> {
                val att = existing.firstOrNull { it.localUri == item.uri.path }
                if (att != null) {
                    att.localUri?.let { db.pendingUploadDao().deleteByLocalUri(it) }
                    db.attachmentDao().deleteById(att.id)
                    att.localUri?.let { File(it).delete() }
                }
            }
            is ImageItem.Remote -> {
                val att = existing.firstOrNull { it.serverId == item.id || it.id == item.id }
                if (att != null) db.attachmentDao().deleteById(att.id)
                db.syncQueueDao().enqueue(
                    SyncQueueEntity(
                        entityType = "ATTACHMENT",
                        operationType = "DELETE",
                        localEntityId = item.id
                    )
                )
            }
        }
    }

    private fun resolveMimeType(uri: Uri): String {
        if (uri.scheme == "file") {
            val ext = uri.path?.substringAfterLast('.', "") ?: ""
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "image/jpeg"
        }
        return ctx.contentResolver.getType(uri) ?: "image/jpeg"
    }

    private fun openStream(uri: Uri): java.io.InputStream {
        if (uri.scheme == "file") {
            val path = uri.path ?: throw IOException("Null path in file URI: $uri")
            val file = File(path)
            if (!file.exists()) throw IOException("Source file not found: $path")
            return FileInputStream(file)
        }
        return ctx.contentResolver.openInputStream(uri)
            ?: throw IOException("ContentResolver returned null stream for: $uri")
    }

    /**
     * Removes orphan files from internal attachments directory.
     * Keeps files still referenced by attachments or pending_uploads tables.
     */
    suspend fun cleanupOrphanAttachmentFiles(): Int = withContext(Dispatchers.IO) {
        val dir = File(ctx.filesDir, "attachments")
        if (!dir.exists() || !dir.isDirectory) return@withContext 0

        val referencedPaths = buildSet {
            addAll(db.attachmentDao().getAllLocalUris())
            addAll(db.pendingUploadDao().getAllLocalUris())
        }

        var deletedCount = 0
        dir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            if (file.absolutePath !in referencedPaths) {
                if (file.delete()) deletedCount++
            }
        }
        deletedCount
    }
}
