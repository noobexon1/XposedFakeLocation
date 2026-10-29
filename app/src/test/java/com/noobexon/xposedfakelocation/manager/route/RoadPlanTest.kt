package com.noobexon.xposedfakelocation.manager.route

import com.noobexon.xposedfakelocation.data.EncodedPolyline
import com.noobexon.xposedfakelocation.data.GeoMath
import com.noobexon.xposedfakelocation.data.model.JoystickSettings
import com.noobexon.xposedfakelocation.data.model.RoadPath
import com.noobexon.xposedfakelocation.data.model.RoadProfile
import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.data.model.RoutePoint
import com.noobexon.xposedfakelocation.data.model.SegmentSettings
import com.noobexon.xposedfakelocation.data.model.segmentKey
import org.junit.Assert.assertEquals
import org.junit.Test

class RoadPlanTest {
    private val a = RoutePoint("a", 0.0, 0.0)
    private val b = RoutePoint("b", 0.001, 0.0)
    private val settings = JoystickSettings(walkSpeedMps = 1.5f)
    private val road = listOf(0.0 to 0.0001, 0.0005 to 0.0002, 0.001 to 0.0001)
    private val path = RoadPath(RoadProfile.FOOT, 0.0, 0.0, 0.001, 0.0, EncodedPolyline.encode(road), 130.0)
    private val route = Route(
        id = "r",
        points = listOf(a, b),
        followRoads = true,
        roadPaths = mapOf("a>b" to path),
        segments = mapOf(segmentKey("a", "b") to SegmentSettings(dwellSeconds = 20, variationPercent = 10)),
    )

    @Test
    fun roadSegmentBecomesOneLegPerPiece() {
        val plan = RoutePlan.from(route, settings)
        val expected = listOf(0.0 to 0.0) + road + listOf(0.001 to 0.0)
        assertEquals(expected.size - 1, plan.legs.size)
        plan.legs.forEachIndexed { i, leg ->
            assertEquals(expected[i].first, leg.startLatitude, 1e-9)
            assertEquals(expected[i].second, leg.startLongitude, 1e-9)
            assertEquals(expected[i + 1].first, leg.endLatitude, 1e-9)
            assertEquals(expected[i + 1].second, leg.endLongitude, 1e-9)
            assertEquals(2, leg.endPointNumber)
            assertEquals(1.5f, leg.speedMps, 0f)
            assertEquals(0.1, leg.variation, 1e-9)
        }
        assertEquals(listOf(0.0, 0.0, 0.0, 20.0), plan.legs.map { it.dwellSeconds })
        val length = expected.zipWithNext { p, q -> GeoMath.distanceMeters(p.first, p.second, q.first, q.second) }.sum()
        assertEquals(length, plan.totalDistanceM, 1e-6)
    }

    @Test
    fun bearingFollowsEachPiece() {
        val plan = RoutePlan.from(route, settings)
        assertEquals(90f, plan.legs[0].bearingDegrees, 0.5f)
        assertEquals(
            GeoMath.initialBearing(0.0, 0.0001, 0.0005, 0.0002).toFloat(),
            plan.legs[1].bearingDegrees,
            1e-3f,
        )
    }

    @Test
    fun missingOrStaleDirectionPlaysStraight() {
        val reversed = RoutePlan.from(route.copy(reversed = true), settings)
        assertEquals(1, reversed.legs.size)
        val stale = route.copy(points = listOf(a, b.copy(latitude = 0.002)))
        assertEquals(1, RoutePlan.from(stale, settings).legs.size)
        assertEquals(1, RoutePlan.from(route.copy(followRoads = false), settings).legs.size)
    }
}
