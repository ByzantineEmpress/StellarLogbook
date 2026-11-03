package com.stellarlogbook.app.data.sync

import com.stellarlogbook.app.data.local.ExplorerStore
import com.stellarlogbook.app.data.local.entity.BodyEntity
import com.stellarlogbook.app.data.local.entity.DiscoveryEntity
import com.stellarlogbook.app.data.local.entity.JumpEntity
import com.stellarlogbook.app.data.remote.JournalParser
import com.stellarlogbook.app.data.remote.ParsedEvent

/**
 * Turns journal lines into the local DB. Idempotent: visits merge, bodies OR
 * their flags, discoveries dedupe on a (kind, bodyName) unique index.
 *
 * Phase 0 fact baked in: discovery truth lives in `Scan.WasDiscovered/WasMapped`
 * (false = mine) and `CodexEntry.IsNewEntry`; Odyssey's SAAScanComplete carries
 * no discovery arrays.
 */
class JournalImporter(
    private val parser: JournalParser,
    private val store: ExplorerStore
) {

    data class Summary(
        val lines: Long,
        val parsed: Int,
        val visits: Int,
        val scans: Int,
        val firstDiscoveries: Int,
        val firstMappings: Int,
        val codex: Int
    ) {
        operator fun plus(other: Summary) = Summary(
            lines = lines + other.lines,
            parsed = parsed + other.parsed,
            visits = visits + other.visits,
            scans = scans + other.scans,
            firstDiscoveries = firstDiscoveries + other.firstDiscoveries,
            firstMappings = firstMappings + other.firstMappings,
            codex = codex + other.codex
        )
    }

    suspend fun importLines(lines: Sequence<String>): Summary {
        var linesSeen = 0L
        var parsed = 0
        var visits = 0
        var scans = 0
        var firstDiscoveries = 0
        var firstMappings = 0
        var codex = 0

        store.runInTransaction {
            for (line in lines) {
                linesSeen++
                val event = parser.parseLine(line) ?: continue
                parsed++
                when (event) {
                    is ParsedEvent.Visit -> {
                        store.mergeVisit(
                            name = event.system,
                            id64 = event.systemAddress.takeIf { it != 0L },
                            x = event.x,
                            y = event.y,
                            z = event.z,
                            at = event.at.toEpochMilli(),
                            knownToCommunity = false
                        )
                        store.insertJump(
                            JumpEntity(
                                toSystemName = event.system,
                                at = event.at.toEpochMilli(),
                                distanceLy = event.jumpDistanceLy
                            )
                        )
                        visits++
                    }

                    is ParsedEvent.Scan -> {
                        scans++
                        val firstDiscovery = event.wasDiscovered == false
                        val firstMapping = event.wasMapped == false
                        if (firstDiscovery) firstDiscoveries++
                        if (firstMapping) firstMappings++

                        if (event.systemName != null && event.bodyId != null) {
                            val existing = store.getBody(event.systemName, event.bodyId)
                            val body = if (existing == null) {
                                BodyEntity(
                                    systemName = event.systemName,
                                    bodyId = event.bodyId,
                                    bodyName = event.bodyName,
                                    type = event.type,
                                    myFirstDiscovery = firstDiscovery,
                                    myFirstMapping = firstMapping,
                                    discoveredAt = event.at.toEpochMilli()
                                )
                            } else {
                                existing.copy(
                                    bodyName = event.bodyName,
                                    type = event.type,
                                    myFirstDiscovery = existing.myFirstDiscovery || firstDiscovery,
                                    myFirstMapping = existing.myFirstMapping || firstMapping,
                                    discoveredAt = minOf(existing.discoveredAt ?: event.at.toEpochMilli(), event.at.toEpochMilli())
                                )
                            }
                            store.upsertBodies(listOf(body))
                        }

                        if (firstDiscovery) {
                            store.insertDiscoveries(
                                listOf(
                                    DiscoveryEntity(
                                        kind = DiscoveryEntity.KIND_DISCOVERY,
                                        bodyName = event.bodyName,
                                        systemName = event.systemName,
                                        systemId64 = event.systemAddress.takeIf { it != 0L },
                                        at = event.at.toEpochMilli(),
                                        detail = event.type
                                    )
                                )
                            )
                        }
                        if (firstMapping) {
                            store.insertDiscoveries(
                                listOf(
                                    DiscoveryEntity(
                                        kind = DiscoveryEntity.KIND_MAPPING,
                                        bodyName = event.bodyName,
                                        systemName = event.systemName,
                                        systemId64 = event.systemAddress.takeIf { it != 0L },
                                        at = event.at.toEpochMilli(),
                                        detail = event.type
                                    )
                                )
                            )
                        }
                    }

                    is ParsedEvent.Codex -> {
                        if (event.isNewEntry) {
                            codex++
                            store.insertDiscoveries(
                                listOf(
                                    DiscoveryEntity(
                                        kind = DiscoveryEntity.KIND_CODEX,
                                        bodyName = event.name,
                                        systemName = event.systemName,
                                        systemId64 = event.systemAddress.takeIf { it != 0L },
                                        at = event.at.toEpochMilli(),
                                        detail = event.region ?: event.category
                                    )
                                )
                            )
                        }
                    }

                    is ParsedEvent.SaaScan -> Unit // no discovery info in Odyssey journals
                }
            }
        }

        return Summary(linesSeen, parsed, visits, scans, firstDiscoveries, firstMappings, codex)
    }
}
