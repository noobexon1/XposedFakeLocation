package com.noobexon.xposedfakelocation.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EncodedPolylineTest {
    @Test
    fun decodesTheReferencePolyline5Vector() {
        val points = EncodedPolyline.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@", precision = 5)!!
        assertEquals(3, points.size)
        assertEquals(38.5, points[0].first, 1e-9)
        assertEquals(-120.2, points[0].second, 1e-9)
        assertEquals(40.7, points[1].first, 1e-9)
        assertEquals(-120.95, points[1].second, 1e-9)
        assertEquals(43.252, points[2].first, 1e-9)
        assertEquals(-126.453, points[2].second, 1e-9)
    }

    @Test
    fun polyline6RoundTripsIncludingNegativeCoordinates() {
        val points = listOf(32.075412 to 34.774512, -33.868820 to 151.209295, 0.0 to -0.000001)
        val decoded = EncodedPolyline.decode(EncodedPolyline.encode(points))!!
        assertEquals(points.size, decoded.size)
        points.zip(decoded).forEach { (expected, actual) ->
            assertEquals(expected.first, actual.first, 1e-9)
            assertEquals(expected.second, actual.second, 1e-9)
        }
    }

    @Test
    fun emptyStringIsAnEmptyPath() {
        assertEquals(emptyList<Pair<Double, Double>>(), EncodedPolyline.decode(""))
    }

    @Test
    fun malformedInputGivesNull() {
        // A value that never terminates (every char has the continuation bit).
        assertNull(EncodedPolyline.decode("~~~~"))
        // A latitude without its longitude.
        assertNull(EncodedPolyline.decode("_p~iF"))
        // A character below the alphabet ('!' = 33 < 63).
        assertNull(EncodedPolyline.decode("!!"))
    }
}
