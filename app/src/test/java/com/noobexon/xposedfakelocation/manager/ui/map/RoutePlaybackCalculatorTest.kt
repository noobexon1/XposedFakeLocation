package com.noobexon.xposedfakelocation.manager.ui.map

import com.noobexon.xposedfakelocation.data.model.RouteWaypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RoutePlaybackCalculatorTest {
    @Test
    fun interpolate_clampsProgressBelowZero() {
        val result = RoutePlaybackCalculator.interpolate(10.0, 20.0, -1.0)

        assertEquals(10.0, result, 0.0)
    }

    @Test
    fun interpolate_clampsProgressAboveOne() {
        val result = RoutePlaybackCalculator.interpolate(10.0, 20.0, 2.0)

        assertEquals(20.0, result, 0.0)
    }

    @Test
    fun interpolate_returnsPointBetweenCoordinates() {
        val result = RoutePlaybackCalculator.interpolate(10.0, 20.0, 0.25)

        assertEquals(12.5, result, 0.0)
    }

    @Test
    fun interpolateLongitude_usesShortestPathAcrossDateLineEastbound() {
        val result = RoutePlaybackCalculator.interpolateLongitude(179.9, -179.9, 0.5)

        assertEquals(180.0, abs(result), 0.0001)
    }

    @Test
    fun interpolateLongitude_usesShortestPathAcrossDateLineWestbound() {
        val result = RoutePlaybackCalculator.interpolateLongitude(-179.9, 179.9, 0.5)

        assertEquals(180.0, abs(result), 0.0001)
    }

    @Test
    fun distanceBetween_returnsZeroForSameWaypoint() {
        val point = RouteWaypoint(latitude = 1.0, longitude = 2.0)

        val result = RoutePlaybackCalculator.distanceBetween(point, point)

        assertEquals(0.0, result, 0.0)
    }

    @Test
    fun distanceBetween_returnsApproximateDistanceForOneDegreeAtEquator() {
        val start = RouteWaypoint(latitude = 0.0, longitude = 0.0)
        val end = RouteWaypoint(latitude = 0.0, longitude = 1.0)

        val result = RoutePlaybackCalculator.distanceBetween(start, end)

        assertTrue(result in 111_000.0..112_000.0)
    }
}
