package com.stellarlogbook.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Per-source sync cursor, e.g. source = "edsm_logs", cursor = last synced datetime. */
@Entity(tableName = "sync_checkpoints")
data class SyncCheckpointEntity(
    @PrimaryKey val source: String,
    val cursor: String? = null,
    val at: Long = 0L
)
