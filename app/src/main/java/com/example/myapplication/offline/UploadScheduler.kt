package com.example.myapplication.offline

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object UploadScheduler {

    fun enqueue(context: Context, pendingId: Long) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<UploadRouteWorker>()
            .setInputData(workDataOf("pendingId" to pendingId))
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                15,
                TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "upload_route_$pendingId",
                ExistingWorkPolicy.REPLACE,
                request
            )
    }
}
