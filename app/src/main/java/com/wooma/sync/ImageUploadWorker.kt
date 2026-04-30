package com.wooma.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wooma.data.local.WoomaDatabase
import com.wooma.data.network.RetrofitClient
import com.wooma.model.CreateAttachmentRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class ImageUploadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    private val db = WoomaDatabase.getInstance(ctx)
    private val api = RetrofitClient.getApi(ctx)

    // Plain OkHttpClient without auth interceptor — S3 presigned URLs reject extra headers
    private val plainHttp = OkHttpClient()

    override suspend fun doWork(): Result {
        val pending = db.pendingUploadDao().getPending()
        for (upload in pending) {
            // Resolve entity server ID if not yet set
            val entityServerId = upload.entityServerId
                ?: resolveEntityServerId(upload.entityLocalId, upload.entityType)

            if (entityServerId == null) {
                // Entity hasn't synced yet; skip this upload — SyncWorker will update entityServerId
                Log.d("ImageUploadWorker", "Skipping upload for ${upload.entityType}/${upload.entityLocalId}: no serverId yet")
                continue
            }

            // Persist resolved serverId so we don't re-resolve next time
            if (upload.entityServerId == null) {
                db.pendingUploadDao().updateEntityServerId(upload.entityLocalId, upload.entityType, entityServerId)
            }

            db.pendingUploadDao().updateStatus(upload.id, "UPLOADING")
            try {
                val file = File(upload.localUri)
                if (!file.exists()) {
                    Log.w("ImageUploadWorker", "File not found: ${upload.localUri}")
                    db.pendingUploadDao().updateStatus(upload.id, "FAILED")
                    continue
                }

                // 1. Get presigned URL from our API
                val presignedResp = withContext(Dispatchers.IO) {
                    api.getPresignedUrl(upload.fileName, upload.mimeType).execute()
                }
                val presignedBody = presignedResp.body()?.data
                    ?: throw Exception("No presigned URL returned (${presignedResp.code()})")
                val uploadUrl = presignedBody.url
                val storageKey = presignedBody.key

                // 2. PUT file bytes directly to S3 (no auth header)
                val fileBytes = file.readBytes()
                val mediaType = upload.mimeType.toMediaTypeOrNull()
                val putRequest = Request.Builder()
                    .url(uploadUrl)
                    .put(fileBytes.toRequestBody(mediaType))
                    .build()
                val putResp = withContext(Dispatchers.IO) {
                    plainHttp.newCall(putRequest).execute()
                }
                if (!putResp.isSuccessful) {
                    throw Exception("S3 PUT failed: ${putResp.code}")
                }

                // 3. Register the attachment record on our server
                val createResp = withContext(Dispatchers.IO) {
                    api.createAttachment(
                        CreateAttachmentRequest(
                            entityId = entityServerId,
                            entityType = upload.entityType,
                            originalName = upload.fileName,
                            storageKey = storageKey,
                            mimeType = upload.mimeType,
                            fileSize = upload.fileSize
                        )
                    ).execute()
                }
                val attachmentId = createResp.body()?.data?.id
                    ?: throw Exception("createAttachment returned null id (${createResp.code()})")

                // 4. Mark done in local DB
                db.pendingUploadDao().markDone(upload.id, storageKey, attachmentId)
                // Attachment local ID is the filename without its extension
                val attachmentLocalId = upload.fileName.substringBeforeLast('.')
                db.attachmentDao().markUploaded(attachmentLocalId, attachmentId, storageKey)

                Log.d("ImageUploadWorker", "Uploaded ${upload.fileName} → $storageKey")
            } catch (e: Exception) {
                Log.e("ImageUploadWorker", "Upload failed for ${upload.fileName}: ${e.message}")
                if (upload.retryCount >= 4) {
                    db.pendingUploadDao().updateStatus(upload.id, "FAILED")
                } else {
                    db.pendingUploadDao().requeueForRetry(upload.id)
                }
            }
        }
        db.pendingUploadDao().purgeDone()
        return Result.success()
    }

    private suspend fun resolveEntityServerId(localId: String, type: String): String? = when (type) {
        "METER"        -> db.meterDao().getById(localId)?.serverId
        "KEY"          -> db.keyDao().getById(localId)?.serverId
        "DETECTOR"     -> db.detectorDao().getById(localId)?.serverId
        "ROOM"         -> db.roomDao().getById(localId)?.serverId
        "ROOM_ITEM"    -> db.roomItemDao().getById(localId)?.serverId
        "REPORT_COVER" -> db.reportDao().getById(localId)?.serverId
        else           -> null
    }
}
