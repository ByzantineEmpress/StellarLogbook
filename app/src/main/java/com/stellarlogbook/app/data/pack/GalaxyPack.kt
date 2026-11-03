package com.stellarlogbook.app.data.pack

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * On-device reader for the Uncharted Pack (plan §6). Answers "is any known
 * system within R of this point?" and "where is the nearest gap with no known
 * systems?" without a network call.
 *
 * Binary layout (little-endian):
 *   header:  magic "SLPK1" (5 B) | version u8 | count u32 |
 *            minX..maxZ f32 (6) | cellSizeLy u32 | gridCount u32 |
 *            builtAtMs u64 | reserved u64            (58 bytes total)
 *   records: count x (id64 u64 | x f32 | y f32 | z f32), sorted by x ascending
 *   grid:    gridCount x (ix i32 | iy i32 | iz i32), sorted lexicographically
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
    private val gridCount: Int

    private val raf = RandomAccessFile(file, "r")
    private val recordsOffset = HEADER_SIZE.toLong()
    private val gridOffset: Long

    init {
        val h = ByteArray(HEADER_SIZE)
        raf.readFully(h)
        require(h[0] == 'S'.code.toByte() && h[1] == 'L'.code.toByte() &&
            h[2] == 'P'.code.toByte() && h[3] == 'K'.code.toByte()) { "Bad pack magic" }
        require(h[5].toInt() and 0xFF == VERSION) { "Unsupported pack version" }
        count = le32(h, 6)
        minX = leF32(h, 10); maxX = leF32(h, 14)
        minY = leF32(h, 18); maxY = leF32(h, 22)
        minZ = leF32(h, 26); maxZ = leF32(h, 30)
        cellSizeLy = le32(h, 34)
        gridCount = le32(h, 38)
        gridOffset = recordsOffset + count * RECORD_SIZE.toLong()
        require(gridOffset + gridCount * CELL_SIZE.toLong() <= raf.length()) { "Truncated pack file" }
    }

    /** Any known system within [radius] of (cx, cy, cz)? */
    fun hasSystemWithin(cx: Float, cy: Float, cz: Float, radius: Float): Boolean {
        var i = lowerBoundX(cx - radius)
        val r2 = radius * radius
        val id = LongArray(1)
        val p = FloatArray(3)
        while (i < count) {
            readRecord(i, p, id)
            if (p[0] > cx + radius) break
            val dx = p[0] - cx
            val dy = p[1] - cy
            val dz = p[2] - cz
            if (dx * dx + dy * dy + dz * dz <= r2) return true
            i++
        }
        return false
    }

    /** Is this 3D grid cell marked as containing >=1 known system? */
    fun isCellOccupied(ix: Int, iy: Int, iz: Int): Boolean {
        var lo = 0
        var hi = gridCount
        val cell = IntArray(3)
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            readCell(mid, cell)
            val cmp = when {
                cell[0] != ix -> if (cell[0] < ix) -1 else 1
                cell[1] != iy -> if (cell[1] < iy) -1 else 1
                cell[2] != iz -> if (cell[2] < iz) -1 else 1
                else -> 0
            }
            when {
                cmp < 0 -> lo = mid + 1
                cmp > 0 -> hi = mid
                else -> return true
            }
        }
        return false
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
     * Spiral gap search: expanding shells of grid cells around the origin;
     * each empty cell is verified exactly (no known system within
     * [searchRadiusLy] of the cell centre). Returns nearest verified gaps.
     * Shell r=0 covers the cell you're in: in deep space this yields an
     * immediate "you are uncharted" result.
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
        val maxR = ceilDiv(maxDistanceLy, cellSizeLy.toFloat())

        for (r in 0..maxR) {
            for (dx in -r..r) {
                for (dy in -r..r) {
                    for (dz in -r..r) {
                        if (maxOf(abs(dx), abs(dy), abs(dz)) != r) continue
                        val ix = ocx + dx
                        val iy = ocy + dy
                        val iz = ocz + dz
                        if (isCellOccupied(ix, iy, iz)) continue
                        val x = cellCentre(ix)
                        val y = cellCentre(iy)
                        val z = cellCentre(iz)
                        val dist = sqrt(
                            (x - originX) * (x - originX) +
                                (y - originY) * (y - originY) +
                                (z - originZ) * (z - originZ)
                        )
                        if (dist > maxDistanceLy) continue
                        if (!hasSystemWithin(x, y, z, searchRadiusLy)) {
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

    /** First record index whose x >= x0 (records sorted by x). */
    private fun lowerBoundX(x0: Float): Int {
        var lo = 0
        var hi = count
        val p = FloatArray(3)
        val id = LongArray(1)
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            readRecord(mid, p, id)
            if (p[0] < x0) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private fun readRecord(index: Int, xyz: FloatArray, id64: LongArray) {
        raf.seek(recordsOffset + index * RECORD_SIZE.toLong())
        val b = ByteArray(RECORD_SIZE)
        raf.readFully(b)
        id64[0] = le64(b, 0)
        xyz[0] = leF32(b, 8)
        xyz[1] = leF32(b, 12)
        xyz[2] = leF32(b, 16)
    }

    private fun readCell(index: Int, ijk: IntArray) {
        raf.seek(gridOffset + index * CELL_SIZE.toLong())
        val b = ByteArray(CELL_SIZE)
        raf.readFully(b)
        ijk[0] = le32(b, 0)
        ijk[1] = le32(b, 4)
        ijk[2] = le32(b, 8)
    }

    private fun cellOf(v: Float): Int = kotlin.math.floor(v / cellSizeLy).toInt()
    private fun cellCentre(cell: Int): Float = cell * cellSizeLy + cellSizeLy / 2f
    private fun ceilDiv(a: Float, b: Float): Int = kotlin.math.ceil(a / b).toInt()

    override fun close() {
        raf.close()
    }

    companion object {
        const val VERSION = 1
        const val HEADER_SIZE = 58
        const val RECORD_SIZE = 20
        const val CELL_SIZE = 12

        private fun le32(b: ByteArray, off: Int): Int =
            (b[off].toInt() and 0xFF) or
                ((b[off + 1].toInt() and 0xFF) shl 8) or
                ((b[off + 2].toInt() and 0xFF) shl 16) or
                ((b[off + 3].toInt() and 0xFF) shl 24)

        private fun le64(b: ByteArray, off: Int): Long =
            (le32(b, off).toLong() and 0xFFFFFFFFL) or
                (le32(b, off + 4).toLong() shl 32)

        private fun leF32(b: ByteArray, off: Int): Float =
            Float.fromBits(le32(b, off))
    }
}
