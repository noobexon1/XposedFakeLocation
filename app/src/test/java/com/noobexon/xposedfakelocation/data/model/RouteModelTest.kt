package com.noobexon.xposedfakelocation.data.model

import com.noobexon.xposedfakelocation.data.JOYSTICK_MAX_SPEED_MPS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteModelTest {
    private fun route(vararg ids: String) =
        Route(id = "r", name = "Trip", points = ids.map { RoutePoint(it, 1.0, 2.0) })

    @Test
    fun segmentKeyIgnoresDirection() {
        assertEquals(segmentKey("a", "b"), segmentKey("b", "a"))
        assertNotEquals(segmentKey("a", "b"), segmentKey("a", "c"))
    }

    @Test
    fun travelSegmentsFollowListOrder() {
        val segments = route("a", "b", "c").travelSegments()
        assertEquals(listOf("a" to "b", "b" to "c"), segments.map { it.from.id to it.to.id })
        assertEquals(listOf(2, 3), segments.map { it.toNumber })
        assertFalse(segments.any { it.closing })
    }

    @Test
    fun travelSegmentsReversedAndLoopedAddClosingSegment() {
        val segments = route("a", "b", "c").copy(reversed = true, loop = true).travelSegments()
        assertEquals(listOf("c" to "b", "b" to "a", "a" to "c"), segments.map { it.from.id to it.to.id })
        assertEquals(listOf(2, 1, 3), segments.map { it.toNumber })
        assertEquals(listOf(false, false, true), segments.map { it.closing })
    }

    @Test
    fun travelSegmentsEmptyBelowTwoPoints() {
        assertTrue(route("a").travelSegments().isEmpty())
        assertTrue(route("a").copy(loop = true).travelSegments().isEmpty())
    }

    @Test
    fun missingSegmentSettingsAreDefaults() {
        val settings = SegmentSettings(dwellSeconds = 5)
        val r = route("a", "b").copy(segments = mapOf(segmentKey("a", "b") to settings))
        assertEquals(settings, r.segmentSettings("b", "a"))
        assertEquals(SegmentSettings(), r.segmentSettings("a", "c"))
    }

    @Test
    fun presetResolvesFromSettingsAndCustomIsClamped() {
        val settings = JoystickSettings(runSpeedMps = 3.5f)
        assertEquals(3.5f, SpeedChoice.Preset(JoystickPreset.RUN).resolveMps(settings), 0f)
        assertEquals(JOYSTICK_MAX_SPEED_MPS, SpeedChoice.Custom(1_000f).resolveMps(settings), 0f)
    }

    @Test
    fun validRouteNeedsTwoPointsAndAName() {
        assertTrue(route("a", "b").isValid())
        assertFalse(route("a").isValid())
        assertFalse(route("a", "b").copy(name = " ").isValid())
        assertTrue(route("a").copy(name = "").isValid(requireName = false, minPoints = 0))
    }

    @Test
    fun outOfRangeValuesFailValidation() {
        val base = route("a", "b")
        assertFalse(base.copy(points = listOf(RoutePoint("a", Double.NaN, 0.0), RoutePoint("b", 0.0, 0.0))).isValid())
        assertFalse(base.copy(defaultSpeed = SpeedChoice.Custom(0f)).isValid())
        val key = segmentKey("a", "b")
        assertFalse(base.copy(segments = mapOf(key to SegmentSettings(dwellSeconds = -1))).isValid())
        assertFalse(base.copy(segments = mapOf(key to SegmentSettings(variationPercent = 60))).isValid())
    }
}
