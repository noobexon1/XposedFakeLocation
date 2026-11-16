package com.noobexon.xposedfakelocation.gnss.nmea

import com.noobexon.xposedfakelocation.gnss.Constellation
import org.junit.Assert.assertEquals
import org.junit.Test

class NmeaStyleTest {
    private fun observe(vararg lines: String): NmeaStyle =
        lines.fold(NmeaStyle.DEFAULT) { style, line -> style.observe(NmeaSentence.parse(line)!!) }

    @Test
    fun defaultIsTheQualcommStyle() {
        val style = NmeaStyle.DEFAULT
        assertEquals("GP", style.talker(Constellation.GPS))
        assertEquals("GL", style.talker(Constellation.GLONASS))
        assertEquals("GA", style.talker(Constellation.GALILEO))
        assertEquals("GB", style.talker(Constellation.BEIDOU))
        assertEquals(6, style.minuteDecimals)
        assertEquals(6, style.gnsModeLength)
        assertEquals(listOf("P90", "", "0000.000000", "N", "00000.000000", "E", "0.000", "W84"), style.dtmFields)
    }

    @Test
    fun learnsTheBeidouTalker() {
        assertEquals("BD", observe("\$BDGSV,1,1,01,06,20,090,33,1*49").talker(Constellation.BEIDOU))
    }

    @Test
    fun learnsMinuteDecimalsAndGnsModeLength() {
        val style = observe(
            "\$GNRMC,154741.00,A,4042.7680,N,07400.3600,W,9.7,90.0,161126,12.9,W,A,V*50",
            "\$GNGNS,154741.00,4042.7680,N,07400.3600,W,AAAN,04,2.0,10.0,-32.7,,,V*2C",
        )
        assertEquals(4, style.minuteDecimals)
        assertEquals(4, style.gnsModeLength)
    }

    @Test
    fun noFixSentencesTeachNothing() {
        val style = observe("\$GPGNS,,,,,,N,,,,,,,V*79", "\$GPGGA,,,,,,0,,,,,,,,*66", "\$GPDTM,,,,,,,,*4A")
        assertEquals(NmeaStyle.DEFAULT, style)
    }

    @Test
    fun learnsTheDatumOffsets() {
        val style = observe("\$GNDTM,P90,,0000.000023,S,00000.000000,W,0.989,W84*45")
        assertEquals(listOf("P90", "", "0000.000023", "S", "00000.000000", "W", "0.989", "W84"), style.dtmFields)
    }
}
