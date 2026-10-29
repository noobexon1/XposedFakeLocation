package com.noobexon.xposedfakelocation.manager.ui.map

import com.noobexon.xposedfakelocation.data.DEFAULT_MAP_ZOOM
import com.noobexon.xposedfakelocation.data.LOCATION_DETECTION_DELAY_MS
import com.noobexon.xposedfakelocation.data.LOCATION_DETECTION_MAX_ATTEMPTS
import com.noobexon.xposedfakelocation.data.WORLD_MAP_ZOOM
import com.noobexon.xposedfakelocation.manager.geo.LatLon
import kotlinx.coroutines.delay

/** Where the first camera position came from. */
enum class InitialCameraSource {
    MARKER,
    LAST_KNOWN,
    LIVE_FIX,
    WORLD;

    /** Whether the target is the device's real location. */
    val isDeviceLocation: Boolean get() = this == LAST_KNOWN || this == LIVE_FIX

    /** Whether the map animates to the target instead of jumping. */
    val animates: Boolean get() = this == MARKER || this == LIVE_FIX
}

/** The first camera position; [zoom] is in osmdroid units. */
data class InitialCamera(val target: LatLon, val zoom: Double, val source: InitialCameraSource)

/**
 * Picks the first camera position: the saved [marker], else the device's [lastKnown] location,
 * else a fix from [liveFix] polled [attempts] times [delayMs] apart, else the world view.
 * [savedZoom] (or [DEFAULT_MAP_ZOOM]) applies to all but the world view.
 */
suspend fun resolveInitialCamera(
    marker: LatLon?,
    savedZoom: Double?,
    lastKnown: suspend () -> LatLon?,
    liveFix: () -> LatLon?,
    attempts: Int = LOCATION_DETECTION_MAX_ATTEMPTS,
    delayMs: Long = LOCATION_DETECTION_DELAY_MS,
): InitialCamera {
    val zoom = savedZoom ?: DEFAULT_MAP_ZOOM
    if (marker != null) return InitialCamera(marker, zoom, InitialCameraSource.MARKER)
    lastKnown()?.let { return InitialCamera(it, zoom, InitialCameraSource.LAST_KNOWN) }
    repeat(attempts) {
        liveFix()?.let { return InitialCamera(it, zoom, InitialCameraSource.LIVE_FIX) }
        delay(delayMs)
    }
    return InitialCamera(LatLon(0.0, 0.0), WORLD_MAP_ZOOM, InitialCameraSource.WORLD)
}
