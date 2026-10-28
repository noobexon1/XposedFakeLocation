package com.noobexon.xposedfakelocation.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteJsonTest {
    private val route = Route(
        id = "r1",
        name = "Park loop",
        points = listOf(RoutePoint("a", 1.0, 2.0), RoutePoint("b", 1.5, 2.5)),
        segments = mapOf(segmentKey("a", "b") to SegmentSettings(SpeedChoice.Custom(4.2f), dwellSeconds = 30, variationPercent = 10)),
        defaultSpeed = SpeedChoice.Preset(JoystickPreset.BIKE),
        loop = true,
        reversed = true,
    )

    @Test
    fun routesRoundTrip() {
        assertEquals(listOf(route), RouteJson.parseRoutes(RouteJson.gson.toJson(listOf(route))))
    }

    @Test
    fun speedChoiceUsesTypedShape() {
        val json = RouteJson.gson.toJson(route)
        assertTrue(json.contains("\"type\":\"preset\""))
        assertTrue(json.contains("\"preset\":\"BIKE\""))
        assertTrue(json.contains("\"type\":\"custom\""))
    }

    @Test
    fun invalidRoutesAreDropped() {
        val bad = route.copy(id = "r2", points = route.points.take(1))
        assertEquals(listOf(route), RouteJson.parseRoutes(RouteJson.gson.toJson(listOf(route, bad))))
    }

    @Test
    fun unknownSpeedTypeInvalidatesRoute() {
        val json = RouteJson.gson.toJson(listOf(route)).replace("\"type\":\"preset\"", "\"type\":\"teleport\"")
        assertTrue(RouteJson.parseRoutes(json).isEmpty())
    }

    @Test
    fun garbageParsesToEmptyList() {
        assertTrue(RouteJson.parseRoutes("not json").isEmpty())
        assertTrue(RouteJson.parseRoutes(null).isEmpty())
    }

    @Test
    fun draftRoundTripsWithFewPointsAndNoName() {
        val draft = RouteDraft(route.copy(name = "", points = route.points.take(1)), sourceRouteId = "r1", dirty = true)
        assertEquals(draft, RouteJson.parseDraft(RouteJson.gson.toJson(draft)))
    }

    @Test
    fun brokenDraftParsesToNull() {
        assertNull(RouteJson.parseDraft("{}"))
        assertNull(RouteJson.parseDraft("["))
        assertNull(RouteJson.parseDraft(null))
    }
}
