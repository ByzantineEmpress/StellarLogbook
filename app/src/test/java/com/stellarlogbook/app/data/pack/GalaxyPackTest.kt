package com.stellarlogbook.app.data.pack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor
import kotlin.random.Random

class GalaxyPackTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private data class Rec(val id64: Long, val x: Float, val y: Float, val z: Float)

    private fun writePack(file: File, recs: List<Rec>, cellSize: Int = 32) {
        val cells = recs.map { intArrayOf(floor(it.x / cellSize).toInt(), floor(it.y / cellSize).toInt(), floor(it.z / cellSize).toInt()) }
            .distinctBy { "${it[0]},${it[1]},${it[2]}" }
            .sortedWith(compareBy({ it[0] }, { it[1] }, { it[2] }))
        val sorted = recs.sortedBy { it.x }

        file.outputStream().use { out ->
            val h = ByteBuffer.allocate(58).order(ByteOrder.LITTLE_ENDIAN)
            h.put("SLPK1".toByteArray())
            h.put(1)
            h.putInt(sorted.size)
            h.putFloat(sorted.minOfOrNull { it.x } ?: 0f)
            h.putFloat(sorted.maxOfOrNull { it.x } ?: 0f)
            h.putFloat(sorted.minOfOrNull { it.y } ?: 0f)
            h.putFloat(sorted.maxOfOrNull { it.y } ?: 0f)
            h.putFloat(sorted.minOfOrNull { it.z } ?: 0f)
            h.putFloat(sorted.maxOfOrNull { it.z } ?: 0f)
            h.putInt(cellSize)
            h.putInt(cells.size)
            h.putLong(System.currentTimeMillis())
            h.putLong(0)
            out.write(h.array())

            val r = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
            for (rec in sorted) {
                r.clear()
                r.putLong(rec.id64)
                r.putFloat(rec.x)
                r.putFloat(rec.y)
                r.putFloat(rec.z)
                out.write(r.array())
            }

            val c = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            for (cell in cells) {
                c.clear()
                c.putInt(cell[0])
                c.putInt(cell[1])
                c.putInt(cell[2])
                out.write(c.array())
            }
        }
    }

    @Test
    fun parsesHeaderAndBounds() {
        val f = tmp.newFile("p1.bin")
        writePack(f, listOf(Rec(1L, -10f, 5f, 0f), Rec(2L, 40f, -5f, 20f)))
        GalaxyPack(f).use { pack ->
            assertEquals(2, pack.count)
            assertEquals(32, pack.cellSizeLy)
            assertEquals(-10f, pack.minX, 1e-6f)
            assertEquals(40f, pack.maxX, 1e-6f)
        }
    }

    @Test
    fun hasSystemWithinRespectsRadius() {
        val f = tmp.newFile("p2.bin")
        writePack(f, listOf(Rec(1L, 0f, 0f, 0f)))
        GalaxyPack(f).use { pack ->
            assertTrue(pack.hasSystemWithin(5f, 0f, 0f, 5.1f))
            assertFalse(pack.hasSystemWithin(5f, 0f, 0f, 4.9f))
            assertTrue(pack.hasSystemWithin(3f, 4f, 0f, 5.0f)) // 3-4-5 triangle
        }
    }

    @Test
    fun cellOccupancyMatchesGrid() {
        val f = tmp.newFile("p3.bin")
        writePack(f, listOf(Rec(1L, 10f, 10f, 10f)))
        GalaxyPack(f).use { pack ->
            assertTrue(pack.isCellOccupied(0, 0, 0))
            assertFalse(pack.isCellOccupied(1, 0, 0))
            assertFalse(pack.isCellOccupied(0, 1, 0))
        }
    }

    @Test
    fun deepSpaceOriginIsAnImmediateGap() {
        val f = tmp.newFile("p4.bin")
        writePack(f, emptyList())
        GalaxyPack(f).use { pack ->
            val gaps = pack.findNearestGaps(0f, 0f, 0f, searchRadiusLy = 10f, maxDistanceLy = 2000f, maxResults = 5)
            assertTrue(gaps.isNotEmpty())
            assertFalse(pack.hasSystemWithin(gaps[0].x, gaps[0].y, gaps[0].z, 10f))
        }
    }

    @Test
    fun findsVerifiedGapsBeyondBubbleCluster() {
        val f = tmp.newFile("p5.bin")
        val random = Random(42)
        val cluster = (0 until 300).map {
            Rec(
                id64 = it.toLong() + 1,
                x = random.nextFloat() * 400f - 200f,
                y = random.nextFloat() * 400f - 200f,
                z = random.nextFloat() * 400f - 200f
            )
        }
        writePack(f, cluster)
        GalaxyPack(f).use { pack ->
            val gaps = pack.findNearestGaps(0f, 0f, 0f, searchRadiusLy = 15f, maxDistanceLy = 5000f, maxResults = 5)
            assertTrue("expected at least one gap", gaps.isNotEmpty())
            assertEquals(5, gaps.size)
            // every returned gap is verified empty within the search radius
            gaps.forEach { gap ->
                assertFalse(pack.hasSystemWithin(gap.x, gap.y, gap.z, 15f))
            }
            // sorted by distance
            assertEquals(gaps.map { it.distanceLy }.sorted(), gaps.map { it.distanceLy })
        }
    }

    @Test
    fun badMagicRejected() {
        val f = tmp.newFile("p6.bin")
        f.writeBytes(ByteArray(58) { 7 })
        try {
            GalaxyPack(f).close()
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("magic"))
        }
    }
}
