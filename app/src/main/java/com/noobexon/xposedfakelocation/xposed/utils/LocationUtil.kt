// LocationUtil.kt
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
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.util.Random
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object LocationUtil {
    private const val TAG = "[LocationUtil]"

    @Volatile
    var logger: ((priority: Int, tag: String, message: String) -> Unit)? = null

    private fun log(message: String, priority: Int = Log.INFO) {
        logger?.invoke(priority, TAG, message)
    }

    private const val DEBUG: Boolean = false

    private val random: Random = Random()

    var latitude: Double = 0.0
    var longitude: Double = 0.0
    var accuracy: Float = 0F
    var altitude: Double = 0.0
    var verticalAccuracy: Float = 0F
    var meanSeaLevel: Double = 0.0
    var meanSeaLevelAccuracy: Float = 0F
    var speed: Float = 0F
    var speedAccuracy: Float = 0F

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

    // Name-based scope attribution for the system-level hooks: a package is spoofed only when it is
    // one of the manager-selected target apps (mirrored into the remote `target_apps` preference).
    fun shouldSpoofPackage(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return PreferencesUtil.getTargetApps().contains(packageName)
    }

    private fun attemptHideMockProvider(fakeLocation: Location) {
        try {
            HiddenApiBypass.invoke(fakeLocation.javaClass, fakeLocation, "setIsFromMockProvider", false)
            log("invoked hidden API - setIsFromMockProvider: false)")
        } catch (e: Exception) {
            log("Not possible to mock - ${e.message}", priority = Log.ERROR)
        }
    }

    @Synchronized
    fun updateLocation() {
        try {
            // If a route is playing, let RoutePlayer control the position
            RoutePlayer.loadActiveRoute()
            if (RoutePlayer.isRouteActive()) {
                return
            }

            PreferencesUtil.getLastClickedLocation()?.let {
                if (PreferencesUtil.getUseRandomize() == true) {
                    val randomizationRadius = PreferencesUtil.getRandomizeRadius() ?: DEFAULT_RANDOMIZE_RADIUS
                    val randomLocation = getRandomLocation(it.latitude, it.longitude, randomizationRadius)
                    latitude = randomLocation.first
                    longitude = randomLocation.second
                } else {
                    latitude = it.latitude
                    longitude = it.longitude
                }

                if (PreferencesUtil.getUseAccuracy() == true) {
                    accuracy = (PreferencesUtil.getAccuracy() ?: DEFAULT_ACCURACY).toFloat()
                }

                if (PreferencesUtil.getUseAltitude() == true) {
                    altitude = PreferencesUtil.getAltitude() ?: DEFAULT_ALTITUDE
                }

                if (PreferencesUtil.getUseVerticalAccuracy() == true) {
                    verticalAccuracy = PreferencesUtil.getVerticalAccuracy()?.toFloat() ?: DEFAULT_VERTICAL_ACCURACY
                }

                if (PreferencesUtil.getUseMeanSeaLevel() == true) {
                    meanSeaLevel = PreferencesUtil.getMeanSeaLevel()?.toDouble() ?: DEFAULT_MEAN_SEA_LEVEL
                }

                if (PreferencesUtil.getUseMeanSeaLevelAccuracy() == true) {
                    meanSeaLevelAccuracy = PreferencesUtil.getMeanSeaLevelAccuracy()?.toFloat() ?: DEFAULT_MEAN_SEA_LEVEL_ACCURACY
                }

                if (PreferencesUtil.getUseSpeed() == true) {
                    speed = PreferencesUtil.getSpeed()?.toFloat() ?: DEFAULT_SPEED
                }

                if (PreferencesUtil.getUseSpeedAccuracy() == true) {
                    speedAccuracy = PreferencesUtil.getSpeedAccuracy()?.toFloat() ?: DEFAULT_SPEED_ACCURACY
                }

                if (DEBUG) {
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
            } ?: log("Last clicked location is null")
        } catch (e: Exception) {
            log("Error - ${e.message}", priority = Log.ERROR)
        }
    }

    // Calculates a random point within a circle around the fake location that has the radius set by by the user. Uses Haversine's formula.
    private fun getRandomLocation(lat: Double, lon: Double, radiusInMeters: Double): Pair<Double, Double> {
        val radiusInRadians = radiusInMeters / RADIUS_EARTH

        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)

        val sinLat = sin(latRad)
        val cosLat = cos(latRad)

        // Generate two random numbers
        val rand1 = random.nextDouble()
        val rand2 = random.nextDouble()

        // Random distance and bearing
        val distance = radiusInRadians * sqrt(rand1)
        val bearing = 2 * PI * rand2

        val sinDistance = sin(distance)
        val cosDistance = cos(distance)

        val newLatRad = asin(sinLat * cosDistance + cosLat * sinDistance * cos(bearing))
        val newLonRad = lonRad + atan2(
            sin(bearing) * sinDistance * cosLat,
            cosDistance - sinLat * sin(newLatRad)
        )

        // Convert back to degrees
        val newLat = Math.toDegrees(newLatRad)
        var newLon = Math.toDegrees(newLonRad)

        // Normalize longitude to be between -180 and 180 degrees
        newLon = ((newLon + 180) % 360 + 360) % 360 - 180

        // Clamp latitude to -90 to 90 degrees
        val finalLat = newLat.coerceIn(-90.0, 90.0)

        return Pair(finalLat, newLon)
    }
}
