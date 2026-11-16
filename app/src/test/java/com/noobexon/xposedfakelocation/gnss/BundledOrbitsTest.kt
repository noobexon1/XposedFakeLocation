package com.noobexon.xposedfakelocation.gnss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BundledOrbitsTest {
    private val svidMap = CelestrakCsvParser.parseSvidMap(File("src/main/assets/gnss/svid_map.csv").readText())
    private val orbits = CelestrakCsvParser.parse(File("src/main/assets/gnss/orbits.csv").readText(), svidMap)

    @Test
    fun everyConstellationIsBundled() {
        val counts = orbits.groupingBy { it.constellation }.eachCount()
        assertEquals(Constellation.entries.toSet(), counts.keys)
        assertTrue(counts.getValue(Constellation.GPS) >= 24)
        assertTrue(counts.getValue(Constellation.GLONASS) >= 18)
        assertTrue(counts.getValue(Constellation.GALILEO) >= 20)
        assertTrue(counts.getValue(Constellation.BEIDOU) >= 30)
    }

    @Test
    fun glonassSatellitesHaveValidChannelsAndSlots() {
        orbits.filter { it.constellation == Constellation.GLONASS }.forEach {
            assertTrue("$it", it.glonassChannel in -7..6)
            assertTrue("$it", it.svid in 1..24)
        }
    }

    @Test
    fun bundledDataSurvivesTheCodec() {
        assertEquals(orbits, OrbitCodec.decode(OrbitCodec.encode(orbits)))
    }
}
