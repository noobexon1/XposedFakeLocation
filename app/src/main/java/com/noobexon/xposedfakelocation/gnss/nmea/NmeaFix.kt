package com.noobexon.xposedfakelocation.gnss.nmea

import com.noobexon.xposedfakelocation.gnss.SkySatellite

/** Everything one NMEA update reports, taken once so all its sentences agree. */
data class NmeaFix(
    val utcMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val mslAltitudeMeters: Double,
    val geoidSeparationMeters: Double,
    val speedMetersPerSecond: Float,
    /** `null` when the course is unknown. */
    val courseDegrees: Float?,
    /** Magnetic declination, east positive. */
    val magneticVariationDegrees: Double,
    val sky: List<SkySatellite>,
    /** `false` until the fake first fix; the position sentences are then empty. */
    val fixed: Boolean,
)
