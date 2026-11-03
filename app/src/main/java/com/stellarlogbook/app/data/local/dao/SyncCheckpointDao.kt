package com.stellarlogbook.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.stellarlogbook.app.data.local.entity.SyncCheckpointEntity

@Dao
interface SyncCheckpointDao {

    @Upsert
    suspend fun set(checkpoint: SyncCheckpointEntity)

    @Query("SELECT * FROM sync_checkpoints WHERE source = :source LIMIT 1")
    suspend fun get(source: String): SyncCheckpointEntity?
}
