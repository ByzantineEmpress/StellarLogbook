package com.stellarlogbook.app.data.local.entity

import androidx.room.Entity

/** A body (star or planet) inside a system, with my discovery status. */
@Entity(tableName = "bodies", primaryKeys = ["systemName", "bodyId"])
data class BodyEntity(
    val systemName: String,
    val bodyId: Long,
    val bodyName: String,
    /** PlanetClass or StarType, e.g. "High metal content body", "M". */
    val type: String,
    val myFirstDiscovery: Boolean = false,
    val myFirstMapping: Boolean = false,
    val discoveredAt: Long? = null,
    val estValue: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
