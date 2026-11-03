package com.stellarlogbook.app.data.local

import com.stellarlogbook.app.data.local.entity.BodyEntity
import com.stellarlogbook.app.data.local.entity.DiscoveryEntity
import com.stellarlogbook.app.data.local.entity.JumpEntity
import com.stellarlogbook.app.data.local.entity.SyncCheckpointEntity
import com.stellarlogbook.app.data.local.entity.SystemEntity

/**
 * Persistence contract used by the sync and import engines. Room-backed in
 * production, in-memory fake in unit tests. The UI keeps reading DAO flows
 * directly via [AppDatabase].
 */
interface ExplorerStore {

    suspend fun getSystem(name: String): SystemEntity?

    /** Merge a visit: earliest first-visit, latest last-visit, incrementing count. */
    suspend fun mergeVisit(
        name: String,
        id64: Long?,
        x: Double?,
        y: Double?,
        z: Double?,
        at: Long,
        knownToCommunity: Boolean
    ): SystemEntity

    suspend fun upsertSystems(systems: List<SystemEntity>)

    suspend fun insertJump(jump: JumpEntity)

    suspend fun getBody(systemName: String, bodyId: Long): BodyEntity?
    suspend fun upsertBodies(bodies: List<BodyEntity>)

    suspend fun insertDiscoveries(items: List<DiscoveryEntity>)

    suspend fun getCheckpoint(source: String): SyncCheckpointEntity?
    suspend fun setCheckpoint(checkpoint: SyncCheckpointEntity)

    suspend fun runInTransaction(block: suspend () -> Unit)
}
