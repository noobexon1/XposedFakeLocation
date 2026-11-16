package com.noobexon.xposedfakelocation.gnss

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** A position on the WGS-84 ellipsoid. */
data class Observer(val latitudeDeg: Double, val longitudeDeg: Double, val altitudeMeters: Double)

/** Direction to a satellite: azimuth clockwise from true north in 0..360, elevation above the horizon. */
data class LookAngle(val azimuthDeg: Double, val elevationDeg: Double)

object LookAngles {
    private const val SEMI_MAJOR_AXIS_KM = 6378.137
    private const val FLATTENING = 1 / 298.257223563

    fun compute(observer: Observer, satelliteEcefKm: Vec3): LookAngle {
        val e2 = FLATTENING * (2 - FLATTENING)
        val lat = Math.toRadians(observer.latitudeDeg)
        val lon = Math.toRadians(observer.longitudeDeg)
        val h = observer.altitudeMeters / 1000.0
        val primeVertical = SEMI_MAJOR_AXIS_KM / sqrt(1 - e2 * sin(lat) * sin(lat))
        val dx = satelliteEcefKm.x - (primeVertical + h) * cos(lat) * cos(lon)
        val dy = satelliteEcefKm.y - (primeVertical + h) * cos(lat) * sin(lon)
        val dz = satelliteEcefKm.z - (primeVertical * (1 - e2) + h) * sin(lat)

        val east = -sin(lon) * dx + cos(lon) * dy
        val north = -sin(lat) * cos(lon) * dx - sin(lat) * sin(lon) * dy + cos(lat) * dz
        val up = cos(lat) * cos(lon) * dx + cos(lat) * sin(lon) * dy + sin(lat) * dz
        return LookAngle(
            azimuthDeg = Math.toDegrees(atan2(east, north)).mod(360.0),
            elevationDeg = Math.toDegrees(atan2(up, hypot(east, north))),
        )
    }
}
