package com.stellarlogbook.app.testutil

import com.stellarlogbook.app.data.local.ExplorerStore
import com.stellarlogbook.app.data.local.entity.BodyEntity
import com.stellarlogbook.app.data.local.entity.DiscoveryEntity
import com.stellarlogbook.app.data.local.entity.JumpEntity
import com.stellarlogbook.app.data.local.entity.SyncCheckpointEntity
import com.stellarlogbook.app.data.local.entity.SystemEntity

/**
 * In-memory [ExplorerStore] mirroring the Room implementation's semantics:
 * visit merge, jump dedupe on (to, at), discovery dedupe on (kind, bodyName).
 */
class FakeExplorerStore : ExplorerStore {

    val systems = mutableMapOf<String, SystemEntity>()
    val bodies = mutableMapOf<Pair<String, Long>, BodyEntity>()
    val jumps = mutableListOf<JumpEntity>()
    val discoveries = mutableListOf<DiscoveryEntity>()
    val checkpoints = mutableMapOf<String, SyncCheckpointEntity>()

    override suspend fun getSystem(name: String): SystemEntity? = systems[name]

    override suspend fun mergeVisit(
        name: String,
        id64: Long?,
        x: Double?,
        y: Double?,
        z: Double?,
        at: Long,
        knownToCommunity: Boolean
    ): SystemEntity {
        val existing = systems[name]
        val merged = if (existing == null) {
            SystemEntity(
                name = name,
                id64 = id64,
                x = x,
                y = y,
                z = z,
                knownToCommunity = knownToCommunity,
                firstVisitedAt = at,
                lastVisitedAt = at,
                visitCount = 1
            )
        } else {
            existing.copy(
                id64 = existing.id64 ?: id64,
                x = existing.x ?: x,
                y = existing.y ?: y,
                z = existing.z ?: z,
                knownToCommunity = existing.knownToCommunity || knownToCommunity,
                firstVisitedAt = minOf(existing.firstVisitedAt ?: at, at),
                lastVisitedAt = maxOf(existing.lastVisitedAt ?: at, at),
                visitCount = existing.visitCount + 1
            )
        }
        systems[name] = merged
        return merged
    }

    override suspend fun upsertSystems(systems: List<SystemEntity>) {
        systems.forEach { this.systems[it.name] = it }
    }

    override suspend fun insertJump(jump: JumpEntity) {
        if (jumps.none { it.toSystemName == jump.toSystemName && it.at == jump.at }) {
            jumps += jump
        }
    }

    override suspend fun getBody(systemName: String, bodyId: Long): BodyEntity? =
        bodies[systemName to bodyId]

    override suspend fun upsertBodies(bodies: List<BodyEntity>) {
        bodies.forEach { this.bodies[it.systemName to it.bodyId] = it }
    }

    override suspend fun insertDiscoveries(items: List<DiscoveryEntity>) {
        items.forEach { item ->
            if (discoveries.none { it.kind == item.kind && it.bodyName == item.bodyName }) {
                discoveries += item
            }
        }
    }

    override suspend fun getCheckpoint(source: String): SyncCheckpointEntity? = checkpoints[source]

    override suspend fun setCheckpoint(checkpoint: SyncCheckpointEntity) {
        checkpoints[checkpoint.source] = checkpoint
    }

    override suspend fun runInTransaction(block: suspend () -> Unit) = block()
}
