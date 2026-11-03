package com.stellarlogbook.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One system-to-system jump; the trail for the Phase 3 map. Dedupes on (to, at). */
@Entity(
    tableName = "jumps",
    indices = [Index("at"), Index(value = ["toSystemName", "at"], unique = true)]
)
data class JumpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromSystemName: String? = null,
    val toSystemName: String,
    val at: Long,
    val distanceLy: Double? = null
)
