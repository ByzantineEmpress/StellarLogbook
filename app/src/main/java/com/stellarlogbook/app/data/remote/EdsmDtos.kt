package com.stellarlogbook.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Shapes verified against live EDSM responses in Phase 0 (2026-11-03). */

@Serializable
data class SystemInfoDto(
    val name: String? = null,
    val id: Long? = null,
    val id64: Long? = null,
    val coords: CoordsDto? = null,
    @SerialName("coordsLocked") val coordsLocked: Boolean = false,
    @SerialName("requirePermit") val requirePermit: Boolean = false,
    @SerialName("permitName") val permitName: String? = null,
    val information: SystemInformationDto? = null,
    @SerialName("primaryStar") val primaryStar: PrimaryStarDto? = null
)

@Serializable
data class CoordsDto(
    val x: Double? = null,
    val y: Double? = null,
    val z: Double? = null
)

@Serializable
data class SystemInformationDto(
    val allegiance: String? = null,
    val government: String? = null,
    val faction: String? = null,
    @SerialName("factionState") val factionState: String? = null,
    val population: Long? = null,
    val security: String? = null,
    val economy: String? = null,
    @SerialName("secondEconomy") val secondEconomy: String? = null,
    val reserve: String? = null,
    /** First-discovery metadata for the system when known to EDSM. */
    val discovered: String? = null,
    @SerialName("discoveredBy") val discoveredBy: String? = null
)

@Serializable
data class PrimaryStarDto(
    val type: String? = null,
    val name: String? = null,
    @SerialName("isScoopable") val isScoopable: Boolean? = null
)

@Serializable
data class SphereSystemDto(
    val distance: Double? = null,
    @SerialName("bodyCount") val bodyCount: Int? = null,
    val name: String? = null,
    val id: Long? = null,
    val id64: Long? = null,
    val coords: CoordsDto? = null,
    @SerialName("coordsLocked") val coordsLocked: Boolean? = null,
    @SerialName("primaryStar") val primaryStar: PrimaryStarDto? = null
)

/** api-logs-v1/get-logs: HTTP 200 + msgnum != 100 means error (e.g. 203 = bad commander/key). */
@Serializable
data class EdsmLogsResponse(
    val msgnum: Int,
    val msg: String? = null,
    val logs: List<EdsmLogEntry>? = null
)

@Serializable
data class EdsmLogEntry(
    val system: String? = null,
    val date: String? = null,
    val coordinates: CoordsDto? = null,
    @SerialName("firstDiscovery") val firstDiscovery: Boolean? = null,
    @SerialName("isDocked") val isDocked: Boolean? = null
)

@Serializable
data class EdsmPositionResponse(
    val msgnum: Int,
    val msg: String? = null,
    val system: String? = null,
    val coordinates: CoordsDto? = null
)
