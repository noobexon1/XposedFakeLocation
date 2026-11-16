package com.noobexon.xposedfakelocation.xposed.gnss

import android.hardware.GeomagneticField
import android.os.SystemClock
import com.noobexon.xposedfakelocation.gnss.nmea.Geoid
import com.noobexon.xposedfakelocation.gnss.nmea.NmeaFix
import com.noobexon.xposedfakelocation.xposed.utils.LocationUtil
import kotlin.math.abs

/** Builds the [NmeaFix] of one NMEA update from the fake sky and the fake position. */
internal object NmeaFixSource {
    private const val SHARE_MILLIS = 200L
    private var shared: NmeaFix? = null
    private var sharedAtMillis = 0L

    /**
     * The fix at [utcMillis]: the sky of [GnssSkySource] (which refreshes the fake position when it rebuilds)
     * and one consistent read of [LocationUtil]. MSL altitude is the MSL override, else the altitude override
     * minus the geoid separation, else 0. The course is known only while a joystick drives the motion.
     * Listeners of the same update (within [SHARE_MILLIS]) share one fix.
     */
    @Synchronized
    fun snapshot(utcMillis: Long, fixed: Boolean): NmeaFix {
        val now = SystemClock.elapsedRealtime()
        shared?.takeIf { it.fixed == fixed && now - sharedAtMillis < SHARE_MILLIS && abs(it.utcMillis - utcMillis) < SHARE_MILLIS }
            ?.let { return it }
        return build(utcMillis, fixed).also {
            shared = it
            sharedAtMillis = now
        }
    }

    private fun build(utcMillis: Long, fixed: Boolean): NmeaFix {
        val sky = GnssSkySource.sky(fixed)
        return synchronized(LocationUtil) {
            val latitude = LocationUtil.latitude
            val longitude = LocationUtil.longitude
            val geoid = Geoid.separationMeters(latitude, longitude)
            val msl = when {
                LocationUtil.meanSeaLevel != 0.0 -> LocationUtil.meanSeaLevel
                LocationUtil.altitude != 0.0 -> LocationUtil.altitude - geoid
                else -> 0.0
            }
            val declination = GeomagneticField(
                latitude.toFloat(),
                longitude.toFloat(),
                (msl + geoid).toFloat(),
                utcMillis,
            ).declination
            NmeaFix(
                utcMillis = utcMillis,
                latitude = latitude,
                longitude = longitude,
                mslAltitudeMeters = msl,
                geoidSeparationMeters = geoid,
                speedMetersPerSecond = LocationUtil.speed,
                courseDegrees = LocationUtil.bearing.takeIf { LocationUtil.motionOverride },
                magneticVariationDegrees = declination.toDouble(),
                sky = sky,
                fixed = fixed,
            )
        }
    }
}
