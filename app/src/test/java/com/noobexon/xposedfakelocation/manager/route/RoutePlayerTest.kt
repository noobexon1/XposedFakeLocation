package com.noobexon.xposedfakelocation.manager.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class RoutePlayerTest {
    private fun leg(distance: Double, speed: Float = 1f, dwell: Double = 0.0, variation: Double = 0.0, end: Int = 2) =
        Leg(0.0, 0.0, 0.0, 0.0, distance, 0f, speed, variation, dwell, end)

    private val random = Random(42)

    private fun run(plan: RoutePlan, state: PlayerState, seconds: Double, multiplier: Float = 1f) =
        RoutePlayer.step(plan, state, seconds, multiplier, random)

    @Test
    fun advancesBySpeedTimesTime() {
        val plan = RoutePlan(listOf(leg(100.0, speed = 2f)), loop = false)
        val step = run(plan, PlayerState(), 3.0)
        assertEquals(6.0, step.state.distanceIntoLegM, 1e-9)
        assertEquals(2f, step.speedMps, 0f)
    }

    @Test
    fun carriesLeftoverDistanceIntoNextLeg() {
        val plan = RoutePlan(listOf(leg(10.0), leg(10.0, end = 3)), loop = false)
        val step = run(plan, PlayerState(), 12.0)
        assertEquals(1, step.state.legIndex)
        assertEquals(2.0, step.state.distanceIntoLegM, 1e-9)
    }

    @Test
    fun waitsAtLegEndWithZeroSpeed() {
        val plan = RoutePlan(listOf(leg(10.0, dwell = 5.0), leg(10.0, end = 3)), loop = false)
        val waiting = run(plan, PlayerState(), 13.0)
        assertEquals(0, waiting.state.legIndex)
        assertEquals(2.0, waiting.state.waitRemainingS, 1e-9)
        assertEquals(0f, waiting.speedMps, 0f)
        val moving = run(plan, waiting.state, 3.0)
        assertEquals(1, moving.state.legIndex)
        assertEquals(1.0, moving.state.distanceIntoLegM, 1e-9)
    }

    @Test
    fun oneTimeRouteFinishesAtTheEnd() {
        val plan = RoutePlan(listOf(leg(10.0)), loop = false)
        val step = run(plan, PlayerState(), 60.0)
        assertEquals(RoutePhase.FINISHED, step.state.phase)
        assertEquals(0f, step.speedMps, 0f)
        assertEquals(1f, RoutePlayer.progress(plan, step.state), 0f)
    }

    @Test
    fun loopWrapsToFirstLeg() {
        val plan = RoutePlan(listOf(leg(10.0), leg(10.0, end = 1)), loop = true)
        val step = run(plan, PlayerState(), 25.0)
        assertEquals(RoutePhase.RUNNING, step.state.phase)
        assertEquals(0, step.state.legIndex)
        assertEquals(5.0, step.state.distanceIntoLegM, 1e-9)
    }

    @Test
    fun multiplierScalesMovementAndReportedSpeed() {
        val plan = RoutePlan(listOf(leg(100.0, speed = 2f)), loop = false)
        val step = run(plan, PlayerState(), 3.0, multiplier = 2f)
        assertEquals(12.0, step.state.distanceIntoLegM, 1e-9)
        assertEquals(4f, step.speedMps, 0f)
    }

    @Test
    fun pausedDoesNotMove() {
        val plan = RoutePlan(listOf(leg(100.0)), loop = false)
        val step = run(plan, PlayerState(phase = RoutePhase.PAUSED, distanceIntoLegM = 4.0), 10.0)
        assertEquals(4.0, step.state.distanceIntoLegM, 0.0)
        assertEquals(0f, step.speedMps, 0f)
    }

    @Test
    fun seekMapsFractionToLegAndCancelsWait() {
        val plan = RoutePlan(listOf(leg(10.0), leg(10.0, end = 3)), loop = false)
        val sought = RoutePlayer.seek(plan, PlayerState(waitRemainingS = 4.0), 0.75f)
        assertEquals(1, sought.legIndex)
        assertEquals(5.0, sought.distanceIntoLegM, 1e-9)
        assertEquals(0.0, sought.waitRemainingS, 0.0)
        assertEquals(0.75f, RoutePlayer.progress(plan, sought), 1e-6f)
    }

    @Test
    fun seekingAFinishedRouteBackPausesIt() {
        val plan = RoutePlan(listOf(leg(10.0)), loop = false)
        val sought = RoutePlayer.seek(plan, PlayerState(phase = RoutePhase.FINISHED, distanceIntoLegM = 10.0), 0.2f)
        assertEquals(RoutePhase.PAUSED, sought.phase)
        assertEquals(2.0, sought.distanceIntoLegM, 1e-6)
    }

    @Test
    fun variationStaysInBoundsAndChangesSmoothly() {
        val plan = RoutePlan(listOf(leg(1_000_000.0, speed = 10f, variation = 0.2)), loop = false)
        var state = PlayerState()
        repeat(2_000) {
            val previous = state.variationFactor
            state = RoutePlayer.step(plan, state, 0.1, 1f, random).state
            assertTrue(state.variationFactor in 0.8..1.2)
            assertTrue(abs(state.variationFactor - previous) <= 0.01 + 1e-9)
        }
    }
}
