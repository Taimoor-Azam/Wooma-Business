package com.wooma.customs

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import com.wooma.data.network.ApiResponseListener
import com.wooma.data.network.MyApi
import com.wooma.data.network.makeApiRequest
import com.wooma.model.ApiResponse
import com.wooma.model.AttachmentRecord
import com.wooma.model.CreateAttachmentRequest
import com.wooma.model.ErrorResponse
import com.wooma.model.PresignedUrlResponse
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import android.app.ProgressDialog
import com.wooma.activities.BaseActivity
import java.io.IOException

object AttachmentUploadHelper {

    private val s3Client = OkHttpClient()

    /**
     * Gets a presigned URL, uploads the image to S3, and returns the storage key.
     * Use this when the caller handles the record creation (e.g. PATCH report cover image).
     */
    fun uploadForStorageKey(
        activity: Activity,
        uri: Uri,
        onSuccess: (storageKey: String) -> Unit,
        onError: (String) -> Unit
    ) {
        val fileInfo = getFileInfo(activity, uri)
        if (fileInfo == null) {
            onError("Could not read file")
            return
        }
        val (originalName, mimeType, _) = fileInfo

        activity.makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = activity,
            showLoading = true,
            requestAction = { api -> api.getPresignedUrl(originalName, mimeType) },
            listener = object : ApiResponseListener<ApiResponse<PresignedUrlResponse>> {
                override fun onSuccess(response: ApiResponse<PresignedUrlResponse>) {
                    val presignedUrl = response.data.url
                    val storageKey = response.data.key
                    Thread {
                        val uploaded = putToS3(activity, uri, presignedUrl, mimeType)
                        activity.runOnUiThread {
                            if (uploaded) onSuccess(storageKey)
                            else onError("Failed to upload image to storage")
                        }
                    }.start()
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    onError(errorMessage?.error?.message ?: "Failed to get upload URL")
                }

                override fun onError(throwable: Throwable) {
                    onError(throwable.message ?: "Network error")
                }
            }
        )
    }

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

        val progressBar = if (activity is BaseActivity) {
            ProgressDialog(activity).apply {
                setMessage("Uploading images...")
                setCancelable(false)
                show()
                activity.activeProgressDialog = this
            }
        } else null

        val results = mutableListOf<AttachmentRecord>()
        var remaining = imageUris.size

        for (uri in imageUris) {
            val fileInfo = getFileInfo(activity, uri)
            if (fileInfo == null) {
                remaining--
                if (remaining == 0) {
                    progressBar?.dismiss()
                    onComplete(results)
                }
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
                                    if (remaining == 0) {
                                        progressBar?.dismiss()
                                        onComplete(results)
                                    }
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
                                    listener = object :
                                        ApiResponseListener<ApiResponse<AttachmentRecord>> {
                                        override fun onSuccess(response: ApiResponse<AttachmentRecord>) {
                                            results.add(response.data)
                                            remaining--
                                            if (remaining == 0) {
                                                progressBar?.dismiss()
                                                onComplete(results)
                                            }
                                        }

                                        override fun onFailure(errorMessage: ErrorResponse?) {
                                            Log.e("AttachmentUpload", "Create record failed: ${errorMessage?.error?.message}")
                                            remaining--
                                            if (remaining == 0) {
                                                progressBar?.dismiss()
                                                onComplete(results)
                                            }
                                        }

                                        override fun onError(throwable: Throwable) {
                                            Log.e("AttachmentUpload", "Network error: ${throwable.message}")
                                            remaining--
                                            if (remaining == 0) {
                                                progressBar?.dismiss()
                                                onComplete(results)
                                            }
                                        }
                                    }
                                )
                            }
                        }.start()
                    }

                    override fun onFailure(errorMessage: ErrorResponse?) {
                        Log.e("AttachmentUpload", "Presigned URL failed: ${errorMessage?.error?.message}")
                        remaining--
                        if (remaining == 0) {
                            progressBar?.dismiss()
                            onComplete(results)
                        }
                    }

                    override fun onError(throwable: Throwable) {
                        Log.e("AttachmentUpload", "Network error: ${throwable.message}")
                        remaining--
                        if (remaining == 0) {
                            progressBar?.dismiss()
                            onComplete(results)
                        }
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
