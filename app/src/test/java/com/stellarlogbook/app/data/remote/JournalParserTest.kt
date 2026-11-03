package com.stellarlogbook.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Fixtures are trimmed copies of real journal lines from the Phase 0 spike
 * (commander's own journals, Oct 2026, Odyssey).
 */
class JournalParserTest {

    private val parser = JournalParser()

    @Test
    fun parsesFsdJumpIntoVisit() {
        val line = """{"timestamp":"2026-10-01T21:51:00Z","event":"FSDJump","Taxi":false,"StarSystem":"Col 285 Sector BP-G a40-1","SystemAddress":22951768116112,"StarPos":[-67.75,-43.21875,77.59375],"JumpDist":8.607}"""
        val e = parser.parseLine(line) as ParsedEvent.Visit
        assertEquals("Col 285 Sector BP-G a40-1", e.system)
        assertEquals(22951768116112L, e.systemAddress)
        assertEquals(-67.75, e.x!!, 1e-9)
        assertEquals(-43.21875, e.y!!, 1e-9)
        assertEquals(77.59375, e.z!!, 1e-9)
        assertEquals(8.607, e.jumpDistanceLy!!, 1e-9)
        assertEquals(Instant.parse("2026-10-01T21:51:00Z"), e.at)
    }

    @Test
    fun scanWithWasDiscoveredFalseIsFirstDiscovery() {
        val line = """{"timestamp":"2026-10-01T21:51:07Z","event":"Scan","ScanType":"AutoScan","BodyName":"Col 285 Sector BP-G a40-1","BodyID":0,"StarSystem":"Col 285 Sector BP-G a40-1","SystemAddress":22951768116112,"StarType":"T","WasDiscovered":false,"WasMapped":false,"WasFootfalled":false}"""
        val e = parser.parseLine(line) as ParsedEvent.Scan
        assertEquals(false, e.wasDiscovered)
        assertEquals(false, e.wasMapped)
        assertEquals("T", e.type)
    }

    @Test
    fun scanWithoutFlagsKeepsNulls() {
        val line = """{"timestamp":"2026-10-01T21:51:07Z","event":"Scan","BodyName":"X","StarSystem":"X","PlanetClass":"Icy body"}"""
        val e = parser.parseLine(line) as ParsedEvent.Scan
        assertNull(e.wasDiscovered)
        assertNull(e.wasMapped)
        assertEquals("Icy body", e.type)
    }

    @Test
    fun odysseySaaScanCompleteParsesWithoutLegacyArrays() {
        // Phase 0 finding: Odyssey journals do NOT include Discovered[]/Mapped[].
        val line = """{"timestamp":"2026-10-14T13:45:46Z","event":"SAAScanComplete","BodyName":"HIP 96072 AB 3 e","SystemAddress":633474126570,"BodyID":40,"ProbesUsed":6,"EfficiencyTarget":4}"""
        val e = parser.parseLine(line) as ParsedEvent.SaaScan
        assertEquals("HIP 96072 AB 3 e", e.bodyName)
    }

    @Test
    fun codexEntryPrefersLocalisedNames() {
        val line = """{"timestamp":"2026-10-02T13:32:05Z","event":"CodexEntry","EntryID":1102202,"Name":"${'$'}Codex_Ent_DA_Type_Name;","Name_Localised":"DA Type Star","SubCategory":"${'$'}Codex_SubCategory_Stars;","Category":"${'$'}Codex_Category_StellarBodies;","Category_Localised":"Astronomical Bodies","Region":"${'$'}Codex_RegionName_18;","Region_Localised":"Inner Orion Spur","System":"PY Vulpeculae","SystemAddress":3755688855915,"BodyID":0,"IsNewEntry":true}"""
        val e = parser.parseLine(line) as ParsedEvent.Codex
        assertEquals("DA Type Star", e.name)
        assertEquals("Astronomical Bodies", e.category)
        assertEquals("Inner Orion Spur", e.region)
        assertTrue(e.isNewEntry)
    }

    @Test
    fun unknownEventsAndGarbageAreIgnored() {
        assertNull(parser.parseLine("""{"timestamp":"2026-10-01T21:51:00Z","event":"Music","MusicTrack":"NoTrack"}"""))
        assertNull(parser.parseLine("this is not json"))
        assertNull(parser.parseLine(""))
        assertNull(parser.parseLine("   "))
        // Valid event with unparseable timestamp must not crash.
        assertNull(parser.parseLine("""{"timestamp":"not-a-date","event":"FSDJump","StarSystem":"X"}"""))
    }
}
