package com.wooma.sync

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object SyncScheduler {

    private val CONNECTED_CONSTRAINT = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun scheduleImmediateSync(context: Context) {
        val wm = WorkManager.getInstance(context)
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(CONNECTED_CONSTRAINT)
            .build()

        // KEEP on a chained unique work drops *all* new requests while Sync or ImageUpload is still
        // enqueued/running — so adding rooms during a long upload never started another SyncWorker.
        // Sync alone with APPEND_OR_REPLACE: queue another sync after the current one, or start fresh
        // if nothing is running. Image uploads run from [SyncWorker] when a pass finishes (see there).
        wm.enqueueUniqueWork(
            "wooma_sync_immediate",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            syncRequest
        )
    }

    /** Called after a sync pass so new server IDs exist before uploads run. */
    fun schedulePendingUploads(context: Context) {
        val wm = WorkManager.getInstance(context)
        val uploadRequest = OneTimeWorkRequestBuilder<ImageUploadWorker>()
            .setConstraints(CONNECTED_CONSTRAINT)
            .build()
        wm.enqueueUniqueWork(
            "wooma_image_upload",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            uploadRequest
        )
    }

    fun schedulePeriodicSync(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "wooma_periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(CONNECTED_CONSTRAINT)
                .build()
        )
    }
}
