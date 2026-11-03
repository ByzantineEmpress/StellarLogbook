package com.stellarlogbook.app.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Keeps the periodic EDSM sync in line with the user's preferences. */
object SyncScheduler {

    const val UNIQUE = "edsm_sync_periodic"
    const val OFF = 0

    fun ensure(context: Context, intervalHours: Int, wifiOnly: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (intervalHours <= OFF) {
            workManager.cancelUniqueWork(UNIQUE)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<EdsmSyncWorker>(intervalHours.toLong(), TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
