package com.stellarlogbook.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A star system.
 *
 * PK is the system NAME rather than id64: the EDSM flight-logs API identifies
 * systems by name (no id64), while journals provide id64. Names are unique in
 * Elite. `id64` is kept as a nullable enrichment column; Phase 1 adds a
 * name -> id64 enrichment pass via the EDSM systems batch endpoint.
 */
@Entity(tableName = "systems", indices = [Index(value = ["lastVisitedAt"])])
data class SystemEntity(
    @PrimaryKey val name: String,
    val id64: Long? = null,
    val x: Double? = null,
    val y: Double? = null,
    val z: Double? = null,
    val knownToCommunity: Boolean = false,
    val firstVisitedAt: Long? = null,
    val lastVisitedAt: Long? = null,
    val visitCount: Int = 0,
    val bookmarked: Boolean = false,
    val notes: String? = null
)
