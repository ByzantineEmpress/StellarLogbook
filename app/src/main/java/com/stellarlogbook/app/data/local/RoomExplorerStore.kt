package com.stellarlogbook.app.data.local

import androidx.room.withTransaction
import com.stellarlogbook.app.data.local.entity.BodyEntity
import com.stellarlogbook.app.data.local.entity.DiscoveryEntity
import com.stellarlogbook.app.data.local.entity.JumpEntity
import com.stellarlogbook.app.data.local.entity.SyncCheckpointEntity
import com.stellarlogbook.app.data.local.entity.SystemEntity

/** Room-backed [ExplorerStore]. */
class RoomExplorerStore(private val db: AppDatabase) : ExplorerStore {

    override suspend fun getSystem(name: String): SystemEntity? = db.systemDao().get(name)

    override suspend fun mergeVisit(
        name: String,
        id64: Long?,
        x: Double?,
        y: Double?,
        z: Double?,
        at: Long,
        knownToCommunity: Boolean
    ): SystemEntity {
        val existing = db.systemDao().get(name)
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
        db.systemDao().upsertAll(listOf(merged))
        return merged
    }

    override suspend fun upsertSystems(systems: List<SystemEntity>) =
        db.systemDao().upsertAll(systems)

    override suspend fun insertJump(jump: JumpEntity) = db.jumpDao().insertAll(listOf(jump))

    override suspend fun getBody(systemName: String, bodyId: Long): BodyEntity? =
        db.bodyDao().get(systemName, bodyId)

    override suspend fun upsertBodies(bodies: List<BodyEntity>) = db.bodyDao().upsertAll(bodies)

    override suspend fun insertDiscoveries(items: List<DiscoveryEntity>) =
        db.discoveryDao().insertAll(items)

    override suspend fun getCheckpoint(source: String): SyncCheckpointEntity? =
        db.syncCheckpointDao().get(source)

    override suspend fun setCheckpoint(checkpoint: SyncCheckpointEntity) =
        db.syncCheckpointDao().set(checkpoint)

    override suspend fun runInTransaction(block: suspend () -> Unit) =
        db.withTransaction { block() }
}
