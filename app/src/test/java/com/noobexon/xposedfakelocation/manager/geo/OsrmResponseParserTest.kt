package com.noobexon.xposedfakelocation.manager.geo

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.noobexon.xposedfakelocation.data.EncodedPolyline
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class OsrmResponseParserTest {
    private val polyline = EncodedPolyline.encode(listOf(32.0754 to 34.7745, 32.0760 to 34.7750, 32.08 to 34.78))

    private fun response(code: String, geometry: String? = polyline, distance: Double? = 812.4): String {
        val root = JsonObject().apply { addProperty("code", code) }
        if (code == "Ok") {
            val route = JsonObject().apply {
                geometry?.let { addProperty("geometry", it) }
                distance?.let { addProperty("distance", it) }
                addProperty("duration", 95.1)
            }
            root.add("routes", JsonArray().apply { add(route) })
        }
        return root.toString()
    }

    private fun assertKind(kind: RoutingException.Kind, json: String) {
        try {
            OsrmResponseParser.parse(json)
            fail("expected $kind")
        } catch (e: RoutingException) {
            assertEquals(kind, e.kind)
        }
    }

    @Test
    fun okGivesGeometryAndDistance() {
        assertEquals(RoadGeometry(polyline, 812.4), OsrmResponseParser.parse(response("Ok")))
    }

    @Test
    fun noRouteAndNoSegmentGiveNoRoute() {
        assertKind(RoutingException.Kind.NO_ROUTE, response("NoRoute"))
        assertKind(RoutingException.Kind.NO_ROUTE, response("NoSegment"))
    }

    @Test
    fun otherCodesGiveServer() {
        assertKind(RoutingException.Kind.SERVER, response("TooBig"))
    }

    @Test
    fun malformedGivesParse() {
        assertKind(RoutingException.Kind.PARSE, "not json")
        assertKind(RoutingException.Kind.PARSE, "[]")
        assertKind(RoutingException.Kind.PARSE, """{"code":"Ok","routes":[]}""")
        assertKind(RoutingException.Kind.PARSE, response("Ok", geometry = null))
        assertKind(RoutingException.Kind.PARSE, response("Ok", distance = null))
        assertKind(RoutingException.Kind.PARSE, response("Ok", geometry = "~~~~"))
    }

    @Test
    fun errorBodiesAreClassified() {
        assertEquals(RoutingException.Kind.NO_ROUTE, OsrmResponseParser.errorKind("""{"code":"NoRoute","message":"x"}"""))
        assertEquals(RoutingException.Kind.SERVER, OsrmResponseParser.errorKind("""{"code":"InvalidQuery"}"""))
        assertEquals(RoutingException.Kind.SERVER, OsrmResponseParser.errorKind("<html>429</html>"))
        assertEquals(RoutingException.Kind.SERVER, OsrmResponseParser.errorKind(null))
    }
}
