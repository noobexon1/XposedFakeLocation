package com.noobexon.xposedfakelocation.xposed.utils

import android.util.Log
import com.noobexon.xposedfakelocation.data.PI
import com.noobexon.xposedfakelocation.data.RADIUS_EARTH
import com.noobexon.xposedfakelocation.data.model.RouteWaypoint
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object RoutePlayer {
    private const val TAG = "[RoutePlayer]"

    @Volatile var logger: ((Int, String, String) -> Unit)? = null
    private fun log(msg: String, priority: Int = Log.INFO) = logger?.invoke(priority, TAG, msg)

    private var waypoints: List<RouteWaypoint> = emptyList()
    private var playbackSpeed: Double = 10.0
    private var isLooping: Boolean = false
    @Volatile private var isActive: Boolean = false
    private var isFinished: Boolean = false

    private var routeStartTime: Long = 0L
    private var segmentDistances: DoubleArray = DoubleArray(0)
    private var totalRouteDistance: Double = 0.0

    private val gson = Gson()

    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "RoutePlayer").also { it.isDaemon = true }
    }
    @Volatile private var timerHandle: java.util.concurrent.ScheduledFuture<*>? = null

    fun loadActiveRoute() {
        try {
            val prefs = PreferencesUtil.getPreferences() ?: return
            val nowPlaying = prefs.getBoolean("route_playing", false)

            if (!nowPlaying) {
                stopTimer()
                isActive = false
                isFinished = false
                log("Route stopped (nowPlaying=false)")
                return
            }

            if (!isActive) {
                if (isFinished) isFinished = false
                playbackSpeed = java.lang.Double.longBitsToDouble(
                    prefs.getLong("route_playback_speed", java.lang.Double.doubleToRawLongBits(10.0))
                )
                isLooping = prefs.getBoolean("route_loop", false)
                val waypointsJson = prefs.getString("active_route_waypoints", null)
                waypoints = if (!waypointsJson.isNullOrBlank()) {
                    val type = object : TypeToken<List<RouteWaypoint>>() {}.type
                    gson.fromJson(waypointsJson, type) ?: emptyList()
                } else {
                    emptyList()
                }
                if (waypoints.isEmpty()) {
                    log("Route not started: no waypoints")
                    return
                }
                precomputeSegmentDistances()
                routeStartTime = System.nanoTime()
                isActive = true
                computeAndSetPosition()
                startTimer()
                log("Route started: ${waypoints.size} waypoints, speed=${playbackSpeed}m/s, segments=${segmentDistances.joinToString()}")
            }

            computeAndSetPosition()
        } catch (e: Exception) {
            log("Error: ${e.message}", Log.ERROR)
            stopTimer()
            isActive = false
        }
    }

    private fun startTimer() {
        stopTimer()
        timerHandle = executor.scheduleWithFixedDelay(
            { timerTick() },
            500, 500, TimeUnit.MILLISECONDS
        )
        log("Timer started (500ms)")
    }

    private fun stopTimer() {
        timerHandle?.cancel(false)
        timerHandle = null
    }

    private fun timerTick() {
        if (isActive) {
            computeAndSetPosition()
        }
    }

    fun isRouteActive(): Boolean = isActive

    fun computeAndSetPosition() {
        if (!isActive || waypoints.isEmpty()) return

        val elapsedNanos = System.nanoTime() - routeStartTime
        val elapsedSeconds = elapsedNanos / 1_000_000_000.0
        val totalDistance = playbackSpeed * elapsedSeconds
        val pos = computePosition(totalDistance)

        if (pos != null) {
            LocationUtil.latitude = pos.first
            LocationUtil.longitude = pos.second
            val walkedWaypoints = computeWalkedWaypoints(totalDistance)
            log("hook: elapsed=${"%.1f".format(elapsedSeconds)}s dist=${"%.1f".format(totalDistance)}m wp_idx=$walkedWaypoints lat=${"%.6f".format(pos.first)} lon=${"%.6f".format(pos.second)}")
        }
    }

    private fun computeWalkedWaypoints(totalDistance: Double): Int {
        var remaining = totalDistance
        for (i in segmentDistances.indices) {
            if (remaining <= segmentDistances[i]) return i
            remaining -= segmentDistances[i]
        }
        return waypoints.size - 1
    }

    private fun computePosition(totalDistance: Double): Pair<Double, Double>? {
        if (waypoints.isEmpty()) return null

        if (totalRouteDistance <= 0.0) {
            return Pair(waypoints[0].latitude, waypoints[0].longitude)
        }

        val effectiveDistance = if (isLooping) {
            totalDistance % totalRouteDistance
        } else {
            totalDistance
        }

        if (effectiveDistance < 0) {
            routeStartTime = System.nanoTime()
            return Pair(waypoints[0].latitude, waypoints[0].longitude)
        }

        var remaining = effectiveDistance
        for (i in segmentDistances.indices) {
            if (remaining <= segmentDistances[i]) {
                val progress = if (segmentDistances[i] > 0) remaining / segmentDistances[i] else 0.0
                val lat = interpolate(waypoints[i].latitude, waypoints[i + 1].latitude, progress)
                val lon = interpolate(waypoints[i].longitude, waypoints[i + 1].longitude, progress)
                return Pair(lat, lon)
            }
            remaining -= segmentDistances[i]
        }

        if (!isLooping) {
            isActive = false
            isFinished = true
            stopTimer()
            log("Route finished")
        }
        return Pair(waypoints.last().latitude, waypoints.last().longitude)
    }

    private fun precomputeSegmentDistances() {
        val n = waypoints.size - 1
        if (n <= 0) {
            segmentDistances = DoubleArray(0)
            totalRouteDistance = 0.0
            return
        }
        segmentDistances = DoubleArray(n)
        for (i in 0 until n) {
            segmentDistances[i] = calculateDistance(
                waypoints[i].latitude, waypoints[i].longitude,
                waypoints[i + 1].latitude, waypoints[i + 1].longitude,
            )
        }
        totalRouteDistance = segmentDistances.sum()
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).let { it * it } +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).let { it * it }
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return RADIUS_EARTH * c
    }

    private fun interpolate(a: Double, b: Double, t: Double): Double = a + (b - a) * t
}
