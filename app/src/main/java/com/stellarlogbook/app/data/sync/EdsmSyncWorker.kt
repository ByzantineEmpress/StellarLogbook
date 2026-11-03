package com.stellarlogbook.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.stellarlogbook.app.core.container
import kotlinx.coroutines.delay
import java.time.Instant

/** Background EDSM sync. Retries with linear backoff; scheduling is set up in SyncScheduler. */
class EdsmSyncWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.container()
        return container.syncEngine.sync().fold(
            onSuccess = { summary ->
                container.userPrefs.markSynced(Instant.now().toEpochMilli())
                Result.success(workDataOf("entries" to summary.entries, "pages" to summary.pages))
            },
            onFailure = { e ->
                if (runAttemptCount < MAX_ATTEMPTS) {
                    delay(BACKOFF_MS * runAttemptCount)
                    Result.retry()
                } else {
                    Result.failure(workDataOf("error" to (e.message ?: "unknown error")))
                }
            }
        )
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        const val BACKOFF_MS = 30_000L
        const val UNIQUE_NAME = "edsm_sync"
    }
}
