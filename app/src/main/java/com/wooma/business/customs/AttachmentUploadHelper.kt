package com.wooma.business.customs

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import com.wooma.business.data.network.ApiResponseListener
import com.wooma.business.data.network.MyApi
import com.wooma.business.data.network.RetrofitClient
import com.wooma.business.data.network.makeApiRequest
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.AttachmentRecord
import com.wooma.business.model.CreateAttachmentRequest
import com.wooma.business.model.ErrorResponse
import com.wooma.business.model.PresignedUrlResponse
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object AttachmentUploadHelper {

    private val s3Client = OkHttpClient()

    /**
     * Uploads a list of image URIs for a given entity.
     * Calls back [onComplete] with the list of created AttachmentRecords,
     * or [onError] if any step fails.
     *
     * Must be called from an Activity (uses makeApiRequest internally).
     */
    fun uploadImages(
        activity: Activity,
        imageUris: List<Uri>,
        entityId: String,
        entityType: String,
        onComplete: (List<AttachmentRecord>) -> Unit,
        onError: (String) -> Unit
    ) {
        if (imageUris.isEmpty()) {
            onComplete(emptyList())
            return
        }

        val results = mutableListOf<AttachmentRecord>()
        var remaining = imageUris.size

        for (uri in imageUris) {
            val fileInfo = getFileInfo(activity, uri)
            if (fileInfo == null) {
                remaining--
                if (remaining == 0) onComplete(results)
                continue
            }
            val (originalName, mimeType, fileSize) = fileInfo

            // Step 1: Get presigned URL
            activity.makeApiRequest(
                apiServiceClass = MyApi::class.java,
                context = activity,
                showLoading = false,
                requestAction = { api -> api.getPresignedUrl(originalName, mimeType) },
                listener = object : ApiResponseListener<ApiResponse<PresignedUrlResponse>> {
                    override fun onSuccess(response: ApiResponse<PresignedUrlResponse>) {
                        val presignedUrl = response.data.url
                        val storageKey = response.data.key

                        // Step 2: Upload bytes to S3 on background thread
                        Thread {
                            val uploaded = putToS3(activity, uri, presignedUrl, mimeType)
                            if (!uploaded) {
                                Log.e("AttachmentUpload", "S3 upload failed for $originalName")
                                activity.runOnUiThread {
                                    remaining--
                                    if (remaining == 0) onComplete(results)
                                }
                                return@Thread
                            }

                            // Step 3: Create attachment record
                            activity.runOnUiThread {
                                activity.makeApiRequest(
                                    apiServiceClass = MyApi::class.java,
                                    context = activity,
                                    showLoading = false,
                                    requestAction = { api ->
                                        api.createAttachment(
                                            CreateAttachmentRequest(
                                                entityId = entityId,
                                                entityType = entityType,
                                                originalName = originalName,
                                                storageKey = storageKey,
                                                mimeType = mimeType,
                                                fileSize = fileSize
                                            )
                                        )
                                    },
                                    listener = object : ApiResponseListener<ApiResponse<AttachmentRecord>> {
                                        override fun onSuccess(response: ApiResponse<AttachmentRecord>) {
                                            results.add(response.data)
                                            remaining--
                                            if (remaining == 0) onComplete(results)
                                        }

                                        override fun onFailure(errorMessage: ErrorResponse?) {
                                            Log.e("AttachmentUpload", "Create record failed: ${errorMessage?.error?.message}")
                                            remaining--
                                            if (remaining == 0) onComplete(results)
                                        }

                                        override fun onError(throwable: Throwable) {
                                            Log.e("AttachmentUpload", "Network error: ${throwable.message}")
                                            remaining--
                                            if (remaining == 0) onComplete(results)
                                        }
                                    }
                                )
                            }
                        }.start()
                    }

                    override fun onFailure(errorMessage: ErrorResponse?) {
                        Log.e("AttachmentUpload", "Presigned URL failed: ${errorMessage?.error?.message}")
                        remaining--
                        if (remaining == 0) onComplete(results)
                    }

                    override fun onError(throwable: Throwable) {
                        Log.e("AttachmentUpload", "Network error: ${throwable.message}")
                        remaining--
                        if (remaining == 0) onComplete(results)
                    }
                }
            )
        }
    }

    /** PUT file bytes directly to S3 presigned URL — no Auth header. */
    private fun putToS3(context: Context, uri: Uri, presignedUrl: String, mimeType: String): Boolean {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return false
            val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            val request = Request.Builder()
                .url(presignedUrl)
                .put(body)
                .build()
            val response = s3Client.newCall(request).execute()
            response.isSuccessful
        } catch (e: IOException) {
            Log.e("AttachmentUpload", "S3 PUT error: ${e.message}")
            false
        }
    }

    /** Returns Triple(originalName, mimeType, fileSize) or null if URI can't be resolved. */
    private fun getFileInfo(context: Context, uri: Uri): Triple<String, String, Long>? {
        return try {
            val mimeType = context.contentResolver.getType(uri)
                ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    MimeTypeMap.getFileExtensionFromUrl(uri.toString())
                ) ?: "image/jpeg"

            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
            val originalName = "img_${System.currentTimeMillis()}.$ext"

            val fileSize = context.contentResolver.openInputStream(uri)?.use { it.available().toLong() } ?: 0L

            Triple(originalName, mimeType, fileSize)
        } catch (e: Exception) {
            Log.e("AttachmentUpload", "getFileInfo error: ${e.message}")
            null
        }
    }
}
