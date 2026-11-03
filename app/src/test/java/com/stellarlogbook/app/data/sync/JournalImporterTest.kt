package com.stellarlogbook.app.data.sync

import com.stellarlogbook.app.data.remote.JournalParser
import com.stellarlogbook.app.testutil.FakeExplorerStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalImporterTest {

    private val store = FakeExplorerStore()
    private val importer = JournalImporter(JournalParser(), store)

    private val fsd = """{"timestamp":"2026-10-01T21:51:00Z","event":"FSDJump","StarSystem":"System A","SystemAddress":111,"StarPos":[1.0,2.0,3.0],"JumpDist":5.5}"""
    private val location = """{"timestamp":"2026-10-01T22:00:00Z","event":"Location","StarSystem":"System A","SystemAddress":111,"StarPos":[1.0,2.0,3.0]}"""
    private val scanFirst = """{"timestamp":"2026-10-01T21:51:07Z","event":"Scan","BodyName":"System A 1","BodyID":1,"StarSystem":"System A","SystemAddress":111,"PlanetClass":"Icy body","WasDiscovered":false,"WasMapped":false}"""
    private val scanAgain = """{"timestamp":"2026-10-02T10:00:00Z","event":"Scan","BodyName":"System A 1","BodyID":1,"StarSystem":"System A","SystemAddress":111,"PlanetClass":"Icy body","WasDiscovered":true,"WasMapped":true}"""
    private val codexNew = """{"timestamp":"2026-10-02T13:32:05Z","event":"CodexEntry","EntryID":1,"Name":"${'$'}Codex_X;","Name_Localised":"Bacterium A","Category":"${'$'}Codex_Category_Biology;","Category_Localised":"Biological","Region":"${'$'}Codex_RegionName_18;","Region_Localised":"Inner Orion Spur","System":"System B","SystemAddress":222,"BodyID":1,"IsNewEntry":true}"""
    private val codexOld = codexNew.replace("\"IsNewEntry\":true", "\"IsNewEntry\":false")

    @Test
    fun importsVisitsScansAndCodex() = runTest {
        val summary = importer.importLines(
            listOf(fsd, location, scanFirst, scanAgain, codexNew, codexOld, "not json", """{"timestamp":"2026-10-01T21:51:00Z","event":"Music"}""").asSequence()
        )

        assertEquals(8L, summary.lines)
        assertEquals(6, summary.parsed)
        assertEquals(2, summary.visits)
        assertEquals(2, summary.scans)
        assertEquals(1, summary.firstDiscoveries)
        assertEquals(1, summary.firstMappings)
        assertEquals(1, summary.codex)

        val systemA = store.systems["System A"]!!
        assertEquals(2, systemA.visitCount)
        assertEquals(111L, systemA.id64)
        assertEquals(1.0, systemA.x!!, 1e-9)
        assertEquals(2, store.jumps.size)

        // Re-scan with WasDiscovered=true must NOT erase the first-discovery flag.
        val body = store.bodies["System A" to 1L]!!
        assertTrue(body.myFirstDiscovery)
        assertTrue(body.myFirstMapping)

        assertEquals(3, store.discoveries.size) // DISCOVERY + MAPPING + CODEX (deduped)
    }

    @Test
    fun repeatedImportsAreIdempotent() = runTest {
        val first = importer.importLines(listOf(fsd, scanFirst, codexNew).asSequence())
        val second = importer.importLines(listOf(fsd, scanFirst, codexNew).asSequence())

        assertEquals(first.visits, second.visits)
        assertEquals(2, store.systems["System A"]!!.visitCount)
        assertEquals(1, store.jumps.size) // deduped on (to, at)
        assertEquals(3, store.discoveries.size)
        assertTrue(store.bodies["System A" to 1L]!!.myFirstDiscovery)
    }
}
