package com.noobexon.xposedfakelocation.gnss.nmea

import com.noobexon.xposedfakelocation.gnss.Constellation
import com.noobexon.xposedfakelocation.gnss.SkySatellite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DopTest {
    private fun sat(elevation: Float, azimuth: Float) =
        SkySatellite(Constellation.GPS, 1, 40f, elevation, azimuth, true, true, true, null)

    @Test
    fun zenithAndThreeOnTheHorizon() {
        val dop = Dop.of(listOf(sat(90f, 0f), sat(0f, 0f), sat(0f, 120f), sat(0f, 240f)))!!
        assertEquals(1.63299, dop.pdop, 1e-4)
        assertEquals(1.15470, dop.hdop, 1e-4)
        assertEquals(1.15470, dop.vdop, 1e-4)
    }

    @Test
    fun sixSatellites() {
        val dop = Dop.of(
            listOf(sat(90f, 0f), sat(30f, 0f), sat(30f, 90f), sat(30f, 180f), sat(30f, 270f), sat(60f, 45f)),
        )!!
        assertEquals(2.28920, dop.pdop, 1e-4)
        assertEquals(1.12639, dop.hdop, 1e-4)
        assertEquals(1.99291, dop.vdop, 1e-4)
    }

    @Test
    fun fewerThanFourSatellitesHaveNoDop() {
        assertNull(Dop.of(listOf(sat(90f, 0f), sat(30f, 0f), sat(30f, 120f))))
    }

    @Test
    fun degenerateGeometryHasNoDop() {
        assertNull(Dop.of(List(5) { sat(45f, 90f) }))
    }
}
