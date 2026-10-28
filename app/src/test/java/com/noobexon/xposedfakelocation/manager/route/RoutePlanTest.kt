package com.noobexon.xposedfakelocation.manager.route

import com.noobexon.xposedfakelocation.data.GeoMath
import com.noobexon.xposedfakelocation.data.model.JoystickPreset
import com.noobexon.xposedfakelocation.data.model.JoystickSettings
import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.data.model.RoutePoint
import com.noobexon.xposedfakelocation.data.model.SegmentSettings
import com.noobexon.xposedfakelocation.data.model.SpeedChoice
import com.noobexon.xposedfakelocation.data.model.segmentKey
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutePlanTest {
    private val a = RoutePoint("a", 0.0, 0.0)
    private val b = RoutePoint("b", 0.001, 0.0)
    private val c = RoutePoint("c", 0.001, 0.001)
    private val settings = JoystickSettings(walkSpeedMps = 1.5f, runSpeedMps = 3.5f)
    private fun route(vararg points: RoutePoint) = Route(id = "r", points = points.toList())

    @Test
    fun buildsOneLegPerSegmentWithDistanceAndBearing() {
        val plan = RoutePlan.from(route(a, b, c), settings)
        assertEquals(2, plan.legs.size)
        assertEquals(GeoMath.distanceMeters(0.0, 0.0, 0.001, 0.0), plan.legs[0].distanceM, 1e-9)
        assertEquals(0f, plan.legs[0].bearingDegrees, 1e-3f)
        assertEquals(90f, plan.legs[1].bearingDegrees, 1e-3f)
        assertEquals(listOf(2, 3), plan.legs.map { it.endPointNumber })
        assertEquals(plan.legs.sumOf { it.distanceM }, plan.totalDistanceM, 1e-9)
        assertEquals(plan.legs[0].distanceM, plan.legStartM(1), 1e-9)
    }

    @Test
    fun reversedLoopStartsAtLastPointAndClosesTheCircuit() {
        val plan = RoutePlan.from(route(a, b, c).copy(reversed = true, loop = true), settings)
        assertEquals(3, plan.legs.size)
        assertEquals(c.latitude, plan.startLatitude, 0.0)
        assertEquals(c.longitude, plan.startLongitude, 0.0)
        assertEquals(3, plan.legs.last().endPointNumber)
        assertEquals(true, plan.loop)
    }

    @Test
    fun speedComesFromOverrideElseDefaultAndIsClamped() {
        val r = route(a, b, c).copy(
            defaultSpeed = SpeedChoice.Preset(JoystickPreset.RUN),
            segments = mapOf(segmentKey("b", "c") to SegmentSettings(speed = SpeedChoice.Custom(10f))),
        )
        val plan = RoutePlan.from(r, settings)
        assertEquals(3.5f, plan.legs[0].speedMps, 0f)
        assertEquals(10f, plan.legs[1].speedMps, 0f)
    }

    @Test
    fun durationIncludesWaitsAndVariationIsAFraction() {
        val r = route(a, b).copy(
            defaultSpeed = SpeedChoice.Custom(10f),
            segments = mapOf(segmentKey("a", "b") to SegmentSettings(dwellSeconds = 30, variationPercent = 20)),
        )
        val plan = RoutePlan.from(r, settings)
        assertEquals(plan.totalDistanceM / 10.0 + 30.0, plan.estimatedDurationS, 1e-6)
        assertEquals(0.2, plan.legs[0].variation, 1e-9)
        assertEquals(30.0, plan.legs[0].dwellSeconds, 0.0)
    }

    @Test
    fun summaryOfShortRouteHasNoDistance() {
        assertEquals(RouteSummary(1, 0.0, 0.0), RouteSummary.of(route(a), settings))
        assertEquals(2, RouteSummary.of(route(a, b), settings).pointCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun planNeedsTwoPoints() {
        RoutePlan.from(route(a), settings)
    }
}
