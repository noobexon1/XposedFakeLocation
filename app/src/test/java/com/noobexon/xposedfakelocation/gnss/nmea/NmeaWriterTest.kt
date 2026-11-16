package com.noobexon.xposedfakelocation.gnss.nmea

import com.noobexon.xposedfakelocation.gnss.Constellation
import com.noobexon.xposedfakelocation.gnss.nmea.NmeaFixtures.FIX
import com.noobexon.xposedfakelocation.gnss.nmea.NmeaFixtures.NO_FIX
import com.noobexon.xposedfakelocation.gnss.nmea.NmeaFixtures.sat
import org.junit.Assert.assertEquals
import org.junit.Test

class NmeaWriterTest {
    private val style = NmeaStyle.DEFAULT

    private fun write(type: String, fix: NmeaFix = FIX, style: NmeaStyle = this.style): List<String> =
        NmeaWriter.sentences(type, fix, style).map { it.format() }

    private fun fields(type: String, fix: NmeaFix): List<String> = NmeaWriter.sentences(type, fix, style).single().fields

    @Test
    fun fixedUpdateMatchesTheReference() {
        assertEquals(NmeaFixtures.GSV, write("GSV"))
        assertEquals(NmeaFixtures.GSA, write("GSA"))
        assertEquals(listOf(NmeaFixtures.GGA), write("GGA"))
        assertEquals(listOf(NmeaFixtures.RMC), write("RMC"))
        assertEquals(listOf(NmeaFixtures.GNS), write("GNS"))
        assertEquals(listOf(NmeaFixtures.VTG), write("VTG"))
        assertEquals(listOf(NmeaFixtures.GLL), write("GLL"))
        assertEquals(listOf(NmeaFixtures.ZDA), write("ZDA"))
        assertEquals(listOf(NmeaFixtures.DTM), write("DTM"))
    }

    @Test
    fun withoutFixPositionSentencesAreEmptyButTheSkyIsListed() {
        assertEquals(NmeaFixtures.GSV, write("GSV", NO_FIX))
        assertEquals(listOf(NmeaFixtures.NO_FIX_GSA), write("GSA", NO_FIX))
        assertEquals(listOf(NmeaFixtures.NO_FIX_GGA), write("GGA", NO_FIX))
        assertEquals(listOf(NmeaFixtures.NO_FIX_RMC), write("RMC", NO_FIX))
        assertEquals(listOf(NmeaFixtures.NO_FIX_GNS), write("GNS", NO_FIX))
        assertEquals(listOf(NmeaFixtures.NO_FIX_VTG), write("VTG", NO_FIX))
        assertEquals(listOf(NmeaFixtures.NO_FIX_GLL), write("GLL", NO_FIX))
        assertEquals(listOf(NmeaFixtures.NO_FIX_ZDA), write("ZDA", NO_FIX))
        assertEquals(listOf(NmeaFixtures.NO_FIX_DTM), write("DTM", NO_FIX))
    }

    @Test
    fun unknownTypesWriteNothing() {
        assertEquals(emptyList<String>(), write("TXT"))
    }

    @Test
    fun mirrorsTheChipStyle() {
        val chip = NmeaStyle.DEFAULT.copy(
            talkers = NmeaStyle.DEFAULT.talkers + (Constellation.BEIDOU to "BD"),
            minuteDecimals = 4,
            gnsModeLength = 4,
            dtmFields = listOf("P90", "", "0000.000023", "S", "00000.000000", "W", "0.989", "W84"),
        )
        assertEquals("\$BDGSV,1,1,01,06,20,090,33,1*49\r\n", write("GSV", style = chip).last())
        assertEquals(listOf("\$GNGGA,154741.00,4042.7680,N,07400.3600,W,1,04,2.0,10.0,M,-32.7,M,,*73\r\n"), write("GGA", style = chip))
        assertEquals(listOf("\$GNGNS,154741.00,4042.7680,N,07400.3600,W,AAAA,04,2.0,10.0,-32.7,,,V*23\r\n"), write("GNS", style = chip))
        assertEquals(listOf("\$GNDTM,P90,,0000.000023,S,00000.000000,W,0.989,W84*45\r\n"), write("DTM", style = chip))
    }

    @Test
    fun coordinatesRoundWithCarryAndHemispheres() {
        val gga = fields("GGA", FIX.copy(latitude = 10.9999999999, longitude = -0.5))
        assertEquals(listOf("1100.000000", "N", "00030.000000", "W"), gga.subList(1, 5))
        val south = fields("GGA", FIX.copy(latitude = -33.5, longitude = 151.25))
        assertEquals(listOf("3330.000000", "S", "15115.000000", "E"), south.subList(1, 5))
    }

    @Test
    fun timeRoundsToTheNearestSecond() {
        assertEquals("154742.00", fields("GGA", FIX.copy(utcMillis = FIX.utcMillis + 500))[0])
        assertEquals("154741.00", fields("GGA", FIX.copy(utcMillis = FIX.utcMillis + 499))[0])
    }

    @Test
    fun courseIsEmptyWhenSlowOrUnknown() {
        val slow = FIX.copy(speedMetersPerSecond = 0.3f)
        assertEquals("", fields("RMC", slow)[7])
        assertEquals("0.6", fields("RMC", slow)[6])
        assertEquals("", fields("VTG", slow)[0])
        assertEquals("", fields("RMC", FIX.copy(courseDegrees = null))[7])
        assertEquals("270.0", fields("RMC", FIX.copy(courseDegrees = -90f))[7])
    }

    @Test
    fun eastMagneticVariationAndNoNegativeZero() {
        val rmc = fields("RMC", FIX.copy(magneticVariationDegrees = 3.04))
        assertEquals(listOf("3.0", "E"), rmc.subList(9, 11))
        assertEquals("0.0", fields("GGA", FIX.copy(geoidSeparationMeters = -0.04))[10])
    }

    @Test
    fun singleSystemFixUsesItsTalker() {
        val gps = FIX.copy(
            sky = listOf(
                sat(Constellation.GPS, 1, 40f, 80f, 0f, true),
                sat(Constellation.GPS, 2, 40f, 30f, 0f, true),
                sat(Constellation.GPS, 3, 40f, 30f, 120f, true),
                sat(Constellation.GPS, 4, 40f, 30f, 240f, true),
                sat(Constellation.GLONASS, 5, 40f, 30f, 60f, false),
            ),
        )
        val gga = NmeaWriter.sentences("GGA", gps, style).single()
        assertEquals("GP", gga.talker)
        assertEquals("04", gga.fields[6])
        val gsa = NmeaWriter.sentences("GSA", gps, style).single()
        assertEquals("GP", gsa.talker)
        assertEquals("1", gsa.fields[17])
        assertEquals("ANNNNN", NmeaWriter.sentences("GNS", gps, style).single().fields[5])
    }

    @Test
    fun fewerThanFourUsedLeavesDopEmpty() {
        val three = FIX.copy(sky = FIX.sky.filter { it.constellation != Constellation.BEIDOU })
        assertEquals("", fields("GGA", three)[7])
        assertEquals("03", fields("GGA", three)[6])
        val gsa = NmeaWriter.sentences("GSA", three, style)
        assertEquals(3, gsa.size)
        gsa.forEach { assertEquals(listOf("", "", ""), it.fields.subList(14, 17)) }
    }

    @Test
    fun fixWithoutUsedSatellites() {
        val none = FIX.copy(sky = NO_FIX.sky)
        val gga = NmeaWriter.sentences("GGA", none, style).single()
        assertEquals("GP", gga.talker)
        assertEquals(listOf("1", "00", ""), gga.fields.subList(5, 8))
        val gsa = NmeaWriter.sentences("GSA", none, style).single()
        assertEquals(listOf("A", "3") + List(15) { "" } + "1", gsa.fields)
    }

    @Test
    fun manyUsedSatellitesAreCappedInGgaAndGsa() {
        val many = FIX.copy(sky = (1..14).map { sat(Constellation.GPS, it, 20f + it, 10f + 5 * it, 25f * it, true) })
        assertEquals("12", fields("GGA", many)[6])
        assertEquals("14", fields("GNS", many)[6])
        val gsa = NmeaWriter.sentences("GSA", many, style).single()
        assertEquals((14 downTo 3).map { "%02d".format(it) }, gsa.fields.subList(2, 14))
    }

    @Test
    fun gsvPagesHoldFourSatellites() {
        val nine = FIX.copy(sky = (1..9).map { sat(Constellation.GPS, 10 - it, 30f, 20f, 10f * it, false) })
        val pages = NmeaWriter.gsv(nine, style)
        assertEquals(3, pages.size)
        assertEquals(listOf("3", "1", "09", "01"), pages[0].fields.subList(0, 4))
        assertEquals(listOf("3", "3", "09", "09"), pages[2].fields.subList(0, 4))
        assertEquals(3 + 4 + 1, pages[2].fields.size)
    }

    @Test
    fun eachBandGetsItsOwnGsvGroupPrimaryFirst() {
        val multiBand = FIX.copy(
            sky = listOf(
                sat(Constellation.GPS, 5, 30f, 40f, 100f, true, 1176.45),
                sat(Constellation.GPS, 5, 33f, 40f, 100f, true, 1575.42),
                sat(Constellation.GLONASS, 3, 30f, 40f, 100f, true, 1247.3125),
            ),
        )
        val gsv = NmeaWriter.gsv(multiBand, style)
        assertEquals(listOf("GP" to "1", "GP" to "8", "GL" to "3"), gsv.map { it.talker to it.fields.last() })
        assertEquals(listOf("05", "40", "100", "33"), gsv[0].fields.subList(3, 7))
        assertEquals("67", gsv[2].fields[3])
    }

    @Test
    fun nearlyDegenerateGeometryCapsDop() {
        val flat = FIX.copy(
            sky = listOf(
                sat(Constellation.GPS, 1, 40f, 30f, 0f, true),
                sat(Constellation.GPS, 2, 40f, 30f, 90f, true),
                sat(Constellation.GPS, 3, 40f, 30f, 180f, true),
                sat(Constellation.GPS, 4, 40f, 30.01f, 270f, true),
            ),
        )
        val gsa = NmeaWriter.sentences("GSA", flat, style).single()
        assertEquals(listOf("99.9", "99.9"), listOf(gsa.fields[14], gsa.fields[16]))
    }

    @Test
    fun zeroSignalHasEmptySnr() {
        val silent = FIX.copy(sky = listOf(sat(Constellation.GPS, 7, 0f, 20f, 10f, false)))
        assertEquals("", NmeaWriter.gsv(silent, style).single().fields[6])
    }

    @Test
    fun signalIdsFollowTheQualcommMapping() {
        fun id(constellation: Constellation, megahertz: Double?) =
            NmeaSignals.signalId(constellation, megahertz?.let { (it * 1e6).toFloat() })
        assertEquals("1", id(Constellation.GPS, null))
        assertEquals("6", id(Constellation.GPS, 1227.60))
        assertEquals("8", id(Constellation.GPS, 1176.45))
        assertEquals("1", id(Constellation.GLONASS, 1605.375))
        assertEquals("3", id(Constellation.GLONASS, 1242.9375))
        assertEquals("7", id(Constellation.GALILEO, 1575.42))
        assertEquals("1", id(Constellation.GALILEO, 1176.45))
        assertEquals("2", id(Constellation.GALILEO, 1207.14))
        assertEquals("1", id(Constellation.BEIDOU, 1561.098))
        assertEquals("3", id(Constellation.BEIDOU, 1575.42))
        assertEquals("5", id(Constellation.BEIDOU, 1176.45))
        assertEquals("B", id(Constellation.BEIDOU, 1207.14))
        assertEquals("7", id(Constellation.GALILEO, 1300.0))
    }
}
