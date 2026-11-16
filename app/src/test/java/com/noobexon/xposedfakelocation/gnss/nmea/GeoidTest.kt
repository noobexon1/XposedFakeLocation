package com.noobexon.xposedfakelocation.gnss.nmea

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoidTest {
    @Test
    fun gridHasOneValuePerFiveDegrees() {
        assertEquals(37 * 72, GEOID_GRID_DECIMETRES.size)
    }

    @Test
    fun gridNodesAreExact() {
        assertEquals(13.6, Geoid.separationMeters(90.0, 10.0), 1e-9)
        assertEquals(-29.5, Geoid.separationMeters(-90.0, 10.0), 1e-9)
        assertEquals(17.2, Geoid.separationMeters(0.0, 0.0), 1e-9)
    }

    /** Reference values from the EGM96 15' grid; the 5 degree grid is within a few metres away from mountains. */
    @Test
    fun matchesEgm96() {
        assertEquals(-32.75, Geoid.separationMeters(40.71, -74.0), 1.5)
        assertEquals(22.41, Geoid.separationMeters(-33.87, 151.21), 1.5)
        assertEquals(-104.68, Geoid.separationMeters(5.0, 78.0), 1.5)
    }

    @Test
    fun longitudeWrapsAround() {
        assertEquals(Geoid.separationMeters(10.0, 0.0), Geoid.separationMeters(10.0, 360.0), 1e-9)
        assertEquals(Geoid.separationMeters(-20.0, 180.0), Geoid.separationMeters(-20.0, -180.0), 1e-9)
        assertEquals(Geoid.separationMeters(10.0, 0.0), Geoid.separationMeters(10.0, -1e-15), 1e-6)
    }
}
