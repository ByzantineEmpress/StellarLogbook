package com.stellarlogbook.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.stellarlogbook.app.data.local.entity.DiscoveryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiscoveryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<DiscoveryEntity>)

    @Query("SELECT * FROM discoveries ORDER BY at DESC")
    fun all(): Flow<List<DiscoveryEntity>>

    @Query("SELECT COUNT(*) FROM discoveries WHERE kind = :kind")
    fun countByKind(kind: String): Flow<Int>
}
