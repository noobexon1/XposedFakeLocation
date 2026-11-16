package com.noobexon.xposedfakelocation.gnss

/** Constellations shown in the fake sky, with their `android.location.GnssStatus` constellation type. */
enum class Constellation(val androidType: Int) {
    GPS(1),
    GLONASS(3),
    BEIDOU(5),
    GALILEO(6);

    companion object {
        fun fromAndroidType(type: Int): Constellation? = entries.firstOrNull { it.androidType == type }
    }
}
