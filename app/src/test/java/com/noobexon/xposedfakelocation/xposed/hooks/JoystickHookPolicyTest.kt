package com.noobexon.xposedfakelocation.xposed.hooks

import com.noobexon.xposedfakelocation.data.JOYSTICK_STALE_MS
import com.noobexon.xposedfakelocation.data.model.JoystickState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JoystickHookPolicyTest {
    private val now = 1_000_000L
    private fun state(active: Boolean = true, updatedAt: Long = now) =
        JoystickState(active = active, bearing = 90f, speed = 1.4f, updatedAt = updatedAt)

    @Test
    fun missingStateIsIgnored() {
        assertNull(JoystickHookPolicy.activeState(null, now))
    }

    @Test
    fun inactiveStateIsIgnored() {
        assertNull(JoystickHookPolicy.activeState(state(active = false), now))
    }

    @Test
    fun freshActiveStateIsReturned() {
        val fresh = state(updatedAt = now - 100)
        assertEquals(fresh, JoystickHookPolicy.activeState(fresh, now))
    }

    @Test
    fun stateAtExactlyTheStaleLimitIsStillHonoured() {
        val edge = state(updatedAt = now - JOYSTICK_STALE_MS)
        assertEquals(edge, JoystickHookPolicy.activeState(edge, now))
    }

    @Test
    fun staleStateIsIgnored() {
        assertNull(JoystickHookPolicy.activeState(state(updatedAt = now - JOYSTICK_STALE_MS - 1), now))
    }

    @Test
    fun slightlyFutureTimestampIsHonoured() {
        val future = state(updatedAt = now + 1_000)
        assertEquals(future, JoystickHookPolicy.activeState(future, now))
    }
}
