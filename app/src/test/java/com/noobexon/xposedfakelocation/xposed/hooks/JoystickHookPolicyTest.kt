package com.noobexon.xposedfakelocation.xposed.hooks

import com.noobexon.xposedfakelocation.data.JOYSTICK_SPEED_ACCURACY
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

    @Test
    fun activeJoystickPausesRandomizeAndCustomSpeed() {
        val motion = JoystickHookPolicy.resolveMotion(
            joystick = state(),
            useRandomize = true,
            useSpeed = true, speed = 9f,
            useSpeedAccuracy = true, speedAccuracy = 7f,
        )
        assertEquals(
            MotionValues(override = true, randomize = false, speed = 1.4f, speedAccuracy = JOYSTICK_SPEED_ACCURACY, bearing = 90f),
            motion
        )
    }

    @Test
    fun idleJoystickStillOverridesWithZeroSpeed() {
        val idle = JoystickState(active = true, bearing = 45f, speed = 0f, updatedAt = now)
        val motion = JoystickHookPolicy.resolveMotion(idle, false, true, 9f, false, 0f)
        assertEquals(true, motion.override)
        assertEquals(0f, motion.speed, 0f)
        assertEquals(45f, motion.bearing, 0f)
    }

    @Test
    fun withoutJoystickUserSettingsApply() {
        val motion = JoystickHookPolicy.resolveMotion(null, true, true, 9f, true, 7f)
        assertEquals(MotionValues(override = false, randomize = true, speed = 9f, speedAccuracy = 7f, bearing = 0f), motion)
    }

    @Test
    fun withoutJoystickDisabledSpeedSettingsReportZero() {
        val motion = JoystickHookPolicy.resolveMotion(null, false, false, 9f, false, 7f)
        assertEquals(MotionValues(override = false, randomize = false, speed = 0f, speedAccuracy = 0f, bearing = 0f), motion)
    }
}
