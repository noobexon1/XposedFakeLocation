package com.noobexon.xposedfakelocation.gnss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FakeSkyBuilderTest {
    private val gpsL5 = 1_176_450_000f

    private fun look(
        constellation: Constellation,
        svid: Int,
        elevation: Double,
        azimuth: Double = 90.0,
        channel: Int? = if (constellation == Constellation.GLONASS) 0 else null,
    ) = OrbitElements(constellation, svid, channel, 0L, 2.0, 0.0, 55.0, 0.0, 0.0, 0.0) to LookAngle(azimuth, elevation)

    private fun gpsSky(count: Int, firstElevation: Double = 20.0, step: Double = 3.0) =
        (1..count).map { look(Constellation.GPS, it, firstElevation + (it - 1) * step) }

    private fun builder() = FakeSkyBuilder(Random(1))

    @Test
    fun onlySatellitesAboveTheHorizonAreListed() {
        val sky = builder().buildFromLooks(
            listOf(look(Constellation.GPS, 1, -1.0), look(Constellation.GPS, 2, 0.5)), 3f, ChipProfile.DEFAULT,
        )
        assertEquals(listOf(2), sky.map { it.svid })
    }

    @Test
    fun ephemerisAboveFiveDegreesAndAlmanacAlways() {
        val sky = builder().buildFromLooks(
            listOf(look(Constellation.GPS, 1, 4.0), look(Constellation.GPS, 2, 6.0)), 3f, ChipProfile.DEFAULT,
        )
        assertFalse(sky.first { it.svid == 1 }.hasEphemeris)
        assertTrue(sky.first { it.svid == 2 }.hasEphemeris)
        assertTrue(sky.all { it.hasAlmanac })
    }

    @Test
    fun cn0FollowsElevationAndAccuracyPenalty() {
        val good = builder().buildFromLooks(listOf(look(Constellation.GPS, 1, 90.0), look(Constellation.GPS, 2, 1.0)), 3f, ChipProfile.DEFAULT)
        assertEquals(45f, good.first { it.svid == 1 }.cn0DbHz, 0.5f)
        assertEquals(25.35f, good.first { it.svid == 2 }.cn0DbHz, 0.5f)
        val poor = builder().buildFromLooks(listOf(look(Constellation.GPS, 1, 90.0)), 30f, ChipProfile.DEFAULT)
        assertEquals(40f, poor.single().cn0DbHz, 0.5f)
    }

    @Test
    fun usedInFixCountFollowsAccuracyBand() {
        val sky = gpsSky(20) + listOf(look(Constellation.GPS, 30, 5.0), look(Constellation.GPS, 31, 10.0))
        mapOf(3f to 10..14, 0f to 10..14, 10f to 6..9, 30f to 4..6).forEach { (accuracy, range) ->
            val built = builder().buildFromLooks(sky, accuracy, ChipProfile.DEFAULT)
            assertTrue("accuracy $accuracy", built.count { it.usedInFix } in range)
            assertTrue(built.filter { it.usedInFix }.all { it.elevationDeg > 10f })
        }
    }

    @Test
    fun usedSetIsStableAndReplacesSatellitesThatDropOut() {
        val builder = builder()
        val sky = gpsSky(20)
        val first = builder.buildFromLooks(sky, 3f, ChipProfile.DEFAULT).filter { it.usedInFix }.map { it.svid }.toSet()
        val second = builder.buildFromLooks(sky, 3f, ChipProfile.DEFAULT).filter { it.usedInFix }.map { it.svid }.toSet()
        assertEquals(first, second)

        val dropped = first.first()
        val third = builder.buildFromLooks(sky.filter { it.first.svid != dropped }, 3f, ChipProfile.DEFAULT)
            .filter { it.usedInFix }.map { it.svid }.toSet()
        assertEquals(first.size, third.size)
        assertTrue(third.containsAll(first - dropped))
    }

    @Test
    fun usedSatellitesAreSpreadAcrossConstellations() {
        val sky = (1..10).map { look(Constellation.GPS, it, 30.0 + it * 4) } +
            (1..10).map { look(Constellation.GLONASS, it, 30.0 + it * 4) }
        val used = builder().buildFromLooks(sky, 3f, ChipProfile.DEFAULT).filter { it.usedInFix }
        assertTrue(used.count { it.constellation == Constellation.GPS } >= 5)
        assertTrue(used.count { it.constellation == Constellation.GLONASS } >= 5)
    }

    @Test
    fun mirrorsConstellationsAndFrequencies() {
        val chip = ChipProfile(mapOf(Constellation.GPS to setOf(ChipProfile.GPS_L1_HZ, gpsL5)), reportsFrequencies = true)
        val sky = builder().buildFromLooks(listOf(look(Constellation.GPS, 1, 60.0), look(Constellation.GLONASS, 1, 60.0)), 3f, chip)
        assertEquals(setOf(Constellation.GPS), sky.map { it.constellation }.toSet())
        assertEquals(setOf(ChipProfile.GPS_L1_HZ, gpsL5), sky.map { it.carrierFrequencyHz }.toSet())
        val l1 = sky.first { it.carrierFrequencyHz == ChipProfile.GPS_L1_HZ }
        val l5 = sky.first { it.carrierFrequencyHz == gpsL5 }
        assertEquals(3f, l1.cn0DbHz - l5.cn0DbHz, 1e-3f)
        assertEquals(l1.usedInFix, l5.usedInFix)
    }

    @Test
    fun glonassUsesItsOwnChannelAndUnmappedGlonassIsSkipped() {
        val sky = builder().buildFromLooks(
            listOf(look(Constellation.GLONASS, 13, 50.0, channel = -2), look(Constellation.GLONASS, 14, 50.0, channel = null)),
            3f,
            ChipProfile.DEFAULT,
        )
        assertEquals(listOf(13), sky.map { it.svid })
        assertEquals(1_602_000_000f - 2 * 562_500f, sky.single().carrierFrequencyHz!!, 256f)
    }

    @Test
    fun noFrequenciesWhenTheChipReportsNone() {
        val chip = ChipProfile(mapOf(Constellation.GPS to emptySet()), reportsFrequencies = false)
        val sky = builder().buildFromLooks(listOf(look(Constellation.GPS, 1, 60.0)), 3f, chip)
        assertNull(sky.single().carrierFrequencyHz)
    }

    @Test
    fun defaultProfileIsGpsAndGlonassOnL1() {
        val sky = builder().buildFromLooks(
            listOf(look(Constellation.GPS, 1, 60.0), look(Constellation.GALILEO, 2, 60.0), look(Constellation.BEIDOU, 3, 60.0)),
            3f,
            ChipProfile.DEFAULT,
        )
        assertEquals(listOf(Constellation.GPS), sky.map { it.constellation })
        assertEquals(ChipProfile.GPS_L1_HZ, sky.single().carrierFrequencyHz)
    }

    @Test
    fun buildComputesLookAnglesAndDropsStaleElements() {
        val gpsSat = OrbitElements(Constellation.GPS, 22, null, 1794796181010L, 2.00559057, 0.01147756, 54.8248, 209.1347, 305.1712, 227.7324)
        val telAviv = Observer(32.08, 34.78, 0.0)
        val sky = builder().build(listOf(gpsSat), telAviv, 1794814181000L, 3f, ChipProfile.DEFAULT)
        assertEquals(13.352f, sky.single().elevationDeg, 0.2f)
        assertEquals(196.122f, sky.single().azimuthDeg, 0.3f)

        val stale = builder().build(listOf(gpsSat), telAviv, gpsSat.epochMillis + 31L * 24 * 3600 * 1000, 3f, ChipProfile.DEFAULT)
        assertTrue(stale.isEmpty())
    }

    @Test
    fun chipProfileFromObservations() {
        val profile = ChipProfile.fromObservations(
            listOf(
                ChipProfile.Observation(1, ChipProfile.GPS_L1_HZ),
                ChipProfile.Observation(1, gpsL5),
                ChipProfile.Observation(3, 1_602_562_500f),
                ChipProfile.Observation(4, ChipProfile.GPS_L1_HZ),
                ChipProfile.Observation(2, null),
            ),
        )!!
        assertEquals(
            mapOf(
                Constellation.GPS to setOf(ChipProfile.GPS_L1_HZ, gpsL5),
                Constellation.GLONASS to setOf(ChipProfile.GLONASS_L1_HZ),
            ),
            profile.frequenciesHz,
        )
        assertTrue(profile.reportsFrequencies)
        assertNull(ChipProfile.fromObservations(listOf(ChipProfile.Observation(4, ChipProfile.GPS_L1_HZ))))

        val withoutFrequencies = ChipProfile.fromObservations(listOf(ChipProfile.Observation(6, null)))!!
        assertFalse(withoutFrequencies.reportsFrequencies)
        assertEquals(mapOf(Constellation.GALILEO to emptySet<Float>()), withoutFrequencies.frequenciesHz)

        val merged = profile.merge(withoutFrequencies)
        assertEquals(setOf(Constellation.GPS, Constellation.GLONASS, Constellation.GALILEO), merged.frequenciesHz.keys)
        assertTrue(merged.reportsFrequencies)
    }

    @Test
    fun legacyEncodingPacksSvidConstellationAndFlags() {
        val full = SkySatellite(Constellation.GPS, 22, 40f, 50f, 10f, true, true, true, ChipProfile.GPS_L1_HZ)
        assertEquals((22 shl 8) or (1 shl 4) or 0xF, LegacyStatusEncoding.svidWithFlags(full))
        val almanacOnly = SkySatellite(Constellation.GLONASS, 13, 20f, 3f, 10f, false, true, false, null)
        assertEquals((13 shl 8) or (3 shl 4) or 0x2, LegacyStatusEncoding.svidWithFlags(almanacOnly))
    }
}
