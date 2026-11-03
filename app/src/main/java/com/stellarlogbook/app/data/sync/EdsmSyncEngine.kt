package com.stellarlogbook.app.data.sync

import com.stellarlogbook.app.data.local.ExplorerStore
import com.stellarlogbook.app.data.local.entity.JumpEntity
import com.stellarlogbook.app.data.local.entity.SyncCheckpointEntity
import com.stellarlogbook.app.data.remote.EdsmApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class SyncSummary(val pages: Int, val entries: Int)

/** Supplies commander + EDSM API key at sync time. */
interface SyncCredentials {
    data class Credentials(val commander: String, val apiKey: String)
    suspend fun get(): Credentials?
}

/**
 * Incremental EDSM flight-log sync: pages get-logs 100 entries at a time from
 * the stored checkpoint up to now, merges visits (earliest/latest/count), and
 * stores the checkpoint.
 *
 * EDSM contract (verified Phase 0): HTTP 200 with msgnum != 100 is an error
 * (203 = commander/API key not found). 429/503 = rate limit; callers back off.
 */
class EdsmSyncEngine(
    private val api: EdsmApi,
    private val credentials: SyncCredentials,
    private val store: ExplorerStore
) {

    suspend fun sync(): Result<SyncSummary> = withContext(Dispatchers.IO) {
        val creds = credentials.get()
            ?: return@withContext Result.failure(IllegalStateException("Commander name / API key not set"))

        var cursor: String? = store.getCheckpoint(SOURCE)?.cursor
        var pages = 0
        var inserted = 0
        val now = Instant.now().plusSeconds(3600).atZone(ZoneOffset.UTC).format(EDS_FORMAT)

        while (pages < MAX_PAGES) {
            val start = cursor ?: EPOCH
            val res = api.logs(creds.commander, creds.apiKey, start, now)
            if (res.msgnum != MSG_OK) {
                return@withContext Result.failure(IOException("EDSM error ${res.msgnum}: ${res.msg}"))
            }
            val entries = res.logs.orEmpty()
            if (entries.isEmpty()) break

            var newest = cursor
            for (e in entries) {
                val name = e.system ?: continue
                val at = parseEdsmDate(e.date)?.toEpochMilli() ?: continue
                store.mergeVisit(
                    name = name,
                    id64 = null,
                    x = e.coordinates?.x,
                    y = e.coordinates?.y,
                    z = e.coordinates?.z,
                    at = at,
                    knownToCommunity = true
                )
                store.insertJump(JumpEntity(toSystemName = name, at = at))
                val d = e.date
                if (d != null && (newest == null || d > newest)) newest = d
                inserted++
            }
            if (newest == cursor) break // no forward progress: stop, don't loop
            cursor = newest
            store.setCheckpoint(SyncCheckpointEntity(SOURCE, cursor, Instant.now().toEpochMilli()))
            pages++
            if (entries.size < PAGE_SIZE) break
        }
        Result.success(SyncSummary(pages, inserted))
    }

    private fun parseEdsmDate(date: String): Instant? = try {
        Instant.from(EDS_FORMAT.parse(date))
    } catch (_: Exception) {
        try {
            Instant.parse(date)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val SOURCE = "edsm_logs"
        const val MSG_OK = 100
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 2000
        const val EPOCH = "2014-12-01 00:00:00"
        private val EDS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)
    }
}
