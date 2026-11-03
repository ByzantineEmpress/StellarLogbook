package com.stellarlogbook.app.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** DTOs must match the live EDSM shapes captured during the Phase 0 probes. */
class EdsmDtosTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun decodesLogsErrorContract() {
        // Verified live: HTTP 200 + msgnum 203 when commander/key not found.
        val dto = json.decodeFromString<EdsmLogsResponse>(
            """{"msgnum":203,"msg":"Commander name/API Key not found"}"""
        )
        assertEquals(203, dto.msgnum)
        assertNull(dto.logs)
    }

    @Test
    fun decodesLogsPageWithCoordinates() {
        val dto = json.decodeFromString<EdsmLogsResponse>(
            """{"msgnum":100,"msg":"OK","logs":[{"system":"Sol","date":"2026-10-01 21:51:00","coordinates":{"x":0,"y":0,"z":0},"firstDiscovery":true}]}"""
        )
        assertEquals(100, dto.msgnum)
        assertEquals("Sol", dto.logs!![0].system)
        assertEquals(0.0, dto.logs!![0].coordinates!!.x!!, 1e-9)
        assertTrue(dto.logs!![0].firstDiscovery!!)
    }

    @Test
    fun decodesLiveSphereSystemsShape() {
        // Verified live (Sol, radius 30).
        val dto = json.decodeFromString<SphereSystemDto>(
            """{"distance":29.87,"bodyCount":6,"name":"Hyroks","id":18795,"id64":7230611002066,"coords":{"x":-28.5625,"y":-2.09375,"z":8.5},"coordsLocked":true,"primaryStar":{"type":"K (Yellow-Orange) Star","name":"Hyroks","isScoopable":true}}"""
        )
        assertEquals("Hyroks", dto.name)
        assertEquals(7230611002066L, dto.id64)
        assertEquals(-28.5625, dto.coords!!.x!!, 1e-9)
        assertEquals("K (Yellow-Orange) Star", dto.primaryStar!!.type)
    }

    @Test
    fun decodesLiveSystemShape() {
        // Verified live: api-v1/system?systemName=Sol&showInformation=1...
        val dto = json.decodeFromString<SystemInfoDto>(
            """{"name":"Sol","coords":{"x":0,"y":0,"z":0},"coordsLocked":true,"requirePermit":true,"permitName":"Sol","information":{"allegiance":"Federation","government":"Democracy","faction":"Mother Gaia","factionState":"None","population":18320926115,"security":"High","economy":"Refinery","secondEconomy":"Service","reserve":"Common"},"primaryStar":{"type":"G (White-Yellow) Star","name":"Sol","isScoopable":true}}"""
        )
        assertEquals("Sol", dto.name)
        assertTrue(dto.requirePermit)
        assertEquals("Federation", dto.information!!.allegiance)
    }
}
