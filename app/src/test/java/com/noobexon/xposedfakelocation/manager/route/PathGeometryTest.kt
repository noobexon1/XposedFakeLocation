package com.noobexon.xposedfakelocation.manager.route

import com.noobexon.xposedfakelocation.data.GeoMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PathGeometryTest {
    @Test
    fun simplifyDropsPointsWithinTolerance() {
        // Nearly straight line east with a 0.5 m wobble, then a real 100 m corner north.
        val points = listOf(
            0.0 to 0.0,
            0.0000045 to 0.0005,
            0.0 to 0.001,
            0.0009 to 0.001,
        )
        val simplified = PathGeometry.simplify(points, toleranceM = 2.0)
        assertEquals(listOf(0.0 to 0.0, 0.0 to 0.001, 0.0009 to 0.001), simplified)
    }

    @Test
    fun simplifyKeepsShortPathsAsIs() {
        val two = listOf(0.0 to 0.0, 1.0 to 1.0)
        assertEquals(two, PathGeometry.simplify(two, 2.0))
    }

    @Test
    fun halfwayLiesOnTheMiddleOfTheLength() {
        // 100 m north, then 300 m east: the halfway point (200 m) is 100 m into the east piece.
        val north = GeoMath.destination(0.0, 0.0, 0.0, 100.0)
        val east = GeoMath.destination(north.first, north.second, 90.0, 300.0)
        val half = PathGeometry.halfway(listOf(0.0 to 0.0, north, east))!!
        val expected = GeoMath.destination(north.first, north.second, 90.0, 100.0)
        assertEquals(expected.first, half.latitude, 1e-9)
        assertEquals(expected.second, half.longitude, 1e-9)
        assertEquals(90.0, half.bearingDegrees, 0.01)
    }

    @Test
    fun halfwayNeedsLength() {
        assertNull(PathGeometry.halfway(listOf(1.0 to 1.0)))
        assertNull(PathGeometry.halfway(listOf(1.0 to 1.0, 1.0 to 1.0)))
    }
}
