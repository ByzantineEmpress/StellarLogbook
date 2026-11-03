package com.stellarlogbook.app.data.sync

import com.stellarlogbook.app.data.local.ExplorerStore
import com.stellarlogbook.app.data.local.entity.JumpEntity
import com.stellarlogbook.app.data.local.entity.SyncCheckpointEntity
import com.stellarlogbook.app.data.remote.EdsmApi
import com.stellarlogbook.app.data.remote.EdsmLogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class SyncSummary(
    val pages: Int,
    val entries: Int,
    val lastMsgnum: Int? = null,
    val lastMessage: String? = null
)

/** Supplies commander + EDSM API key at sync time. */
interface SyncCredentials {
    data class Credentials(val commander: String, val apiKey: String)
    suspend fun get(): Credentials?
}

/**
 * Incremental EDSM flight-log sync: pages get-logs 100 entries at a time from
 * the stored checkpoint up to now, merges visits, and stores the checkpoint.
 *
 * EDSM contract (verified Phase 0): HTTP 200 with msgnum != 100 is an error
 * (203 = commander/API key not found). 429/503 = rate limit; callers back off.
 * Very wide date ranges can come back empty even when history exists, so the
 * first page falls back to the last 30 days before concluding "no logs".
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
        var lastMsgnum = MSG_OK
        var lastMessage: String? = null
        val end = Instant.now().plusSeconds(3600).atZone(ZoneOffset.UTC).format(EDS_FORMAT)

        while (pages < MAX_PAGES) {
            val start = cursor ?: EPOCH
            val res = api.logs(creds.commander, creds.apiKey, start, end)
            lastMsgnum = res.msgnum
            lastMessage = res.msg
            if (res.msgnum != MSG_OK) {
                return@withContext Result.failure(IOException("EDSM error ${res.msgnum}: ${res.msg}"))
            }
            val entries = res.logs.orEmpty()

            if (entries.isEmpty()) {
                // Fallback once, on the very first page: try the last 30 days.
                if (pages == 0 && cursor == null) {
                    val narrow = Instant.now().minus(30, ChronoUnit.DAYS)
                        .atZone(ZoneOffset.UTC).format(EDS_FORMAT)
                    val res2 = api.logs(creds.commander, creds.apiKey, narrow, end)
                    lastMsgnum = res2.msgnum
                    lastMessage = res2.msg
                    if (res2.msgnum != MSG_OK) {
                        return@withContext Result.failure(IOException("EDSM error ${res2.msgnum}: ${res2.msg}"))
                    }
                    val entries2 = res2.logs.orEmpty()
                    if (entries2.isEmpty()) break
                    val (newCursor, added) = processPage(entries2, cursor)
                    if (newCursor == null) break
                    cursor = newCursor
                    inserted += added
                    pages++
                    if (entries2.size < PAGE_SIZE) break
                    continue
                }
                break
            }

            val (newCursor, added) = processPage(entries, cursor)
            if (newCursor == null || newCursor == cursor) break // no forward progress
            cursor = newCursor
            inserted += added
            pages++
            if (entries.size < PAGE_SIZE) break
        }
        Result.success(SyncSummary(pages, inserted, lastMsgnum, lastMessage))
    }

    /** Merge one page; returns (newest cursor date, inserted count). */
    private suspend fun processPage(entries: List<EdsmLogEntry>, cursor: String?): Pair<String?, Int> {
        var newest = cursor
        var added = 0
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
            added++
        }
        if (newest != null && newest != cursor) {
            store.setCheckpoint(SyncCheckpointEntity(SOURCE, newest, Instant.now().toEpochMilli()))
        }
        return newest to added
    }

    private fun parseEdsmDate(date: String?): Instant? {
        val d = date ?: return null
        return try {
            Instant.from(EDS_FORMAT.parse(d))
        } catch (_: Exception) {
            try {
                Instant.parse(d)
            } catch (_: Exception) {
                null
            }
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
