package com.noobexon.xposedfakelocation.data.model

import com.noobexon.xposedfakelocation.data.DEFAULT_JOYSTICK_MAX_SPEED
import com.noobexon.xposedfakelocation.data.DEFAULT_JOYSTICK_PRESET_BIKE
import com.noobexon.xposedfakelocation.data.DEFAULT_JOYSTICK_PRESET_DRIVE
import com.noobexon.xposedfakelocation.data.DEFAULT_JOYSTICK_PRESET_RUN
import com.noobexon.xposedfakelocation.data.DEFAULT_JOYSTICK_PRESET_WALK

/** How stick deflection maps to speed. */
enum class JoystickMode { DYNAMIC, CONSTANT }

/** Fixed-speed presets available in [JoystickMode.CONSTANT]. */
enum class JoystickPreset { WALK, RUN, BIKE, DRIVE }

/** Manager-only joystick configuration. All speeds are in m/s. */
data class JoystickSettings(
    val mode: JoystickMode = JoystickMode.DYNAMIC,
    val maxSpeedMps: Float = DEFAULT_JOYSTICK_MAX_SPEED,
    val walkSpeedMps: Float = DEFAULT_JOYSTICK_PRESET_WALK,
    val runSpeedMps: Float = DEFAULT_JOYSTICK_PRESET_RUN,
    val bikeSpeedMps: Float = DEFAULT_JOYSTICK_PRESET_BIKE,
    val driveSpeedMps: Float = DEFAULT_JOYSTICK_PRESET_DRIVE,
    val selectedPreset: JoystickPreset = JoystickPreset.WALK,
) {
    fun presetSpeedMps(preset: JoystickPreset = selectedPreset): Float = when (preset) {
        JoystickPreset.WALK -> walkSpeedMps
        JoystickPreset.RUN -> runSpeedMps
        JoystickPreset.BIKE -> bikeSpeedMps
        JoystickPreset.DRIVE -> driveSpeedMps
    }
}

/**
 * Motion override published by the joystick overlay through remote prefs and read by the hooks.
 * [updatedAt] is wall-clock millis so every process judges staleness against the same clock.
 */
data class JoystickState(
    val active: Boolean,
    val bearing: Float,
    val speed: Float,
    val updatedAt: Long,
)
