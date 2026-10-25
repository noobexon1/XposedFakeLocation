package com.noobexon.xposedfakelocation.data

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

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

    /** Wraps [longitude] (degrees) into `[-180, 180)`. */
    fun normalizeLongitude(longitude: Double): Double = ((longitude + 180) % 360 + 360) % 360 - 180
}
