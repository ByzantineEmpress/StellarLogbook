package com.stellarlogbook.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class GalaxyMathTest {

    @Test
    fun distanceIsEuclidean() {
        assertEquals(5.0, GalaxyMath.distance(0.0, 0.0, 0.0, 3.0, 4.0, 0.0), 1e-9)
    }

    @Test
    fun distanceIsSymmetric() {
        val a = GalaxyMath.distance(1.0, 2.0, 3.0, -4.0, 5.5, 6.0)
        val b = GalaxyMath.distance(-4.0, 5.5, 6.0, 1.0, 2.0, 3.0)
        assertEquals(a, b, 1e-9)
    }

    @Test
    fun distanceFromSolIsMagnitude() {
        assertEquals(13.0, GalaxyMath.distanceFromSol(3.0, 4.0, 12.0), 1e-9)
    }

    @Test
    fun zeroDistanceForSamePoint() {
        assertEquals(0.0, GalaxyMath.distance(1.0, 2.0, 3.0, 1.0, 2.0, 3.0), 1e-9)
    }
}
