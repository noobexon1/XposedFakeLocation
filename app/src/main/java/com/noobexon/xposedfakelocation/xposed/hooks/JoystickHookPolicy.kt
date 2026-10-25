package com.noobexon.xposedfakelocation.xposed.hooks

import com.noobexon.xposedfakelocation.data.JOYSTICK_SPEED_ACCURACY
import com.noobexon.xposedfakelocation.data.JOYSTICK_STALE_MS
import com.noobexon.xposedfakelocation.data.model.JoystickState
import kotlin.math.abs

/** Speed/bearing values the hooks should report, plus whether randomize may run. */
internal data class MotionValues(
    val override: Boolean,
    val randomize: Boolean,
    val speed: Float,
    val speedAccuracy: Float,
    val bearing: Float,
)

/** Pure decisions about honouring the joystick motion override published by the manager. */
internal object JoystickHookPolicy {
    /**
     * Returns [state] only when it is active and was refreshed within [JOYSTICK_STALE_MS] of
     * [nowMillis]. The window is symmetric so a small wall-clock adjustment between the manager's
     * write and the hook's read doesn't drop a live session.
     */
    fun activeState(state: JoystickState?, nowMillis: Long): JoystickState? {
        if (state == null || !state.active) return null
        return state.takeIf { abs(nowMillis - it.updatedAt) <= JOYSTICK_STALE_MS }
    }

    /**
     * Resolves motion fields for one hook read. [joystick] must already be filtered through
     * [activeState]. While a joystick is active, randomize and the user's custom speed settings
     * are paused so they can't fight the steered movement.
     */
    fun resolveMotion(
        joystick: JoystickState?,
        useRandomize: Boolean,
        useSpeed: Boolean,
        speed: Float,
        useSpeedAccuracy: Boolean,
        speedAccuracy: Float,
    ): MotionValues = if (joystick != null) {
        MotionValues(
            override = true,
            randomize = false,
            speed = joystick.speed,
            speedAccuracy = JOYSTICK_SPEED_ACCURACY,
            bearing = joystick.bearing,
        )
    } else {
        MotionValues(
            override = false,
            randomize = useRandomize,
            speed = if (useSpeed) speed else 0f,
            speedAccuracy = if (useSpeedAccuracy) speedAccuracy else 0f,
            bearing = 0f,
        )
    }
}
