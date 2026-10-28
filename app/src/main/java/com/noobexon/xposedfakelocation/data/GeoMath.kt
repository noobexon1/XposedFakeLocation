package com.noobexon.xposedfakelocation.data

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Spherical-earth geometry helpers shared by the manager app and the hook side. */
object GeoMath {
    /**
     * Returns the point reached by travelling [distanceMeters] from ([latitude], [longitude]) along
     * the great circle starting at [bearingDegrees] (0 = north, 90 = east).
     *
     * @return (latitude, longitude) in decimal degrees; latitude clamped to `[-90, 90]`, longitude
     *   normalized to `[-180, 180)`.
     */
    fun destination(
        latitude: Double,
        longitude: Double,
        bearingDegrees: Double,
        distanceMeters: Double,
    ): Pair<Double, Double> {
        val angularDistance = distanceMeters / RADIUS_EARTH
        val bearing = Math.toRadians(bearingDegrees)
        val latRad = Math.toRadians(latitude)
        val lonRad = Math.toRadians(longitude)

        val sinLat = sin(latRad)
        val cosLat = cos(latRad)
        val sinDistance = sin(angularDistance)
        val cosDistance = cos(angularDistance)

        val sinNewLat = (sinLat * cosDistance + cosLat * sinDistance * cos(bearing)).coerceIn(-1.0, 1.0)
        val newLatRad = asin(sinNewLat)
        val newLonRad = lonRad + atan2(
            sin(bearing) * sinDistance * cosLat,
            cosDistance - sinLat * sinNewLat
        )

        val newLat = Math.toDegrees(newLatRad).coerceIn(-90.0, 90.0)
        return newLat to normalizeLongitude(Math.toDegrees(newLonRad))
    }

    /** Great-circle (haversine) distance in meters between two points. */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = phi2 - phi1
        val dLambda = Math.toRadians(lon2 - lon1)
        val a = sin(dPhi / 2).pow(2) + cos(phi1) * cos(phi2) * sin(dLambda / 2).pow(2)
        return 2 * RADIUS_EARTH * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /** Initial great-circle bearing from the first point to the second, in `[0, 360)` (0 = north). */
    fun initialBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLambda = Math.toRadians(lon2 - lon1)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Wraps [longitude] (degrees) into `[-180, 180)`. */
    fun normalizeLongitude(longitude: Double): Double = ((longitude + 180) % 360 + 360) % 360 - 180
}
