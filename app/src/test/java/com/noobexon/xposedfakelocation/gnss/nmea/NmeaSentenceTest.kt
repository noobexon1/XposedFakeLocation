package com.noobexon.xposedfakelocation.gnss.nmea

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NmeaSentenceTest {
    private val captured = listOf(
        "\$GPGSV,3,1,12,18,09,203,11,24,28,175,17,05,12,133,,06,14,037,,1*6D",
        "\$GLGSV,1,1,04,76,53,347,,75,54,102,,87,30,210,,85,28,028,,1*79",
        "\$GNGSA,A,3,05,18,24,,,,,,,,,,3.3,3.1,0.9,1*30",
        "\$GNVTG,,T,,M,0.0,N,0.0,K,A*3D",
        "\$GNDTM,P90,,0000.000023,S,00000.000000,W,0.989,W84*45",
        "\$GPGSA,A,1,,,,,,,,,,,,,,,,*32",
        "\$GPGGA,,,,,,0,,,,,,,,*66",
        "\$GPRMC,,V,,,,,,,,,,N,V*29",
        "\$GPGNS,,,,,,N,,,,,,,V*79",
        "\$GPVTG,,T,,M,,N,,K,N*2C",
        "\$GPDTM,,,,,,,,*4A",
    )

    @Test
    fun capturedSentencesRoundTrip() {
        captured.forEach { line ->
            assertEquals(line + "\r\n", NmeaSentence.parse(line + "\r\n")!!.format())
        }
    }

    @Test
    fun parsesAddressAndFields() {
        val gsv = NmeaSentence.parse(captured[0])!!
        assertEquals("GP", gsv.talker)
        assertEquals("GSV", gsv.type)
        assertEquals(20, gsv.fields.size)
        assertEquals("3", gsv.fields.first())
        assertEquals("05", gsv.fields[11])
        assertEquals("", gsv.fields[14])
        assertEquals("1", gsv.fields.last())
        assertEquals(List(8) { "" }, NmeaSentence.parse("\$GPDTM,,,,,,,,*4A")!!.fields)
    }

    @Test
    fun parsesProprietarySentences() {
        val sentence = NmeaSentence.parse("\$PQXM,1,2*17")!!
        assertEquals("P", sentence.talker)
        assertEquals("QXM", sentence.type)
        assertEquals(listOf("1", "2"), sentence.fields)
        assertEquals("\$PQXM,1,2*17\r\n", sentence.format())
    }

    @Test
    fun checksumIsXorOfTheBody() {
        assertEquals("32", NmeaSentence.checksum("GPGSA,A,1,,,,,,,,,,,,,,,,"))
        assertEquals("4A", NmeaSentence.checksum(NmeaSentence("GP", "DTM", List(8) { "" }).body))
    }

    @Test
    fun acceptsLowercaseChecksums() {
        assertEquals("DTM", NmeaSentence.parse("\$GPDTM,,,,,,,,*4a")!!.type)
    }

    @Test
    fun rejectsMalformedLines() {
        assertNull(NmeaSentence.parse("\$GPDTM,,,,,,,,*4B"))
        assertNull(NmeaSentence.parse("GPDTM,,,,,,,,*4A"))
        assertNull(NmeaSentence.parse("\$GPDTM,,,,,,,,"))
        assertNull(NmeaSentence.parse("\$GPDTM,,,,,,,,*4A1"))
        assertNull(NmeaSentence.parse("\$GPDT,*2B"))
        assertNull(NmeaSentence.parse(""))
    }
}
