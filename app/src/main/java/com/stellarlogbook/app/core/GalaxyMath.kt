package com.stellarlogbook.app.core

import kotlin.math.sqrt

/** Distance helpers on Elite's galactic coordinate system (light-years, Sol at origin). */
object GalaxyMath {

    /** Euclidean distance between two points. */
    fun distance(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double): Double {
        val dx = x1 - x2
        val dy = y1 - y2
        val dz = z1 - z2
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /** Distance from Sol (0, 0, 0). */
    fun distanceFromSol(x: Double, y: Double, z: Double): Double =
        sqrt(x * x + y * y + z * z)
}
