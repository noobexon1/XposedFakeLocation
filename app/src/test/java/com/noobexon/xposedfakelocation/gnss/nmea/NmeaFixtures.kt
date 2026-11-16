package com.noobexon.xposedfakelocation.gnss.nmea

import com.noobexon.xposedfakelocation.gnss.Constellation
import com.noobexon.xposedfakelocation.gnss.SkySatellite

/** A fixed update (New York, 2026-11-16 15:47:41 UTC) whose sentences were computed by `ref.py`. */
internal object NmeaFixtures {
    fun sat(
        constellation: Constellation,
        svid: Int,
        cn0: Float,
        elevation: Float,
        azimuth: Float,
        used: Boolean,
        megahertz: Double? = null,
    ) = SkySatellite(constellation, svid, cn0, elevation, azimuth, true, true, used, megahertz?.let { (it * 1e6).toFloat() })

    val SKY = listOf(
        sat(Constellation.GPS, 5, 40.2f, 45.4f, 120.6f, true, 1575.42),
        sat(Constellation.GPS, 12, 30f, 10f, 300f, false, 1575.42),
        sat(Constellation.GLONASS, 13, 42f, 60f, 10f, true, 1600.875),
        sat(Constellation.GALILEO, 11, 35f, 30f, 200f, true, 1575.42),
        sat(Constellation.BEIDOU, 6, 33f, 20f, 90f, true, 1561.098),
    )

    val FIX = NmeaFix(
        utcMillis = 1_794_844_061_000L,
        latitude = 40.7128,
        longitude = -74.006,
        mslAltitudeMeters = 10.0,
        geoidSeparationMeters = -32.7,
        speedMetersPerSecond = 5f,
        courseDegrees = 90f,
        magneticVariationDegrees = -12.9,
        sky = SKY,
        fixed = true,
    )

    val NO_FIX = FIX.copy(fixed = false, sky = SKY.map { it.copy(usedInFix = false) })

    val GSV = listOf(
        "\$GPGSV,1,1,02,05,45,121,40,12,10,300,30,1*66\r\n",
        "\$GLGSV,1,1,01,77,60,010,42,1*48\r\n",
        "\$GAGSV,1,1,01,11,30,200,35,7*45\r\n",
        "\$GBGSV,1,1,01,06,20,090,33,1*4A\r\n",
    )
    val GSA = listOf(
        "\$GNGSA,A,3,05,,,,,,,,,,,,4.0,2.0,3.5,1*34\r\n",
        "\$GNGSA,A,3,77,,,,,,,,,,,,4.0,2.0,3.5,2*32\r\n",
        "\$GNGSA,A,3,11,,,,,,,,,,,,4.0,2.0,3.5,3*33\r\n",
        "\$GNGSA,A,3,06,,,,,,,,,,,,4.0,2.0,3.5,4*32\r\n",
    )
    const val GGA = "\$GNGGA,154741.00,4042.768000,N,07400.360000,W,1,04,2.0,10.0,M,-32.7,M,,*73\r\n"
    const val RMC = "\$GNRMC,154741.00,A,4042.768000,N,07400.360000,W,9.7,90.0,161126,12.9,W,A,V*50\r\n"
    const val GNS = "\$GNGNS,154741.00,4042.768000,N,07400.360000,W,AAAANN,04,2.0,10.0,-32.7,,,V*23\r\n"
    const val VTG = "\$GNVTG,90.0,T,,M,9.7,N,18.0,K,A*1D\r\n"
    const val GLL = "\$GNGLL,4042.768000,N,07400.360000,W,154741.00,A,A*6A\r\n"
    const val ZDA = "\$GNZDA,154741.00,16,11,2026,00,00*7B\r\n"
    const val DTM = "\$GNDTM,P90,,0000.000000,N,00000.000000,E,0.000,W84*43\r\n"

    const val NO_FIX_GSA = "\$GPGSA,A,1,,,,,,,,,,,,,,,,*32\r\n"
    const val NO_FIX_GGA = "\$GPGGA,,,,,,0,,,,,,,,*66\r\n"
    const val NO_FIX_RMC = "\$GPRMC,,V,,,,,,,,,,N,V*29\r\n"
    const val NO_FIX_GNS = "\$GPGNS,,,,,,N,,,,,,,V*79\r\n"
    const val NO_FIX_VTG = "\$GPVTG,,T,,M,,N,,K,N*2C\r\n"
    const val NO_FIX_GLL = "\$GPGLL,,,,,,V,N*64\r\n"
    const val NO_FIX_ZDA = "\$GPZDA,,,,,,*48\r\n"
    const val NO_FIX_DTM = "\$GPDTM,,,,,,,,*4A\r\n"
}
