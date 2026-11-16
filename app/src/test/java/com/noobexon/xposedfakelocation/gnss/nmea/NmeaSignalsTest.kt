package com.noobexon.xposedfakelocation.gnss.nmea

import com.noobexon.xposedfakelocation.gnss.ChipProfile
import com.noobexon.xposedfakelocation.gnss.Constellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NmeaSignalsTest {
    private fun observe(line: String) = NmeaSignals.observation(NmeaSentence.parse(line)!!)

    @Test
    fun gsvShowsSystemAndBand() {
        assertEquals(
            ChipProfile.Observation(Constellation.GALILEO.androidType, 1_575_420_000f),
            observe("\$GAGSV,3,1,12,04,01,320,,13,50,154,,15,45,064,,19,19,272,,7*71"),
        )
        assertEquals(
            ChipProfile.Observation(Constellation.GLONASS.androidType, ChipProfile.GLONASS_L1_HZ),
            observe("\$GLGSV,1,1,04,76,53,347,,75,54,102,,87,30,210,,85,28,028,,1*79"),
        )
        assertEquals(
            ChipProfile.Observation(Constellation.GPS.androidType, 1_176_450_000f),
            observe("\$GPGSV,1,1,01,05,40,100,30,8*5F"),
        )
        assertEquals(
            ChipProfile.Observation(Constellation.BEIDOU.androidType, 1_207_140_000f),
            observe("\$GBGSV,1,1,00,B*05"),
        )
    }

    @Test
    fun missingSignalIdMeansThePrimaryBand() {
        assertEquals(
            ChipProfile.Observation(Constellation.GPS.androidType, ChipProfile.GPS_L1_HZ),
            observe("\$GPGSV,1,1,01,05,40,100,30*4B"),
        )
    }

    @Test
    fun otherSentencesAndSystemsShowNothing() {
        assertNull(observe("\$GQGSV,1,1,01,193,40,100,30,1*69"))
        assertNull(observe("\$GPGSA,A,1,,,,,,,,,,,,,,,,*32"))
    }

    @Test
    fun capturedUpdateTeachesAllFourSystems() {
        val profile = ChipProfile.fromObservations(
            listOf(
                "\$GPGSV,3,1,12,18,09,203,11,24,28,175,17,05,12,133,,06,14,037,,1*6D",
                "\$GLGSV,1,1,04,76,53,347,,75,54,102,,87,30,210,,85,28,028,,1*79",
                "\$GAGSV,3,1,12,04,01,320,,13,50,154,,15,45,064,,19,19,272,,7*71",
                "\$GBGSV,2,1,08,02,27,118,,03,02,098,,06,09,087,,08,04,052,,1*79",
            ).mapNotNull(::observe),
        )!!
        assertEquals(Constellation.entries.toSet(), profile.frequenciesHz.keys)
    }
}
