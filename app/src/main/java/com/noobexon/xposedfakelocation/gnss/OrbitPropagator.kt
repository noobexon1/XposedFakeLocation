package com.noobexon.xposedfakelocation.gnss

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Cartesian vector in kilometres. */
data class Vec3(val x: Double, val y: Double, val z: Double)

/**
 * Two-body Kepler propagation with J2 secular drift of the node and perigee, rotated into the
 * Earth-fixed frame by Greenwich mean sidereal time. Within 0.2 deg of SGP4 for GNSS orbits up to a
 * week from epoch.
 */
object OrbitPropagator {
    private const val MU_KM3_S2 = 398600.4418
    private const val EARTH_RADIUS_KM = 6378.137
    private const val J2 = 1.08262668e-3
    private const val KEPLER_ITERATIONS = 10

    /** Earth-fixed position (km) of [elements] at [timeMillis]. */
    fun ecef(elements: OrbitElements, timeMillis: Long): Vec3 {
        val n = elements.meanMotionRevPerDay * 2 * PI / 86_400.0
        val e = elements.eccentricity
        val i = Math.toRadians(elements.inclinationDeg)
        val a = cbrt(MU_KM3_S2 / (n * n))
        val p = a * (1 - e * e)
        val j2Factor = J2 * (EARTH_RADIUS_KM / p).pow(2)
        val dt = (timeMillis - elements.epochMillis) / 1000.0

        val raan = Math.toRadians(elements.raanDeg) - 1.5 * n * j2Factor * cos(i) * dt
        val argPerigee = Math.toRadians(elements.argPerigeeDeg) + 0.75 * n * j2Factor * (5 * cos(i).pow(2) - 1) * dt
        val meanAnomaly = Math.toRadians(elements.meanAnomalyDeg) + n * dt

        var eccentricAnomaly = meanAnomaly
        repeat(KEPLER_ITERATIONS) {
            eccentricAnomaly -= (eccentricAnomaly - e * sin(eccentricAnomaly) - meanAnomaly) / (1 - e * cos(eccentricAnomaly))
        }
        val trueAnomaly = 2 * atan2(sqrt(1 + e) * sin(eccentricAnomaly / 2), sqrt(1 - e) * cos(eccentricAnomaly / 2))
        val radius = a * (1 - e * cos(eccentricAnomaly))
        val u = argPerigee + trueAnomaly

        val x = radius * (cos(raan) * cos(u) - sin(raan) * sin(u) * cos(i))
        val y = radius * (sin(raan) * cos(u) + cos(raan) * sin(u) * cos(i))
        val z = radius * sin(u) * sin(i)
        val theta = gmst(timeMillis)
        return Vec3(x * cos(theta) + y * sin(theta), -x * sin(theta) + y * cos(theta), z)
    }

    /** Greenwich mean sidereal time (IAU 1982) in radians, with UTC standing in for UT1. */
    internal fun gmst(timeMillis: Long): Double {
        val t = (timeMillis / 86_400_000.0 + 2_440_587.5 - 2_451_545.0) / 36_525.0
        val seconds = 67_310.54841 + (876_600.0 * 3600 + 8_640_184.812866) * t + 0.093104 * t * t - 6.2e-6 * t * t * t
        return Math.toRadians(seconds.mod(86_400.0) / 240.0)
    }
}
