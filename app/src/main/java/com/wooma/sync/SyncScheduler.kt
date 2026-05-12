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
        val uploadRequest = OneTimeWorkRequestBuilder<ImageUploadWorker>()
            .setConstraints(CONNECTED_CONSTRAINT)
            .build()

        // Run in sequence to avoid races where image upload starts before entity CREATE assigns server IDs.
        // KEEP avoids cancelling an in-flight sync; REPLACE could abort mid–rooms/bulk and leave rows
        // PENDING so the replacement worker POSTs again.
        wm.beginUniqueWork(
            "wooma_sync_pipeline",
            ExistingWorkPolicy.KEEP,
            syncRequest
        ).then(uploadRequest).enqueue()
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
