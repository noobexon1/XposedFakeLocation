package com.noobexon.xposedfakelocation.gnss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos

class OrbitMathTest {
    private data class Ref(
        val elements: OrbitElements,
        val timeMillis: Long,
        val observer: Observer,
        val azimuthDeg: Double,
        val elevationDeg: Double,
    )

    private val telAviv = Observer(32.08, 34.78, 0.0)
    private val newYork = Observer(40.71, -74.0, 10.0)
    private val sydney = Observer(-33.87, 151.21, 0.0)

    private val gpsSat = OrbitElements(Constellation.GPS, 22, null, 1794796181010L, 2.00559057, 0.01147756, 54.8248, 209.1347, 305.1712, 227.7324)
    private val glonassSat = OrbitElements(Constellation.GLONASS, 13, -2, 1794816184975L, 2.13101412, 0.00040394, 64.3171, 186.7891, 68.7485, 112.1139)
    private val galileoSat = OrbitElements(Constellation.GALILEO, 11, null, 1794666301422L, 1.70475808, 0.00022602, 56.9332, 337.6751, 311.3317, 48.6888)
    private val beidouSat = OrbitElements(Constellation.BEIDOU, 6, null, 1794443799549L, 1.00272326, 0.0055753, 54.3082, 161.6241, 219.2975, 147.5878)

    private val references = listOf(
        Ref(gpsSat, 1794814181000L, telAviv, 196.122, 13.352),
        Ref(gpsSat, 1795055381000L, newYork, 170.093, 40.094),
        Ref(gpsSat, 1795400981000L, newYork, 170.546, 32.391),
        Ref(glonassSat, 1794834184000L, newYork, 179.218, 18.112),
        Ref(glonassSat, 1795420984000L, sydney, 19.074, 13.591),
        Ref(galileoSat, 1794925501000L, telAviv, 72.491, 33.610),
        Ref(galileoSat, 1795271101000L, telAviv, 165.607, 25.545),
        Ref(beidouSat, 1794461799000L, telAviv, 44.563, 31.148),
        Ref(beidouSat, 1794702999000L, telAviv, 93.942, 13.281),
        Ref(beidouSat, 1794702999000L, sydney, 304.303, 20.718),
        Ref(beidouSat, 1795048599000L, telAviv, 91.916, 16.399),
        Ref(beidouSat, 1795048599000L, sydney, 305.423, 17.161),
    )

    @Test
    fun matchesSgp4WithinTwoTenthsOfADegree() {
        references.forEach { ref ->
            val look = LookAngles.compute(ref.observer, OrbitPropagator.ecef(ref.elements, ref.timeMillis))
            val elevationError = abs(look.elevationDeg - ref.elevationDeg)
            val azimuthError = abs(((look.azimuthDeg - ref.azimuthDeg + 540.0) % 360.0) - 180.0) *
                cos(Math.toRadians(ref.elevationDeg))
            assertTrue("$ref elevation off by $elevationError", elevationError < 0.2)
            assertTrue("$ref azimuth off by $azimuthError", azimuthError < 0.2)
        }
    }

    @Test
    fun gmstAtJ2000() {
        assertEquals(Math.toRadians(280.46061837), OrbitPropagator.gmst(946_728_000_000L), 1e-6)
    }

    @Test
    fun satelliteStraightAboveIsAtZenith() {
        val look = LookAngles.compute(Observer(0.0, 0.0, 0.0), Vec3(26_000.0, 0.0, 0.0))
        assertEquals(90.0, look.elevationDeg, 1e-9)
    }

    @Test
    fun satelliteToTheNorthHasAzimuthZero() {
        val look = LookAngles.compute(Observer(0.0, 0.0, 0.0), Vec3(6_378.137, 0.0, 10_000.0))
        assertEquals(0.0, look.azimuthDeg, 1e-9)
    }
}
