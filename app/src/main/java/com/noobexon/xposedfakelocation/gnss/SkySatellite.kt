package com.noobexon.xposedfakelocation.gnss

/** One entry of a fake `GnssStatus`: one per satellite and carrier frequency. */
data class SkySatellite(
    val constellation: Constellation,
    val svid: Int,
    val cn0DbHz: Float,
    val elevationDeg: Float,
    val azimuthDeg: Float,
    val hasEphemeris: Boolean,
    val hasAlmanac: Boolean,
    val usedInFix: Boolean,
    /** `null` when the chip doesn't report carrier frequencies. */
    val carrierFrequencyHz: Float?,
)
