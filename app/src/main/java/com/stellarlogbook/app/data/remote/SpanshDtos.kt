package com.stellarlogbook.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Shapes verified against live Spansh /api/system/<id64> in Phase 0. */

@Serializable
data class SpanshSystemResponse(
    val record: SpanshSystemRecord? = null
)

@Serializable
data class SpanshSystemRecord(
    val name: String? = null,
    val id64: Long? = null,
    val allegiance: String? = null,
    val bodies: List<SpanshBody>? = null
)

@Serializable
data class SpanshBody(
    val id: Long? = null,
    val id64: Long? = null,
    val name: String? = null,
    val type: String? = null,
    val subtype: String? = null,
    @SerialName("distance_to_arrival") val distanceToArrival: Double? = null,
    @SerialName("is_main_star") val isMainStar: Boolean? = null,
    @SerialName("estimated_scan_value") val estimatedScanValue: Long? = null,
    @SerialName("estimated_mapping_value") val estimatedMappingValue: Long? = null
)
