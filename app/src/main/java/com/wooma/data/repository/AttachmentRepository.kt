package com.wooma.data.repository

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.wooma.data.local.WoomaDatabase
import com.wooma.data.local.entity.AttachmentEntity
import com.wooma.data.local.entity.SyncQueueEntity
import com.wooma.data.local.entity.PendingUploadEntity
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
     * Deletes an attachment offline-first.
     * Local (pending upload): cancels upload + removes from DB + deletes file.
     * Remote (uploaded): removes from DB + queues server delete for sync.
     */
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
}
