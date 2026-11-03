package com.stellarlogbook.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.stellarlogbook.app.data.local.entity.BodyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyDao {

    @Upsert
    suspend fun upsertAll(bodies: List<BodyEntity>)

    @Query("SELECT * FROM bodies WHERE systemName = :systemName AND bodyId = :bodyId LIMIT 1")
    suspend fun get(systemName: String, bodyId: Long): BodyEntity?

    @Query("SELECT * FROM bodies WHERE myFirstDiscovery = 1 OR myFirstMapping = 1 ORDER BY discoveredAt DESC")
    fun myDiscoveries(): Flow<List<BodyEntity>>

    @Query("SELECT COUNT(*) FROM bodies WHERE myFirstDiscovery = 1")
    fun firstDiscoveryCount(): Flow<Int>
}
