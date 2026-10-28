package com.noobexon.xposedfakelocation.manager.route

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RouteSessionTest {
    private val plan = RoutePlan(
        listOf(Leg(0.0, 0.0, 0.0009, 0.0, 100.0, 0f, 10f, 0.0, 0.0, 2)),
        loop = false,
    )

    private fun session() = RouteSession(
        scope = CoroutineScope(Job()),
        plan = plan,
        publish = {},
        monotonicClock = { 0L },
        wallClock = { 42L },
        random = Random(1),
    )

    @Test
    fun firstTickPublishesActiveFrameAtStart() {
        val frame = session().tick(0)!!
        assertTrue(frame.state.active)
        assertEquals(10f, frame.state.speed, 0f)
        assertEquals(42L, frame.state.updatedAt)
        assertEquals(0.0, frame.latitude, 0.0)
    }

    @Test
    fun movingWritesAreThrottled() {
        val s = session()
        assertNotNull(s.tick(0))
        assertNull(s.tick(100))
        val frame = s.tick(250)!!
        assertTrue(frame.latitude > 0.0)
        assertEquals(2.5, s.status.value.distanceCoveredM, 1e-6)
    }

    @Test
    fun pausePublishesStationaryFrameImmediately() {
        val s = session()
        s.tick(0)
        s.pause()
        val frame = s.tick(100)!!
        assertTrue(frame.state.active)
        assertEquals(0f, frame.state.speed, 0f)
        assertEquals(RoutePhase.PAUSED, s.status.value.phase)
    }

    @Test
    fun finishedRouteStaysActiveAtZeroSpeedWithHeartbeat() {
        val s = session()
        s.tick(0)
        val finished = s.tick(20_000)!!
        assertEquals(0f, finished.state.speed, 0f)
        assertTrue(finished.state.active)
        assertEquals(RoutePhase.FINISHED, s.status.value.phase)
        assertEquals(1f, s.status.value.progress, 0f)
        assertNull(s.tick(21_000))
        assertNotNull(s.tick(22_000))
    }

    @Test
    fun seekForcesTheNextWrite() {
        val s = session()
        s.tick(0)
        assertNull(s.tick(100))
        s.seek(0.5f)
        assertNotNull(s.tick(150))
        assertEquals(50.5, s.status.value.distanceCoveredM, 1e-6)
    }

    @Test
    fun replayAfterFinishStartsOver() {
        val s = session()
        s.tick(0)
        s.tick(20_000)
        s.play()
        assertEquals(RoutePhase.RUNNING, s.status.value.phase)
        assertEquals(0.0, s.status.value.distanceCoveredM, 0.0)
    }

    @Test
    fun multiplierCyclesThroughPresets() {
        val s = session()
        s.cycleMultiplier()
        assertEquals(2f, s.status.value.multiplier, 0f)
        s.cycleMultiplier()
        assertEquals(0.5f, s.status.value.multiplier, 0f)
        s.cycleMultiplier()
        assertEquals(1f, s.status.value.multiplier, 0f)
    }

    @Test
    fun startPointCountsAsOwnEcho() {
        assertTrue(session().isOwnEcho(0.0, 0.0))
        assertFalse(session().isOwnEcho(5.0, 5.0))
    }

    @Test
    fun stopReturnsInactiveFrame() {
        val s = session()
        s.tick(0)
        val last = s.stop()
        assertFalse(last.state.active)
        assertEquals(0f, last.state.speed, 0f)
    }
}
