package com.noobexon.xposedfakelocation.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {
    private val metersPerDegree = Math.toRadians(1.0) * RADIUS_EARTH

    @Test
    fun zeroDistanceReturnsSamePoint() {
        val (lat, lon) = GeoMath.destination(32.0, 34.8, 123.0, 0.0)
        assertEquals(32.0, lat, 1e-12)
        assertEquals(34.8, lon, 1e-12)
    }

    @Test
    fun hundredMetersNorthIncreasesLatitudeOnly() {
        val (lat, lon) = GeoMath.destination(0.0, 0.0, 0.0, 100.0)
        assertEquals(100.0 / metersPerDegree, lat, 1e-9)
        assertEquals(0.0, lon, 1e-12)
    }

    @Test
    fun eastwardDegreesScaleWithCosineOfLatitude() {
        val (_, lonAtEquator) = GeoMath.destination(0.0, 0.0, 90.0, 100.0)
        val (_, lonAt60) = GeoMath.destination(60.0, 0.0, 90.0, 100.0)
        assertEquals(2.0, lonAt60 / lonAtEquator, 1e-3)
    }

    @Test
    fun crossingAntimeridianWrapsLongitude() {
        val (_, lon) = GeoMath.destination(0.0, 179.9995, 90.0, 200.0)
        assertEquals(-179.9987, lon, 1e-4)
    }

    @Test
    fun movingPastNorthPoleStaysInValidRange() {
        val (lat, lon) = GeoMath.destination(89.9995, 0.0, 0.0, 200.0)
        assertTrue(lat.isFinite() && lat in -90.0..90.0)
        assertTrue(lon.isFinite() && lon >= -180.0 && lon < 180.0)
    }

    @Test
    fun normalizeLongitudeKeepsHalfOpenRange() {
        assertEquals(-180.0, GeoMath.normalizeLongitude(180.0), 0.0)
        assertEquals(179.0, GeoMath.normalizeLongitude(-181.0), 1e-9)
        assertEquals(-180.0, GeoMath.normalizeLongitude(540.0), 0.0)
        assertEquals(10.0, GeoMath.normalizeLongitude(10.0), 1e-12)
    }

    @Test
    fun distanceOfOneDegreeLatitudeMatchesEarthRadius() {
        val expected = Math.toRadians(1.0) * RADIUS_EARTH
        assertEquals(expected, GeoMath.distanceMeters(0.0, 0.0, 1.0, 0.0), 1e-6)
    }

    @Test
    fun distanceToSelfIsZero() {
        assertEquals(0.0, GeoMath.distanceMeters(10.0, 20.0, 10.0, 20.0), 0.0)
    }

    @Test
    fun initialBearingPointsAlongCardinalDirections() {
        assertEquals(0.0, GeoMath.initialBearing(0.0, 0.0, 1.0, 0.0), 1e-9)
        assertEquals(90.0, GeoMath.initialBearing(0.0, 0.0, 0.0, 1.0), 1e-9)
        assertEquals(180.0, GeoMath.initialBearing(1.0, 0.0, 0.0, 0.0), 1e-9)
        assertEquals(270.0, GeoMath.initialBearing(0.0, 1.0, 0.0, 0.0), 1e-9)
    }

    @Test
    fun destinationRoundTripsThroughDistanceAndBearing() {
        val (lat, lon) = GeoMath.destination(48.0, 11.0, 37.0, 1_500.0)
        assertEquals(1_500.0, GeoMath.distanceMeters(48.0, 11.0, lat, lon), 1e-3)
        assertEquals(37.0, GeoMath.initialBearing(48.0, 11.0, lat, lon), 1e-6)
    }
}
