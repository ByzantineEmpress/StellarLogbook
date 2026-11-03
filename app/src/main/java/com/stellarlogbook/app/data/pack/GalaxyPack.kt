package com.stellarlogbook.app.data.pack

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * On-device reader for the Uncharted Pack v2 (plan §6). Answers "is any known
 * system within R of this point?" and "where is the nearest gap with no known
 * systems?" without a network call. Spatial-only: names come from APIs.
 *
 * Binary layout (little-endian):
 *   header:  magic "SLPK1" (5 B) | version u8 = 2 | count u32 |
 *            minX..maxZ f32 (6) | cellSizeLy u32 | reserved u32 |
 *            builtAtMs u64 | reserved u64           (58 bytes total)
 *   records: count x (x f32 | y f32 | z f32), sorted by x ascending
 *
 * A "gap" is a grid cell whose centre has NO known system within the cell's
 * half-diagonal (cellSize * sqrt(3) / 2) — geometrically the cell's whole
 * volume is covered, so this is exact without a separate occupancy grid.
 */
class GalaxyPack(file: File) : Closeable {

    val count: Int
    val cellSizeLy: Int
    val minX: Float
    val maxX: Float
    val minY: Float
    val maxY: Float
    val minZ: Float
    val maxZ: Float

    private val raf = RandomAccessFile(file, "r")
    private val recordsOffset = HEADER_SIZE.toLong()
    private val halfDiagonal: Float

    init {
        val h = ByteArray(HEADER_SIZE)
        raf.readFully(h)
        require(
            h[0] == 'S'.code.toByte() && h[1] == 'L'.code.toByte() &&
                h[2] == 'P'.code.toByte() && h[3] == 'K'.code.toByte()
        ) { "Bad pack magic" }
        require(h[5].toInt() and 0xFF == VERSION) { "Unsupported pack version" }
        count = le32(h, 6)
        minX = leF32(h, 10); maxX = leF32(h, 14)
        minY = leF32(h, 18); maxY = leF32(h, 22)
        minZ = leF32(h, 26); maxZ = leF32(h, 30)
        cellSizeLy = le32(h, 34)
        halfDiagonal = cellSizeLy * HALF_SQRT3
        require(recordsOffset + count.toLong() * RECORD_SIZE <= raf.length()) { "Truncated pack file" }
    }

    /** Any known system within [radius] of (cx, cy, cz)? */
    fun hasSystemWithin(cx: Float, cy: Float, cz: Float, radius: Float): Boolean {
        var found = false
        val r2 = radius * radius
        forEachInXRange(cx - radius, cx + radius) { x, y, z ->
            val dx = x - cx
            val dy = y - cy
            val dz = z - cz
            if (dx * dx + dy * dy + dz * dz <= r2) found = true
        }
        return found
    }

    data class Gap(
        val cellX: Int,
        val cellY: Int,
        val cellZ: Int,
        val x: Float,
        val y: Float,
        val z: Float,
        val distanceLy: Float
    )

    /**
     * Spiral gap search: expanding shells of grid cells around the origin.
     * A cell is a gap when its centre has no known system within the cell
     * half-diagonal (covers the whole cell). Shell r=0 is the cell you're in:
     * in deep space this yields an immediate "you are uncharted" result.
     */
    fun findNearestGaps(
        originX: Float,
        originY: Float,
        originZ: Float,
        searchRadiusLy: Float,
        maxDistanceLy: Float,
        maxResults: Int
    ): List<Gap> {
        val results = ArrayList<Gap>()
        val ocx = cellOf(originX)
        val ocy = cellOf(originY)
        val ocz = cellOf(originZ)
        val maxR = ceil(maxDistanceLy / cellSizeLy).toInt()
        val probeRadius = maxOf(searchRadiusLy, halfDiagonal)

        for (r in 0..maxR) {
            for (dx in -r..r) {
                for (dy in -r..r) {
                    for (dz in -r..r) {
                        if (maxOf(abs(dx), abs(dy), abs(dz)) != r) continue
                        val ix = ocx + dx
                        val iy = ocy + dy
                        val iz = ocz + dz
                        val x = cellCentre(ix)
                        val y = cellCentre(iy)
                        val z = cellCentre(iz)
                        val dist = sqrt(
                            (x - originX) * (x - originX) +
                                (y - originY) * (y - originY) +
                                (z - originZ) * (z - originZ)
                        )
                        if (dist > maxDistanceLy) continue
                        // Empty cell check first (cheap via half-diagonal), then
                        // confirm the search radius if the user asked for more.
                        if (!hasSystemWithin(x, y, z, probeRadius)) {
                            results.add(Gap(ix, iy, iz, x, y, z, dist))
                            if (results.size >= maxResults) {
                                results.sortBy { it.distanceLy }
                                return results
                            }
                        }
                    }
                }
            }
        }
        results.sortBy { it.distanceLy }
        return results
    }

    /**
     * Visits records with x in [x0, x1] in ascending order. The slab is read
     * contiguously (records are sorted by x), so this is one seek + one read.
     */
    fun forEachInXRange(x0: Float, x1: Float, visitor: (x: Float, y: Float, z: Float) -> Unit) {
        val lo = lowerBoundX(x0)
        val hi = upperBoundX(x1)
        if (lo >= hi) return
        val len = hi - lo
        val bytes = ByteArray(len * RECORD_SIZE)
        raf.seek(recordsOffset + lo.toLong() * RECORD_SIZE)
        raf.readFully(bytes)
        var off = 0
        for (i in 0 until len) {
            visitor(leF32(bytes, off), leF32(bytes, off + 4), leF32(bytes, off + 8))
            off += RECORD_SIZE
        }
    }

    /** First record index whose x >= x0. */
    private fun lowerBoundX(x0: Float): Int {
        var lo = 0
        var hi = count
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (readX(mid) < x0) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** First record index whose x > x1. */
    private fun upperBoundX(x1: Float): Int {
        var lo = 0
        var hi = count
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (readX(mid) <= x1) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private fun readX(index: Int): Float {
        val b = ByteArray(4)
        raf.seek(recordsOffset + index.toLong() * RECORD_SIZE)
        raf.readFully(b)
        return leF32(b, 0)
    }

    private fun cellOf(v: Float): Int = floor(v / cellSizeLy).toInt()
    private fun cellCentre(cell: Int): Float = cell * cellSizeLy + cellSizeLy / 2f

    override fun close() {
        raf.close()
    }

    companion object {
        const val VERSION = 2
        const val HEADER_SIZE = 58
        const val RECORD_SIZE = 12
        private const val HALF_SQRT3 = 0.8660254f // sqrt(3)/2

        private fun le32(b: ByteArray, off: Int): Int =
            (b[off].toInt() and 0xFF) or
                ((b[off + 1].toInt() and 0xFF) shl 8) or
                ((b[off + 2].toInt() and 0xFF) shl 16) or
                ((b[off + 3].toInt() and 0xFF) shl 24)

        private fun leF32(b: ByteArray, off: Int): Float = Float.fromBits(le32(b, off))
    }
}
