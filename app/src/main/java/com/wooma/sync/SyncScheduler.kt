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
        wm.enqueueUniqueWork(
            "wooma_sync",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(CONNECTED_CONSTRAINT)
                .build()
        )
        wm.enqueueUniqueWork(
            "wooma_image_upload",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ImageUploadWorker>()
                .setConstraints(CONNECTED_CONSTRAINT)
                .build()
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
