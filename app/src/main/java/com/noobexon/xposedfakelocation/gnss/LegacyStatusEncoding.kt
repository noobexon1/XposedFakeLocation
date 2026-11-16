package com.noobexon.xposedfakelocation.gnss

/** `svidWithFlags` encoding of the Android 10 hidden `GnssStatus` constructor. */
object LegacyStatusEncoding {
    private const val HAS_EPHEMERIS = 0x1
    private const val HAS_ALMANAC = 0x2
    private const val USED_IN_FIX = 0x4
    private const val HAS_CARRIER_FREQUENCY = 0x8

    fun svidWithFlags(satellite: SkySatellite): Int {
        var flags = 0
        if (satellite.hasEphemeris) flags = flags or HAS_EPHEMERIS
        if (satellite.hasAlmanac) flags = flags or HAS_ALMANAC
        if (satellite.usedInFix) flags = flags or USED_IN_FIX
        if (satellite.carrierFrequencyHz != null) flags = flags or HAS_CARRIER_FREQUENCY
        return (satellite.svid shl 8) or ((satellite.constellation.androidType and 0xf) shl 4) or flags
    }
}
