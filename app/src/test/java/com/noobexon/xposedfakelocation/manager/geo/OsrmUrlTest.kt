package com.noobexon.xposedfakelocation.manager.geo

import com.noobexon.xposedfakelocation.data.DEFAULT_ROUTING_SERVER_URL
import com.noobexon.xposedfakelocation.data.model.RoadProfile
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class OsrmUrlTest {
    private val request = RoadRequest("a>b", RoadProfile.BIKE, 32.0754, 34.7745, 32.08, 34.78)

    @Test
    fun profileIsSubstitutedAndCoordinatesAreLonLat() {
        assertEquals(
            "https://routing.openstreetmap.de/routed-bike/route/v1/driving/34.774500,32.075400;34.780000,32.080000" +
                "?overview=full&geometries=polyline6&steps=false",
            buildRouteUrl(DEFAULT_ROUTING_SERVER_URL, request),
        )
    }

    @Test
    fun templateWithoutPlaceholderAndTrailingSlash() {
        assertEquals(
            "http://10.0.2.2:5000/route/v1/driving/34.774500,32.075400;34.780000,32.080000" +
                "?overview=full&geometries=polyline6&steps=false",
            buildRouteUrl("http://10.0.2.2:5000/", request),
        )
    }

    @Test
    fun decimalsIgnoreTheDefaultLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals(true, buildRouteUrl("https://x.org", request).contains("34.774500,32.075400"))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
