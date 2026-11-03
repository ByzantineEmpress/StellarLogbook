package com.stellarlogbook.app.data.sync

import com.stellarlogbook.app.data.prefs.UserPrefs
import com.stellarlogbook.app.data.remote.EdsmApi
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Read-only EDSM connectivity check for the Settings screen: shows exactly
 * what EDSM returns for the current credentials, so a broken sync can be
 * diagnosed without a computer.
 */
class EdsmDiagnostics(
    private val api: EdsmApi,
    private val prefs: UserPrefs
) {

    data class Report(
        val configured: Boolean,
        val positionMsgnum: Int? = null,
        val positionMsg: String? = null,
        val positionSystem: String? = null,
        val logsMsgnum: Int? = null,
        val logsMsg: String? = null,
        val logsCount: Int? = null,
        val newestEntry: String? = null
    )

    suspend fun run(): Report {
        val commander = prefs.commanderName.first()?.takeIf { it.isNotBlank() }
        val key = prefs.apiKey()?.takeIf { it.isNotBlank() }
        if (commander == null || key == null) return Report(configured = false)

        val pos = try {
            api.position(commander, key)
        } catch (e: Exception) {
            return Report(
                configured = true,
                positionMsgnum = -1,
                positionMsg = e.message ?: "network error",
                logsMsgnum = -1,
                logsMsg = e.message ?: "network error"
            )
        }

        val end = Instant.now().plusSeconds(3600).atZone(ZoneOffset.UTC).format(FORMAT)
        val narrow = Instant.now().minus(30, ChronoUnit.DAYS).atZone(ZoneOffset.UTC).format(FORMAT)
        val logs = try {
            api.logs(commander, key, narrow, end)
        } catch (e: Exception) {
            return Report(
                configured = true,
                positionMsgnum = pos.msgnum,
                positionMsg = pos.msg,
                positionSystem = pos.system,
                logsMsgnum = -1,
                logsMsg = e.message ?: "network error"
            )
        }

        val entries = logs.logs.orEmpty()
        return Report(
            configured = true,
            positionMsgnum = pos.msgnum,
            positionMsg = pos.msg,
            positionSystem = pos.system,
            logsMsgnum = logs.msgnum,
            logsMsg = logs.msg,
            logsCount = entries.size,
            newestEntry = entries.firstOrNull()?.let { "${it.system} @ ${it.date}" }
        )
    }

    companion object {
        private val FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)
    }
}
