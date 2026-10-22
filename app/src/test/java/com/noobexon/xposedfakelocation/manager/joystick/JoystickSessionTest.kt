package com.noobexon.xposedfakelocation.manager.joystick

import com.noobexon.xposedfakelocation.data.model.JoystickSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JoystickSessionTest {
    private fun session() = JoystickSession(
        scope = CoroutineScope(Job()),
        startLatitude = 10.0,
        startLongitude = 20.0,
        settings = MutableStateFlow(JoystickSettings(maxSpeedMps = 3f)),
        publish = {},
        monotonicClock = { 0L },
        wallClock = { 42L },
    )

    @Test
    fun writePolicyThrottlesMovingAndHeartbeatsIdle() {
        assertFalse(JoystickWritePolicy.shouldWrite(100, 0, moving = true, wasMoving = true))
        assertTrue(JoystickWritePolicy.shouldWrite(250, 0, moving = true, wasMoving = true))
        assertTrue(JoystickWritePolicy.shouldWrite(10, 0, moving = false, wasMoving = true))
        assertFalse(JoystickWritePolicy.shouldWrite(1_999, 0, moving = false, wasMoving = false))
        assertTrue(JoystickWritePolicy.shouldWrite(2_000, 0, moving = false, wasMoving = false))
    }

    @Test
    fun recentPositionsForgetsOldestBeyondCapacity() {
        val recent = RecentPositions(capacity = 2)
        recent.add(1.0, 1.0)
        recent.add(2.0, 2.0)
        recent.add(3.0, 3.0)
        assertFalse(recent.contains(1.0, 1.0))
        assertTrue(recent.contains(2.0, 2.0))
        assertTrue(recent.contains(3.0, 3.0))
    }

    @Test
    fun firstTickPublishesActiveStationaryFrame() {
        val frame = session().tick(1_000)
        assertNotNull(frame)
        frame!!
        assertTrue(frame.state.active)
        assertEquals(0f, frame.state.speed, 0f)
        assertEquals(42L, frame.state.updatedAt)
        assertEquals(10.0, frame.latitude, 0.0)
        assertEquals(20.0, frame.longitude, 0.0)
    }

    @Test
    fun movingPublishesAtMostEvery250Ms() {
        val s = session()
        s.setInput(0f, 1f)
        assertNotNull(s.tick(0))
        assertNull(s.tick(50))
        assertNull(s.tick(200))
        val frame = s.tick(250)
        assertNotNull(frame)
        assertTrue(frame!!.latitude > 10.0)
        assertEquals(3f, frame.state.speed, 1e-4f)
    }

    @Test
    fun releasePublishesImmediatelyWithZeroSpeed() {
        val s = session()
        s.setInput(0f, 1f)
        s.tick(0)
        s.tick(100)
        s.setInput(0f, 0f)
        val frame = s.tick(150)
        assertNotNull(frame)
        assertEquals(0f, frame!!.state.speed, 0f)
        assertTrue(frame.state.active)
    }

    @Test
    fun idleSessionHeartbeatsEveryTwoSeconds() {
        val s = session()
        assertNotNull(s.tick(0))
        assertNull(s.tick(1_000))
        assertNotNull(s.tick(2_000))
    }

    @Test
    fun echoOfPublishedPositionIsIgnored() {
        val s = session()
        s.setInput(0f, 1f)
        s.tick(0)
        val published = s.tick(250)!!
        s.tick(300)
        val before = s.currentState
        s.onExternalMarker(published.latitude, published.longitude)
        assertEquals(before, s.currentState)
    }

    @Test
    fun externalMarkerChangeTeleports() {
        val s = session()
        s.tick(0)
        s.onExternalMarker(50.0, 8.0)
        assertEquals(50.0, s.currentState.latitude, 0.0)
        assertEquals(8.0, s.currentState.longitude, 0.0)
    }

    @Test
    fun stopReturnsInactiveFrameAtCurrentPosition() {
        val s = session()
        s.setInput(0f, 1f)
        s.tick(0)
        s.tick(250)
        val frame = s.stop()
        assertFalse(frame.state.active)
        assertEquals(0f, frame.state.speed, 0f)
        assertEquals(s.currentState.latitude, frame.latitude, 0.0)
    }
}
