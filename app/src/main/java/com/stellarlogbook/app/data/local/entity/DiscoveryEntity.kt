package com.stellarlogbook.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A discovery credited to me: first discovery, first mapping, or codex entry.
 * Journal is the authoritative source (verified Phase 0: Odyssey journals do
 * NOT populate SAAScanComplete.Discovered/Mapped; Scan.WasDiscovered/WasMapped
 * and CodexEntry carry the truth).
 */
@Entity(
    tableName = "discoveries",
    indices = [Index("at"), Index(value = ["kind", "bodyName"], unique = true)]
)
data class DiscoveryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** DISCOVERY | MAPPING | CODEX */
    val kind: String,
    val bodyName: String,
    val systemName: String? = null,
    val systemId64: Long? = null,
    val at: Long,
    val detail: String? = null
) {
    companion object {
        const val KIND_DISCOVERY = "DISCOVERY"
        const val KIND_MAPPING = "MAPPING"
        const val KIND_CODEX = "CODEX"
    }
}
