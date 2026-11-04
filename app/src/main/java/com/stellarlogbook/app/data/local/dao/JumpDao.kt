package com.stellarlogbook.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.stellarlogbook.app.data.local.entity.JumpEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JumpDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(jumps: List<JumpEntity>)

    @Query("SELECT * FROM jumps ORDER BY at DESC LIMIT 1")
    fun latest(): Flow<JumpEntity?>

    @Query("SELECT * FROM jumps ORDER BY at ASC LIMIT :limit")
    fun recent(limit: Int): Flow<List<JumpEntity>>
}
