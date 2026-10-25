package com.noobexon.xposedfakelocation.manager.joystick

import com.noobexon.xposedfakelocation.data.GeoMath
import com.noobexon.xposedfakelocation.data.model.JoystickMode
import com.noobexon.xposedfakelocation.data.model.JoystickSettings
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/** Stick deflection in `[-1, 1]` on each axis; positive [y] points north (screen up). */
data class MovementInput(val x: Float, val y: Float) {
    companion object {
        val NONE = MovementInput(0f, 0f)
    }
}

/** Simulated position plus the bearing/speed that produced it. */
data class MovementState(
    val latitude: Double,
    val longitude: Double,
    val bearingDegrees: Float,
    val speedMps: Float,
)

/** Pure integration of joystick input into movement. */
object MovementEngine {
    const val DEAD_ZONE = 0.15f

    fun speedFor(input: MovementInput, settings: JoystickSettings): Float {
        val magnitude = min(1f, hypot(input.x, input.y))
        if (magnitude < DEAD_ZONE) return 0f
        return when (settings.mode) {
            JoystickMode.DYNAMIC -> settings.maxSpeedMps * ((magnitude - DEAD_ZONE) / (1f - DEAD_ZONE))
            JoystickMode.CONSTANT -> settings.presetSpeedMps()
        }
    }

    /** Compass bearing of [input]: up = 0°, right = 90°, in `[0, 360)`. */
    fun bearingFor(input: MovementInput): Float {
        val degrees = Math.toDegrees(atan2(input.x.toDouble(), input.y.toDouble())).toFloat()
        return (degrees + 360f) % 360f
    }

    /**
     * Advances [state] by [dtSeconds] of movement driven by [input]. Inside the dead zone the
     * position and last bearing are kept and speed drops to 0.
     */
    fun step(
        state: MovementState,
        input: MovementInput,
        settings: JoystickSettings,
        dtSeconds: Double,
    ): MovementState {
        val speed = speedFor(input, settings)
        if (speed == 0f) return state.copy(speedMps = 0f)
        val bearing = bearingFor(input)
        if (dtSeconds <= 0.0) return state.copy(bearingDegrees = bearing, speedMps = speed)
        val (latitude, longitude) = GeoMath.destination(
            state.latitude, state.longitude, bearing.toDouble(), speed * dtSeconds
        )
        return MovementState(latitude, longitude, bearing, speed)
    }
}
