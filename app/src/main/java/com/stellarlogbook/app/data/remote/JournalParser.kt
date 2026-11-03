package com.stellarlogbook.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Tolerant parser for the Elite Dangerous journal (one JSON event per line).
 * Unknown events and malformed lines are skipped by design: the game adds
 * fields over time and we only care about a small whitelist that answers the
 * four core questions.
 *
 * Phase 0 findings from real journals (Oct 2026, Odyssey):
 * - FSDJump / Location / CarrierJump carry StarSystem, SystemAddress, StarPos.
 * - Scan carries WasDiscovered / WasMapped (false = my first).
 * - SAAScanComplete has NO Discovered/Mapped arrays in Odyssey (legacy-only).
 * - CodexEntry carries Category/Region/System/IsNewEntry.
 */
class JournalParser(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
) {

    fun parseLine(line: String): ParsedEvent? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val e = json.decodeFromString<JournalLineDto>(trimmed)
            when (e.event) {
                EVENT_FSD_JUMP, EVENT_LOCATION, EVENT_CARRIER_JUMP -> {
                    val system = e.starSystem ?: return null
                    val pos = e.starPos
                    ParsedEvent.Visit(
                        system = system,
                        systemAddress = e.systemAddress,
                        x = pos?.getOrNull(0),
                        y = pos?.getOrNull(1),
                        z = pos?.getOrNull(2),
                        at = e.timestamp?.let(::parseInstant) ?: return null,
                        jumpDistanceLy = e.jumpDist
                    )
                }
                EVENT_SCAN -> {
                    val body = e.bodyName ?: return null
                    ParsedEvent.Scan(
                        bodyName = body,
                        systemName = e.starSystem,
                        systemAddress = e.systemAddress,
                        bodyId = e.bodyId,
                        wasDiscovered = e.wasDiscovered,
                        wasMapped = e.wasMapped,
                        type = e.planetClass ?: e.starType ?: "Unknown",
                        at = e.timestamp?.let(::parseInstant) ?: return null
                    )
                }
                EVENT_CODEX -> {
                    val name = e.nameLocalised ?: e.name ?: return null
                    ParsedEvent.Codex(
                        entryId = e.entryId,
                        name = name,
                        category = e.categoryLocalised ?: e.category ?: "Unknown",
                        systemName = e.starSystem,
                        systemAddress = e.systemAddress,
                        bodyId = e.bodyId,
                        region = e.regionLocalised ?: e.region,
                        isNewEntry = e.isNewEntry == true,
                        at = e.timestamp?.let(::parseInstant) ?: return null
                    )
                }
                EVENT_SAA_SCAN_COMPLETE -> ParsedEvent.SaaScan(
                    bodyName = e.bodyName ?: return null,
                    at = e.timestamp?.let(::parseInstant) ?: return null
                )
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseInstant(ts: String): Instant? = try {
        Instant.parse(ts)
    } catch (_: Exception) {
        null
    }

    companion object {
        const val EVENT_FSD_JUMP = "FSDJump"
        const val EVENT_LOCATION = "Location"
        const val EVENT_CARRIER_JUMP = "CarrierJump"
        const val EVENT_SCAN = "Scan"
        const val EVENT_CODEX = "CodexEntry"
        const val EVENT_SAA_SCAN_COMPLETE = "SAAScanComplete"
    }
}

sealed interface ParsedEvent {

    data class Visit(
        val system: String,
        val systemAddress: Long,
        val x: Double?,
        val y: Double?,
        val z: Double?,
        val at: Instant,
        val jumpDistanceLy: Double?
    ) : ParsedEvent

    data class Scan(
        val bodyName: String,
        val systemName: String?,
        val systemAddress: Long,
        val bodyId: Long?,
        val wasDiscovered: Boolean?,
        val wasMapped: Boolean?,
        val type: String,
        val at: Instant
    ) : ParsedEvent

    data class Codex(
        val entryId: Long,
        val name: String,
        val category: String,
        val systemName: String?,
        val systemAddress: Long,
        val bodyId: Long?,
        val region: String?,
        val isNewEntry: Boolean,
        val at: Instant
    ) : ParsedEvent

    data class SaaScan(val bodyName: String, val at: Instant) : ParsedEvent
}

/** One journal line; unknown keys ignored. */
@Serializable
data class JournalLineDto(
    val event: String? = null,
    val timestamp: String? = null,
    @SerialName("StarSystem") val starSystem: String? = null,
    @SerialName("SystemAddress") val systemAddress: Long = 0,
    @SerialName("StarPos") val starPos: List<Double>? = null,
    @SerialName("JumpDist") val jumpDist: Double? = null,
    @SerialName("BodyName") val bodyName: String? = null,
    @SerialName("BodyID") val bodyId: Long? = null,
    @SerialName("WasDiscovered") val wasDiscovered: Boolean? = null,
    @SerialName("WasMapped") val wasMapped: Boolean? = null,
    @SerialName("PlanetClass") val planetClass: String? = null,
    @SerialName("StarType") val starType: String? = null,
    @SerialName("EntryID") val entryId: Long = 0,
    @SerialName("Name") val name: String? = null,
    @SerialName("Name_Localised") val nameLocalised: String? = null,
    @SerialName("Category") val category: String? = null,
    @SerialName("Category_Localised") val categoryLocalised: String? = null,
    @SerialName("Region") val region: String? = null,
    @SerialName("Region_Localised") val regionLocalised: String? = null,
    @SerialName("IsNewEntry") val isNewEntry: Boolean? = null
)
