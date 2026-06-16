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
import androidx.core.content.edit

/**
 * Singleton that plays back a route inside the Xposed module process.
 *
 * [RoutePlayer] is called by [LocationUtil.updateLocation] when
 * [PreferencesUtil.getIsPlaying] and an active route are detected. It
 * interpolates the position between two waypoints based on elapsed time
 * and the configured speed.
 *
 * The waypoint list and current progress are read from the remote preferences
 * written by the manager app.
 */
object RoutePlayer {
    private const val TAG = "[RoutePlayer]"

    /**
     * Minimum interval between [advance] calls in nanoseconds.
     * Location getters (getLatitude, getLongitude, etc.) are hooked individually
     * and called in rapid succession by the target app (microseconds apart).
     * This threshold ensures only one advance per ~100ms window, giving a
     * meaningful time delta for interpolation.
     */
    private const val MIN_ADVANCE_INTERVAL_NANOS = 100_000_000L // 100ms

    @Volatile var logger: ((Int, String, String) -> Unit)? = null
    private fun log(msg: String, priority: Int = Log.INFO) = logger?.invoke(priority, TAG, msg)

    private var waypoints: List<RouteWaypoint> = emptyList()
    private var currentIndex: Int = 0
    private var progress: Double = 0.0
    private var playbackSpeed: Double = 10.0 // m/s
    private var isLooping: Boolean = false
    private var isActive: Boolean = false

    /** Timestamp of the last [advance] call, used for delta calculation. */
    private var lastUpdateTime: Long = 0L

    private val gson = Gson()

    /**
     * Loads active route data from the remote preferences.
     * Should be called on every [LocationUtil.updateLocation] invocation
     * to ensure up-to-date data is used.
     *
     * Does NOT reset [lastUpdateTime] – that is handled by [advance] so
     * that wall-clock delta between consecutive calls is preserved.
     */
    fun loadActiveRoute() {
        try {
            val prefs = PreferencesUtil.getPreferences() ?: return
            isActive = prefs.getBoolean("route_playing", false)
            if (!isActive) return

            playbackSpeed = java.lang.Double.longBitsToDouble(
                prefs.getLong("route_playback_speed", java.lang.Double.doubleToRawLongBits(10.0))
            )
            isLooping = prefs.getBoolean("route_loop", false)
            currentIndex = prefs.getInt("active_route_waypoint_index", 0)
            progress = java.lang.Double.longBitsToDouble(
                prefs.getLong("active_route_progress", java.lang.Double.doubleToRawLongBits(0.0))
            )

            val waypointsJson = prefs.getString("active_route_waypoints", null)
            if (!waypointsJson.isNullOrBlank()) {
                val type = object : TypeToken<List<RouteWaypoint>>() {}.type
                waypoints = gson.fromJson(waypointsJson, type) ?: emptyList()
            } else {
                waypoints = emptyList()
            }

            if (waypoints.isEmpty()) {
                isActive = false
            }

            log("Route loaded: ${waypoints.size} waypoints, index=$currentIndex, progress=$progress")
        } catch (e: Exception) {
            log("Error loading route: ${e.message}", Log.ERROR)
            isActive = false
        }
    }

    /**
     * Returns whether the RoutePlayer is active (a route is being played).
     */
    fun isRouteActive(): Boolean = isActive

    /**
     * Moves the position along the route based on time elapsed since the
     * last call. Updates [LocationUtil.latitude] and [LocationUtil.longitude]
     * directly.
     *
     * Should be called by [LocationUtil.updateLocation] when the RoutePlayer
     * is active.
     */
    fun advance() {
        if (!isActive || waypoints.isEmpty()) return

        val now = System.nanoTime()

        if (lastUpdateTime == 0L) {
            lastUpdateTime = now
            setPosition()
            return
        }

        val deltaNanos = now - lastUpdateTime

        // Skip if called too frequently (microseconds between Location getters).
        // Only advance once per ~100ms to get a meaningful time delta.
        if (deltaNanos < MIN_ADVANCE_INTERVAL_NANOS) return

        // If more than 2 seconds elapsed (stop->restart), reinitialize
        // without jumping past waypoints.
        if (deltaNanos > 2_000_000_000L) {
            lastUpdateTime = now
            setPosition()
            return
        }

        lastUpdateTime = now
        val deltaSeconds = deltaNanos / 1_000_000_000.0

        // Calculate distance between current and next waypoint in meters
        if (currentIndex >= waypoints.size - 1) {
            if (isLooping) {
                currentIndex = 0
                progress = 0.0
            } else {
                if (waypoints.isNotEmpty()) {
                    setPositionAt(waypoints.last())
                }
                isActive = false
                return
            }
        }

        val currentWp = waypoints[currentIndex]
        val nextWp = waypoints[currentIndex + 1]

        val distance = calculateDistance(
            currentWp.latitude, currentWp.longitude,
            nextWp.latitude, nextWp.longitude,
        )

        if (distance <= 0) {
            currentIndex++
            progress = 0.0
            setPosition()
            persistState()
            return
        }

        val timeForSegment = distance / playbackSpeed

        val deltaProgress = deltaSeconds / timeForSegment
        progress += deltaProgress

        if (progress >= 1.0) {
            currentIndex++
            progress = 0.0
            setPosition()
            persistState()
        } else {
            val lat = interpolate(currentWp.latitude, nextWp.latitude, progress)
            val lon = interpolate(currentWp.longitude, nextWp.longitude, progress)
            LocationUtil.latitude = lat
            LocationUtil.longitude = lon
        }
    }

    /** Sets position to the current waypoint. */
    private fun setPosition() {
        if (waypoints.isEmpty()) return
        val wp = waypoints[currentIndex.coerceAtMost(waypoints.size - 1)]
        setPositionAt(wp)
    }

    private fun setPositionAt(wp: RouteWaypoint) {
        LocationUtil.latitude = wp.latitude
        LocationUtil.longitude = wp.longitude
    }

    /** Persists current index and progress to remote preferences. */
    private fun persistState() {
        try {
            val prefs = PreferencesUtil.getPreferences() ?: return
            prefs.edit {
                putInt("active_route_waypoint_index", currentIndex)
                    .putLong(
                        "active_route_progress",
                        java.lang.Double.doubleToRawLongBits(progress)
                    )
            }
        } catch (e: Exception) {
            log("Error persisting route state: ${e.message}", Log.ERROR)
        }
    }

    /**
     * Calculates the distance in meters between two coordinates
     * using the Haversine formula.
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).let { it * it } +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).let { it * it }
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return RADIUS_EARTH * c
    }

    /** Linear interpolation between two values. */
    private fun interpolate(a: Double, b: Double, t: Double): Double = a + (b - a) * t
}
