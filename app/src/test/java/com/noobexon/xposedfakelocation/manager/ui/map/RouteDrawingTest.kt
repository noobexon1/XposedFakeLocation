package com.noobexon.xposedfakelocation.manager.ui.map

import com.noobexon.xposedfakelocation.data.EncodedPolyline
import com.noobexon.xposedfakelocation.data.model.RoadPath
import com.noobexon.xposedfakelocation.data.model.RoadProfile
import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.data.model.RoutePoint
import com.noobexon.xposedfakelocation.data.model.directedKey
import com.noobexon.xposedfakelocation.manager.geo.RoutingException
import com.noobexon.xposedfakelocation.manager.route.RoadFailure
import com.noobexon.xposedfakelocation.manager.route.roadRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteDrawingTest {
    private val a = RoutePoint("a", 0.0, 0.0)
    private val b = RoutePoint("b", 0.0, 0.01)
    private val c = RoutePoint("c", 0.01, 0.01)

    private fun route(vararg points: RoutePoint) = Route(id = "r", points = points.toList())

    @Test
    fun straightSegmentsRunInTravelOrderWithArrows() {
        val drawing = routeDrawing(route(a, b, c), emptyMap())
        assertEquals(listOf("a" to "b", "b" to "c"), drawing.segments.map { it.fromId to it.toId })
        val first = drawing.segments.first()
        assertEquals(listOf(0.0 to 0.0, 0.0 to 0.01), first.path)
        assertFalse(first.loading)
        assertFalse(first.dashed)
        val arrow = first.marker as SegmentMarker.Arrow
        assertEquals(0.0, arrow.latitude, 1e-6)
        assertEquals(0.005, arrow.longitude, 1e-6)
        assertEquals(90.0, arrow.bearingDegrees, 0.5)
    }

    @Test
    fun pointsAreNumberedWithStartAndEnd() {
        val points = routeDrawing(route(a, b, c), emptyMap()).points
        assertEquals(listOf(1, 2, 3), points.map { it.number })
        assertEquals(listOf(PointRole.START, PointRole.NORMAL, PointRole.END), points.map { it.role })
    }

    @Test
    fun reversedRouteStartsAtTheLastNumber() {
        val drawing = routeDrawing(route(a, b, c).copy(reversed = true), emptyMap())
        assertEquals(listOf("c" to "b", "b" to "a"), drawing.segments.map { it.fromId to it.toId })
        assertEquals(listOf(1, 2, 3), drawing.points.map { it.number })
        assertEquals(listOf(PointRole.END, PointRole.NORMAL, PointRole.START), drawing.points.map { it.role })
    }

    @Test
    fun loopAddsADashedClosingSegmentAndHasNoEnd() {
        val drawing = routeDrawing(route(a, b, c).copy(loop = true), emptyMap())
        assertEquals(listOf(false, false, true), drawing.segments.map { it.dashed })
        assertEquals("c" to "a", drawing.segments.last().let { it.fromId to it.toId })
        assertEquals(listOf(PointRole.START, PointRole.NORMAL, PointRole.NORMAL), drawing.points.map { it.role })
    }

    @Test
    fun roadSegmentWithoutAPathIsLoadingAndStraight() {
        val segment = routeDrawing(route(a, b).copy(followRoads = true), emptyMap()).segments.single()
        assertTrue(segment.loading)
        assertEquals(listOf(0.0 to 0.0, 0.0 to 0.01), segment.path)
        assertTrue(segment.marker is SegmentMarker.Arrow)
    }

    @Test
    fun failedRoadSegmentShowsAWarning() {
        val request = roadRequest(a, b, RoadProfile.FOOT)!!
        val failures = mapOf(request.key to RoadFailure(request, RoutingException.Kind.NETWORK))
        val segment = routeDrawing(route(a, b).copy(followRoads = true), failures).segments.single()
        assertFalse(segment.loading)
        assertTrue(segment.marker is SegmentMarker.Warning)
    }

    @Test
    fun storedRoadPathIsDrawn() {
        val road = listOf(0.0 to 0.0, 0.005 to 0.005, 0.0 to 0.01)
        val path = RoadPath(RoadProfile.FOOT, 0.0, 0.0, 0.0, 0.01, EncodedPolyline.encode(road), 1500.0)
        val withPath = route(a, b).copy(followRoads = true, roadPaths = mapOf(directedKey("a", "b") to path))
        val segment = routeDrawing(withPath, emptyMap()).segments.single()
        assertFalse(segment.loading)
        assertEquals(road, segment.path)
    }

    @Test
    fun emptyRouteDrawsNothing() {
        val drawing = routeDrawing(route(), emptyMap())
        assertTrue(drawing.segments.isEmpty())
        assertTrue(drawing.points.isEmpty())
    }
}
