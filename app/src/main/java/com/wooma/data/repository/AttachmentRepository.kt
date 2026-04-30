package com.wooma.data.repository

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.wooma.data.local.WoomaDatabase
import com.wooma.data.local.entity.AttachmentEntity
import com.wooma.data.local.entity.PendingUploadEntity
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
