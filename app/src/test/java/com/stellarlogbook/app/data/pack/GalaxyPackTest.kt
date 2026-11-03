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
import kotlin.random.Random

class GalaxyPackTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private data class Rec(val x: Float, val y: Float, val z: Float)

    /** Writes a v2 pack: header + 12 B records (x,y,z f32), sorted by x. */
    private fun writePack(file: File, recs: List<Rec>, cellSize: Int = 32) {
        val sorted = recs.sortedBy { it.x }
        file.outputStream().use { out ->
            val h = ByteBuffer.allocate(58).order(ByteOrder.LITTLE_ENDIAN)
            h.put("SLPK1".toByteArray())
            h.put(2)
            h.putInt(sorted.size)
            h.putFloat(sorted.minOfOrNull { it.x } ?: 0f)
            h.putFloat(sorted.maxOfOrNull { it.x } ?: 0f)
            h.putFloat(sorted.minOfOrNull { it.y } ?: 0f)
            h.putFloat(sorted.maxOfOrNull { it.y } ?: 0f)
            h.putFloat(sorted.minOfOrNull { it.z } ?: 0f)
            h.putFloat(sorted.maxOfOrNull { it.z } ?: 0f)
            h.putInt(cellSize)
            h.putInt(0)
            h.putLong(System.currentTimeMillis())
            h.putLong(0)
            out.write(h.array())

            val r = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            for (rec in sorted) {
                r.clear()
                r.putFloat(rec.x)
                r.putFloat(rec.y)
                r.putFloat(rec.z)
                out.write(r.array())
            }
        }
    }

    @Test
    fun parsesHeaderAndBounds() {
        val f = tmp.newFile("p1.bin")
        writePack(f, listOf(Rec(-10f, 5f, 0f), Rec(40f, -5f, 20f)))
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
        writePack(f, listOf(Rec(0f, 0f, 0f)))
        GalaxyPack(f).use { pack ->
            assertTrue(pack.hasSystemWithin(5f, 0f, 0f, 5.1f))
            assertFalse(pack.hasSystemWithin(5f, 0f, 0f, 4.9f))
            assertTrue(pack.hasSystemWithin(3f, 4f, 0f, 5.0f)) // 3-4-5 triangle
        }
    }

    @Test
    fun forEachInXRangeVisitsOnlyTheSlab() {
        val f = tmp.newFile("p3.bin")
        writePack(f, listOf(Rec(-100f, 0f, 0f), Rec(-10f, 1f, 0f), Rec(0f, 2f, 0f), Rec(10f, 3f, 0f), Rec(100f, 4f, 0f)))
        GalaxyPack(f).use { pack ->
            val ys = mutableListOf<Float>()
            pack.forEachInXRange(-20f, 20f) { _, y, _ -> ys.add(y) }
            assertEquals(listOf(1f, 2f, 3f), ys)
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
            gaps.forEach { gap ->
                assertFalse(pack.hasSystemWithin(gap.x, gap.y, gap.z, 15f))
            }
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

    @Test
    fun truncatedFileRejected() {
        val f = tmp.newFile("p7.bin")
        writePack(f, listOf(Rec(0f, 0f, 0f)))
        val bytes = f.readBytes()
        f.writeBytes(bytes.copyOf(bytes.size - 6)) // cut half a record off
        try {
            GalaxyPack(f).close()
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Truncated"))
        }
    }
}
