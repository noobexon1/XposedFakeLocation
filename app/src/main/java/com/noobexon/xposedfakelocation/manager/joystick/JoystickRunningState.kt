package com.noobexon.xposedfakelocation.manager.joystick

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether a joystick session is running in this process; lets route playback refuse to start. */
object JoystickRunningState {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    internal fun set(running: Boolean) {
        _running.value = running
    }
}
