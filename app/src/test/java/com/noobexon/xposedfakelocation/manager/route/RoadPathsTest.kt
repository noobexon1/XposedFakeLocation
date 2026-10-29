package com.noobexon.xposedfakelocation.manager.route

import com.noobexon.xposedfakelocation.data.EncodedPolyline
import com.noobexon.xposedfakelocation.data.model.JoystickPreset
import com.noobexon.xposedfakelocation.data.model.RoadPath
import com.noobexon.xposedfakelocation.data.model.RoadProfile
import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.data.model.RoutePoint
import com.noobexon.xposedfakelocation.data.model.SegmentSettings
import com.noobexon.xposedfakelocation.data.model.SpeedChoice
import com.noobexon.xposedfakelocation.data.model.segmentKey
import com.noobexon.xposedfakelocation.manager.geo.RoadGeometry
import com.noobexon.xposedfakelocation.manager.geo.RoutingException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoadPathsTest {
    private val a = RoutePoint("a", 0.0, 0.0)
    private val b = RoutePoint("b", 0.001, 0.0)
    private val c = RoutePoint("c", 0.001, 0.001)
    private fun route(vararg points: RoutePoint) = Route(id = "r", points = points.toList(), followRoads = true)

    /** Stores a path for every missing request, as a fetch would. */
    private fun Route.fetched(): Route = copy(
        roadPaths = roadPaths + missingRoadPaths().associate { request ->
            val polyline = EncodedPolyline.encode(
                listOf(request.fromLatitude to request.fromLongitude + 0.0001, request.toLatitude to request.toLongitude + 0.0001)
            )
            request.key to request.toRoadPath(RoadGeometry(polyline, 120.0))
        },
    )

    @Test
    fun newRouteNeedsBothDirectionsOfEverySegment() {
        assertEquals(setOf("a>b", "b>a", "b>c", "c>b"), route(a, b, c).missingRoadPaths().map { it.key }.toSet())
        assertEquals(RoadProfile.FOOT, route(a, b).missingRoadPaths().first().profile)
    }

    @Test
    fun straightRoutesAndOverridesNeedNothing() {
        assertEquals(emptyList<Any>(), route(a, b).copy(followRoads = false).missingRoadPaths())
        val override = route(a, b, c).copy(segments = mapOf(segmentKey("b", "c") to SegmentSettings(followRoads = false)))
        assertEquals(setOf("a>b", "b>a"), override.missingRoadPaths().map { it.key }.toSet())
    }

    @Test
    fun fetchedRouteNeedsNothingAndReversingNeedsNothing() {
        val done = route(a, b, c).fetched()
        assertEquals(emptyList<Any>(), done.missingRoadPaths())
        assertEquals(emptyList<Any>(), done.copy(reversed = true).missingRoadPaths())
    }

    @Test
    fun movingAPointMakesItsPathsStale() {
        val moved = route(a, b, c).fetched().let { r -> r.copy(points = r.points.map { if (it.id == "c") it.copy(latitude = 0.002) else it }) }
        assertEquals(setOf("b>c", "c>b"), moved.missingRoadPaths().map { it.key }.toSet())
        assertNull(moved.roadPathFor(b, moved.points[2]))
    }

    @Test
    fun reorderAndLoopNeedOnlyNewSegments() {
        val done = route(a, b, c).fetched()
        assertEquals(setOf("c>a", "a>c"), done.copy(loop = true).missingRoadPaths().map { it.key }.toSet())
        // Order a, c, b: segment a–c is new; c–b existed as b–c, so its paths are reused.
        assertEquals(setOf("a>c", "c>a"), done.copy(points = listOf(a, c, b)).missingRoadPaths().map { it.key }.toSet())
    }

    @Test
    fun profileChangeMakesPathsStale() {
        val done = route(a, b).fetched()
        val driving = done.copy(defaultSpeed = SpeedChoice.Preset(JoystickPreset.DRIVE))
        assertEquals(setOf("a>b", "b>a"), driving.missingRoadPaths().map { it.key }.toSet())
        assertEquals(RoadProfile.CAR, driving.missingRoadPaths().first().profile)
    }

    @Test
    fun identicalPointsNeedNoRoad() {
        val twin = RoutePoint("t", 0.0, 0.0)
        assertEquals(emptyList<Any>(), route(a, twin).missingRoadPaths())
        assertEquals(0, route(a, twin).missingRoadSegmentCount())
    }

    @Test
    fun coordinatesRunPointRoadPointWithoutDuplicates() {
        val r = route(a, b).copy(
            roadPaths = mapOf(
                "a>b" to RoadPath(RoadProfile.FOOT, 0.0, 0.0, 0.001, 0.0, EncodedPolyline.encode(listOf(0.0 to 0.0, 0.0005 to 0.0001, 0.001 to 0.0001)), 130.0),
            ),
        )
        assertEquals(listOf(0.0 to 0.0, 0.0005 to 0.0001, 0.001 to 0.0001, 0.001 to 0.0), r.roadCoordinates(a, b))
        assertNull(r.roadCoordinates(b, a))
    }

    @Test
    fun statusReflectsPathFailureOrLoading() {
        val r = route(a, b)
        assertEquals(RoadStatus.Loading, r.roadStatus(a, b, emptyMap()))
        val request = r.missingRoadPaths().first { it.key == "a>b" }
        val failures = mapOf("a>b" to RoadFailure(request, RoutingException.Kind.NO_ROUTE))
        assertEquals(RoadStatus.Failed(RoutingException.Kind.NO_ROUTE), r.roadStatus(a, b, failures))
        assertEquals(RoadStatus.Ready(120.0), r.fetched().roadStatus(a, b, failures))
        assertNull(r.copy(followRoads = false).roadStatus(a, b, failures))
        assertEquals(1, r.missingRoadSegmentCount())
        assertEquals(0, r.fetched().missingRoadSegmentCount())
    }
}
