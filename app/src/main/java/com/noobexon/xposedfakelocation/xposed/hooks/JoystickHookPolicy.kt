package com.noobexon.xposedfakelocation.xposed.hooks

import com.noobexon.xposedfakelocation.data.JOYSTICK_STALE_MS
import com.noobexon.xposedfakelocation.data.model.JoystickState
import kotlin.math.abs

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
}
