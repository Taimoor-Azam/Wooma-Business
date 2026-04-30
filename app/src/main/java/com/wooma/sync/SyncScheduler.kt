package com.wooma.sync

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object SyncScheduler {

    private val CONNECTED_CONSTRAINT = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun scheduleImmediateSync(context: Context) {
        WorkManager.getInstance(context)
            .beginUniqueWork(
                "wooma_sync",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(CONNECTED_CONSTRAINT)
                    .build()
            )
            .then(
                OneTimeWorkRequestBuilder<ImageUploadWorker>()
                    .setConstraints(CONNECTED_CONSTRAINT)
                    .build()
            )
            .enqueue()
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
