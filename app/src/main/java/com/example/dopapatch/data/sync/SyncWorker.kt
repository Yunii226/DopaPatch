package com.example.dopapatch.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.dopapatch.DopaPatchApp
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as DopaPatchApp).container
        return container.syncManager.sync().fold(
            onSuccess = { container.rescheduleAlarms(); Result.success() }, // remote changes may add alarm tasks
            onFailure = { Result.retry() },
        )
    }

    companion object {
        private val onlyOnline = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Run now (app open) + keep a periodic safety net. Both no-op when signed out. */
        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.enqueueUniqueWork(
                "sync-now", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(onlyOnline).build(),
            )
            wm.enqueueUniquePeriodicWork(
                "sync-periodic", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(onlyOnline).build(),
            )
        }
    }
}
