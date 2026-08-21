package com.noobexon.xposedfakelocation.xposed.utils

import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import com.noobexon.xposedfakelocation.data.DEFAULT_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_ALTITUDE
import com.noobexon.xposedfakelocation.data.DEFAULT_MEAN_SEA_LEVEL
import com.noobexon.xposedfakelocation.data.DEFAULT_MEAN_SEA_LEVEL_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_RANDOMIZE_RADIUS
import com.noobexon.xposedfakelocation.data.DEFAULT_SPEED
import com.noobexon.xposedfakelocation.data.DEFAULT_SPEED_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_VERTICAL_ACCURACY
import com.noobexon.xposedfakelocation.data.PI
import com.noobexon.xposedfakelocation.data.RADIUS_EARTH
import com.noobexon.xposedfakelocation.xposed.utils.LocationUtil.attemptHideMockProvider
import com.noobexon.xposedfakelocation.xposed.utils.LocationUtil.createFakeLocation
import com.noobexon.xposedfakelocation.xposed.utils.LocationUtil.log
import com.noobexon.xposedfakelocation.xposed.utils.LocationUtil.updateLocation
import org.lsposed.hiddenapibypass.HiddenApiBypass
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Singleton holding the current spoofed location state and utilities for building
 * fake [Location] objects.
 *
 * All mutable fields are updated exclusively by [updateLocation], which pulls the
 * latest values from [PreferencesUtil] on every call. External callers (hooks) read
 * the fields but cannot write them directly.
 */
object LocationUtil {
    private const val TAG = "[LocationUtil]"

    /**
     * Optional logger wired in by [com.noobexon.xposedfakelocation.xposed.ModuleEntry].
     * When set, all [log] calls are routed through the libxposed logging channel.
     * Must be `@Volatile` because it is written from one thread and read from many.
     */
    @Volatile
    var logger: ((priority: Int, tag: String, message: String) -> Unit)? = null
    private fun log(message: String, priority: Int = Log.INFO) = logger?.invoke(priority, TAG, message)

    /** Current spoofed latitude in decimal degrees. Updated by [updateLocation]. */
    var latitude: Double = 0.0
        private set
    /** Current spoofed longitude in decimal degrees. Updated by [updateLocation]. */
    var longitude: Double = 0.0
        private set
    /** Current spoofed horizontal accuracy in metres. Zero means "not overridden". */
    var accuracy: Float = 0F
        private set
    /** Current spoofed altitude in metres above WGS-84. Zero means "not overridden". */
    var altitude: Double = 0.0
        private set
    /** Current spoofed vertical accuracy in metres. Zero means "not overridden". */
    var verticalAccuracy: Float = 0F
        private set
    /** Current spoofed MSL altitude in metres (API 34+). Zero means "not overridden". */
    var meanSeaLevel: Double = 0.0
        private set
    /** Current spoofed MSL altitude accuracy in metres (API 34+). Zero means "not overridden". */
    var meanSeaLevelAccuracy: Float = 0F
        private set
    /** Current spoofed ground speed in m/s. Zero means "not overridden". */
    var speed: Float = 0F
        private set
    /** Current spoofed speed accuracy in m/s. Zero means "not overridden". */
    var speedAccuracy: Float = 0F
        private set

    /**
     * Builds a [Location] object populated with the current spoofed field values.
     *
     * [updateLocation] is invoked first so the spoofed fields always reflect the latest
     * preferences, regardless of which hook path calls this method. Without this, callers
     * that skip [updateLocation] (e.g. the system_server hooks) build a location from the
     * initial `0.0` coordinates.
     *
     * If [originalLocation] is provided its metadata (time, bearing, elapsed realtime, etc.)
     * is preserved; otherwise a fresh [Location] is created with a slightly backdated timestamp
     * to satisfy recency checks in some apps.
     *
     * Only non-zero spoofed fields are applied, so unset optional fields fall back to whatever
     * the [originalLocation] carried. The mock-provider flag is cleared via [attemptHideMockProvider].
     *
     * This method is `@Synchronized` to prevent reading partially-updated fields if
     * [updateLocation] is called concurrently.
     *
     * @param originalLocation Optional real location whose metadata is copied into the result.
     * @param provider Location provider string written into the returned [Location].
     */
    @Synchronized
    fun createFakeLocation(originalLocation: Location? = null, provider: String = LocationManager.GPS_PROVIDER): Location {
        updateLocation()

        val fakeLocation = if (originalLocation == null) {
            Location(provider).apply {
                time = System.currentTimeMillis() - 300
            }
        } else {
            Location(originalLocation.provider).apply {
                time = originalLocation.time
                accuracy = originalLocation.accuracy
                bearing = originalLocation.bearing
                bearingAccuracyDegrees = originalLocation.bearingAccuracyDegrees
                elapsedRealtimeNanos = originalLocation.elapsedRealtimeNanos
                verticalAccuracyMeters = originalLocation.verticalAccuracyMeters
            }
        }

        fakeLocation.latitude = latitude
        fakeLocation.longitude = longitude

        if (accuracy != 0F) {
            fakeLocation.accuracy = accuracy
        }

        if (altitude != 0.0) {
            fakeLocation.altitude = altitude
        }

        if (verticalAccuracy != 0F) {
            fakeLocation.verticalAccuracyMeters = verticalAccuracy
        }

        if (speed != 0F) {
            fakeLocation.speed = speed
        }

        if (speedAccuracy != 0F) {
            fakeLocation.speedAccuracyMetersPerSecond = speedAccuracy
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (meanSeaLevel != 0.0) {
                fakeLocation.mslAltitudeMeters = meanSeaLevel
            }

            if (meanSeaLevelAccuracy != 0F) {
                fakeLocation.mslAltitudeAccuracyMeters = meanSeaLevelAccuracy
            }
        }

        attemptHideMockProvider(fakeLocation)

        return fakeLocation
    }

    /**
     * Reads the latest spoofed location settings from [PreferencesUtil] and updates all
     * mutable fields on this object.
     *
     * Coordinates are either taken directly from the last clicked location or randomized
     * within a user-configured radius using the Haversine formula. Optional fields
     * (accuracy, altitude, speed, etc.) are only updated when their corresponding
     * "use" flag is enabled in preferences.
     *
     * This method is `@Synchronized` to guarantee that [createFakeLocation] always sees a
     * consistent snapshot even when called from a different thread.
     */
    @Synchronized
    fun updateLocation() {
        runCatching {
            val location = PreferencesUtil.getLastClickedLocation() ?: run {
                log("Last clicked location is null")
                return
            }

            if (PreferencesUtil.getUseRandomize() == true) {
                val randomizationRadius = PreferencesUtil.getRandomizeRadius() ?: DEFAULT_RANDOMIZE_RADIUS
                val (randomLat, randomLon) = getRandomLocation(location.latitude, location.longitude, randomizationRadius)
                latitude = randomLat
                longitude = randomLon
            } else {
                latitude = location.latitude
                longitude = location.longitude
            }

            if (PreferencesUtil.getUseAccuracy() == true) {
                accuracy = (PreferencesUtil.getAccuracy() ?: DEFAULT_ACCURACY).toFloat()
            }

            if (PreferencesUtil.getUseAltitude() == true) {
                altitude = PreferencesUtil.getAltitude() ?: DEFAULT_ALTITUDE
            }

            if (PreferencesUtil.getUseVerticalAccuracy() == true) {
                verticalAccuracy = PreferencesUtil.getVerticalAccuracy() ?: DEFAULT_VERTICAL_ACCURACY
            }

            if (PreferencesUtil.getUseMeanSeaLevel() == true) {
                meanSeaLevel = PreferencesUtil.getMeanSeaLevel() ?: DEFAULT_MEAN_SEA_LEVEL
            }

            if (PreferencesUtil.getUseMeanSeaLevelAccuracy() == true) {
                meanSeaLevelAccuracy = PreferencesUtil.getMeanSeaLevelAccuracy() ?: DEFAULT_MEAN_SEA_LEVEL_ACCURACY
            }

            if (PreferencesUtil.getUseSpeed() == true) {
                speed = PreferencesUtil.getSpeed() ?: DEFAULT_SPEED
            }

            if (PreferencesUtil.getUseSpeedAccuracy() == true) {
                speedAccuracy = PreferencesUtil.getSpeedAccuracy() ?: DEFAULT_SPEED_ACCURACY
            }
        }.onFailure { log("Error - ${it.message}", priority = Log.ERROR) }
    }

    /**
     * Calculates a uniformly distributed random point within a circle of [radiusInMeters]
     * centred at ([lat], [lon]) using the Haversine formula.
     *
     * @return A [Pair] of (latitude, longitude) in decimal degrees, clamped/normalised
     *         to valid WGS-84 ranges.
     */
    private fun getRandomLocation(lat: Double, lon: Double, radiusInMeters: Double): Pair<Double, Double> {
        val radiusInRadians = radiusInMeters / RADIUS_EARTH

        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)

        val sinLat = sin(latRad)
        val cosLat = cos(latRad)

        val rand1 = Random.nextDouble()
        val rand2 = Random.nextDouble()

        val distance = radiusInRadians * sqrt(rand1)
        val bearing = 2 * PI * rand2

        val sinDistance = sin(distance)
        val cosDistance = cos(distance)

        val newLatRad = asin(sinLat * cosDistance + cosLat * sinDistance * cos(bearing))
        val newLonRad = lonRad + atan2(
            sin(bearing) * sinDistance * cosLat,
            cosDistance - sinLat * sin(newLatRad)
        )

        val newLat = Math.toDegrees(newLatRad)
        var newLon = Math.toDegrees(newLonRad)

        newLon = ((newLon + 180) % 360 + 360) % 360 - 180

        val finalLat = newLat.coerceIn(-90.0, 90.0)

        return Pair(finalLat, newLon)
    }

    /**
     * Attempts to clear the mock-provider flag on [fakeLocation] via the hidden API
     * `Location.setIsFromMockProvider(false)`, bypassed using [HiddenApiBypass].
     *
     * Failure is logged but silently swallowed — some ROM variants or future API levels
     * may block this call, in which case spoofing still works but the mock flag remains set.
     */
    private fun attemptHideMockProvider(fakeLocation: Location) {
        runCatching {
            HiddenApiBypass.invoke(fakeLocation.javaClass, fakeLocation, "setIsFromMockProvider", false)
            log("invoked hidden API - setIsFromMockProvider: false)")
        }.onFailure { log("Not possible to mock - ${it.message}", priority = Log.ERROR) }
    }

    /**
     * Logs all current spoofed location values. Not called in production, but useful for debugging.
     */
    @Suppress("unused")
    private fun logCurrentValues() {
        log("Updated fake location values to:")
        log("\tCoordinates: (latitude = $latitude, longitude = $longitude)")
        log("\tAccuracy: $accuracy")
        log("\tAltitude: $altitude")
        log("\tVertical Accuracy: $verticalAccuracy")
        log("\tMean Sea Level: $meanSeaLevel")
        log("\tMean Sea Level Accuracy: $meanSeaLevelAccuracy")
        log("\tSpeed: $speed")
        log("\tSpeed Accuracy: $speedAccuracy")
    }
}
