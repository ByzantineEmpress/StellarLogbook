package com.stellarlogbook.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.stellarlogbook.app.data.local.entity.SystemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SystemDao {

    @Upsert
    suspend fun upsertAll(systems: List<SystemEntity>)

    @Query("SELECT * FROM systems WHERE name = :name LIMIT 1")
    suspend fun get(name: String): SystemEntity?

    @Query("SELECT * FROM systems WHERE name = :name LIMIT 1")
    fun observe(name: String): Flow<SystemEntity?>

    @Query("UPDATE systems SET bookmarked = :value WHERE name = :name")
    suspend fun setBookmarked(name: String, value: Boolean)

    @Query("UPDATE systems SET notes = :notes WHERE name = :name")
    suspend fun setNotes(name: String, notes: String?)

    @Query("SELECT * FROM systems WHERE name LIKE '%' || :query || '%' ORDER BY name LIMIT 200")
    fun search(query: String): Flow<List<SystemEntity>>

    @Query("SELECT * FROM systems WHERE lastVisitedAt IS NOT NULL ORDER BY lastVisitedAt DESC")
    fun allVisited(): Flow<List<SystemEntity>>

    @Query("SELECT COUNT(*) FROM systems WHERE lastVisitedAt IS NOT NULL")
    fun visitedCount(): Flow<Int>

    @Query("SELECT * FROM systems WHERE lastVisitedAt IS NOT NULL ORDER BY lastVisitedAt DESC LIMIT 1")
    fun latestVisit(): Flow<SystemEntity?>
}
