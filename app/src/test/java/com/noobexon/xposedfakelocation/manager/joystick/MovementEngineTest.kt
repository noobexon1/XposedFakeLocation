package com.noobexon.xposedfakelocation.manager.joystick

import com.noobexon.xposedfakelocation.data.GeoMath
import com.noobexon.xposedfakelocation.data.model.JoystickMode
import com.noobexon.xposedfakelocation.data.model.JoystickPreset
import com.noobexon.xposedfakelocation.data.model.JoystickSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementEngineTest {
    private val dynamic = JoystickSettings(mode = JoystickMode.DYNAMIC, maxSpeedMps = 3f)
    private val constantRun = JoystickSettings(
        mode = JoystickMode.CONSTANT,
        runSpeedMps = 3f,
        selectedPreset = JoystickPreset.RUN,
    )
    private val origin = MovementState(latitude = 10.0, longitude = 20.0, bearingDegrees = 42f, speedMps = 0f)

    @Test
    fun inputInsideDeadZoneDoesNotMoveAndKeepsBearing() {
        val result = MovementEngine.step(origin, MovementInput(0.1f, 0f), dynamic, 1.0)
        assertEquals(origin.latitude, result.latitude, 0.0)
        assertEquals(origin.longitude, result.longitude, 0.0)
        assertEquals(42f, result.bearingDegrees, 0f)
        assertEquals(0f, result.speedMps, 0f)
    }

    @Test
    fun dynamicSpeedScalesLinearlyOutsideDeadZone() {
        assertEquals(3f, MovementEngine.speedFor(MovementInput(0f, 1f), dynamic), 1e-4f)
        assertEquals(1.5f, MovementEngine.speedFor(MovementInput(0f, 0.575f), dynamic), 1e-4f)
    }

    @Test
    fun deflectionBeyondUnitCircleIsClamped() {
        assertEquals(3f, MovementEngine.speedFor(MovementInput(1f, 1f), dynamic), 1e-4f)
    }

    @Test
    fun constantModeUsesSelectedPresetRegardlessOfDeflection() {
        assertEquals(3f, MovementEngine.speedFor(MovementInput(0.3f, 0f), constantRun), 0f)
        assertEquals(3f, MovementEngine.speedFor(MovementInput(1f, 0f), constantRun), 0f)
    }

    @Test
    fun bearingFollowsCompassConvention() {
        assertEquals(0f, MovementEngine.bearingFor(MovementInput(0f, 1f)), 1e-3f)
        assertEquals(90f, MovementEngine.bearingFor(MovementInput(1f, 0f)), 1e-3f)
        assertEquals(180f, MovementEngine.bearingFor(MovementInput(0f, -1f)), 1e-3f)
        assertEquals(270f, MovementEngine.bearingFor(MovementInput(-1f, 0f)), 1e-3f)
        assertEquals(225f, MovementEngine.bearingFor(MovementInput(-1f, -1f)), 1e-3f)
    }

    @Test
    fun fullUpForOneSecondMovesNorthByMaxSpeed() {
        val result = MovementEngine.step(origin, MovementInput(0f, 1f), dynamic, 1.0)
        val (expectedLat, expectedLon) = GeoMath.destination(10.0, 20.0, 0.0, 3.0)
        assertEquals(expectedLat, result.latitude, 1e-12)
        assertEquals(expectedLon, result.longitude, 1e-12)
        assertEquals(0f, result.bearingDegrees, 1e-3f)
        assertEquals(3f, result.speedMps, 1e-4f)
        assertTrue(result.latitude > origin.latitude)
    }

    @Test
    fun zeroElapsedTimeReportsMotionWithoutMoving() {
        val result = MovementEngine.step(origin, MovementInput(1f, 0f), dynamic, 0.0)
        assertEquals(origin.latitude, result.latitude, 0.0)
        assertEquals(origin.longitude, result.longitude, 0.0)
        assertEquals(90f, result.bearingDegrees, 1e-3f)
        assertEquals(3f, result.speedMps, 1e-4f)
    }
}
