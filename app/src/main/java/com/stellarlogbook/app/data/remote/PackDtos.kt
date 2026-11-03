package com.stellarlogbook.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The pack manifest attached to the GitHub Release (v2). */

@Serializable
data class PackManifestDto(
    @SerialName("builtAt") val builtAt: String? = null,
    val version: Int? = null,
    @SerialName("recordCount") val recordCount: Int? = null,
    @SerialName("cellSizeLy") val cellSizeLy: Int? = null,
    val full: PackFileDto? = null,
    val slices: List<PackSliceDto> = emptyList()
)

@Serializable
data class PackFileDto(
    val file: String? = null,
    val bytes: Long? = null
)

@Serializable
data class PackSliceDto(
    val file: String? = null,
    val bytes: Long? = null,
    val count: Int? = null,
    val minX: Float? = null,
    val maxX: Float? = null
)
