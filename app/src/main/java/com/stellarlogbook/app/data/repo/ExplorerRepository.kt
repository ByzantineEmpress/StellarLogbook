package com.stellarlogbook.app.data.repo

import com.stellarlogbook.app.data.local.AppDatabase
import com.stellarlogbook.app.data.local.entity.BodyEntity
import com.stellarlogbook.app.data.local.entity.DiscoveryEntity
import com.stellarlogbook.app.data.local.entity.SystemEntity
import com.stellarlogbook.app.data.prefs.UserPrefs
import kotlinx.coroutines.flow.Flow

/** Facade over Room + prefs for the UI. Remote calls belong to the sync engine. */
class ExplorerRepository(
    private val db: AppDatabase,
    private val prefs: UserPrefs
) {

    fun visitedSystems(query: String = ""): Flow<List<SystemEntity>> =
        if (query.isBlank()) db.systemDao().allVisited() else db.systemDao().search(query.trim())

    fun visitedCount(): Flow<Int> = db.systemDao().visitedCount()
    fun latestVisit(): Flow<SystemEntity?> = db.systemDao().latestVisit()
    fun observeSystem(name: String): Flow<SystemEntity?> = db.systemDao().observe(name)
    fun observeBodies(systemName: String): Flow<List<BodyEntity>> = db.bodyDao().bodiesFor(systemName)
    fun discoveries(): Flow<List<DiscoveryEntity>> = db.discoveryDao().all()
    fun discoveryCount(): Flow<Int> = db.discoveryDao().countByKind(DiscoveryEntity.KIND_DISCOVERY)
    fun codexCount(): Flow<Int> = db.discoveryDao().countByKind(DiscoveryEntity.KIND_CODEX)
    fun lastSyncAt(): Flow<Long> = prefs.lastSyncAt
    fun commanderName(): Flow<String?> = prefs.commanderName

    suspend fun setCredentials(commander: String, apiKey: String) =
        prefs.setCredentials(commander, apiKey)

    suspend fun setBookmarked(name: String, value: Boolean) =
        db.systemDao().setBookmarked(name, value)

    suspend fun setNotes(name: String, notes: String?) = db.systemDao().setNotes(name, notes)

    /** Persist enriched facts about a system (coords, id64, known-to-community). */
    suspend fun enrichSystem(system: SystemEntity) = db.systemDao().upsertAll(listOf(system))

    suspend fun markSynced() = prefs.markSynced(java.time.Instant.now().toEpochMilli())
}
