package com.noobexon.xposedfakelocation.gnss

/** CelesTrak mean orbital elements of one satellite. Angles in degrees, mean motion in revolutions per day. */
data class OrbitElements(
    val constellation: Constellation,
    val svid: Int,
    /** GLONASS FDMA frequency channel `k` (-7..6); `null` for other constellations. */
    val glonassChannel: Int?,
    val epochMillis: Long,
    val meanMotionRevPerDay: Double,
    val eccentricity: Double,
    val inclinationDeg: Double,
    val raanDeg: Double,
    val argPerigeeDeg: Double,
    val meanAnomalyDeg: Double,
)
