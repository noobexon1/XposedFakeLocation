package com.noobexon.xposedfakelocation.manager.ui.map

import com.noobexon.xposedfakelocation.data.RADIUS_EARTH
import com.noobexon.xposedfakelocation.data.model.RouteWaypoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal object RoutePlaybackCalculator {
    fun interpolate(start: Double, end: Double, progress: Double): Double {
        val clampedProgress = progress.coerceIn(0.0, 1.0)
        return start + (end - start) * clampedProgress
    }

    fun interpolateLongitude(start: Double, end: Double, progress: Double): Double {
        val clampedProgress = progress.coerceIn(0.0, 1.0)
        val delta = normalizeLongitude(end - start)
        return normalizeLongitude(start + delta * clampedProgress)
    }

    fun distanceBetween(start: RouteWaypoint, end: RouteWaypoint): Double {
        val startLat = Math.toRadians(start.latitude)
        val endLat = Math.toRadians(end.latitude)
        val deltaLat = Math.toRadians(end.latitude - start.latitude)
        val deltaLon = Math.toRadians(end.longitude - start.longitude)
        val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
            cos(startLat) * cos(endLat) * sin(deltaLon / 2) * sin(deltaLon / 2)
        val c = 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
        return RADIUS_EARTH * c
    }

    private fun normalizeLongitude(longitude: Double): Double {
        var normalized = (longitude + 180.0) % 360.0
        if (normalized < 0.0) normalized += 360.0
        return normalized - 180.0
    }
}
