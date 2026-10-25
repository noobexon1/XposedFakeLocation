# Joystick Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a floating joystick overlay that steers the spoofed location in real time, reporting realistic bearing and speed to target apps.

**Architecture:** A foreground service in the manager app draws a Compose overlay via `WindowManager`, integrates stick input into a moving position (`MovementEngine`, 20 Hz), and publishes the position plus a small `JoystickState` to LSPosed remote prefs (throttled to 250 ms, 2 s heartbeat). Hook-side `LocationUtil` honours a fresh joystick state by pausing randomize / custom speed and reporting the joystick's bearing and speed.

**Tech Stack:** Kotlin 2.2, Jetpack Compose (BOM 2024.12.01, Material 3), `androidx.lifecycle:lifecycle-service`, LSPosed modern API (libxposed 101), Gson, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-22-joystick-overlay-design.md`

## Global Constraints

- `minSdk = 29`, `compileSdk = targetSdk = 36`, JVM 21. No new dependencies other than `androidx.lifecycle:lifecycle-service`.
- Hook-side code (`xposed/**`) may depend on `data/**` but never on `manager/**`.
- New Gson-serialized models live in `com.noobexon.xposedfakelocation.data.model` (already kept by `proguard-rules.pro`).
- Speeds are **stored in m/s** and **displayed in km/h** (`MPS_TO_KMH = 3.6f`).
- Timing constants: stale after `5000 ms`, moving write interval `250 ms`, idle heartbeat `2000 ms`, tick `50 ms`, dead zone `0.15`, joystick speed accuracy `0.5 m/s`.
- Defaults: max speed `3.0 m/s`; presets walk `1.4`, run `3.0`, bike `5.5`, drive `14.0` m/s; selected preset `WALK`; mode `DYNAMIC`.
- Settings paused while the joystick is active: randomize, custom speed, custom speed accuracy. All others stay as configured.
- Every user-facing string is added to `values/`, `values-de/` and `values-zh/strings.xml` in the same task that introduces it.
- Unit tests: `.\gradlew.bat :app:testDebugUnitTest` (Windows PowerShell). Compile check: `.\gradlew.bat :app:assembleDebug`.
- Spec deviation (agreed simplification): the Settings screen does not get a "default mode" row — the mode chosen in the overlay is persisted and reused, which covers the same need.

## File Structure

| File | Status | Responsibility |
|---|---|---|
| `data/GeoMath.kt` | Create | Great-circle destination + longitude normalization (shared by hooks and manager) |
| `data/Constants.kt` | Modify | Joystick keys, defaults, timing constants |
| `data/model/Joystick.kt` | Create | `JoystickMode`, `JoystickPreset`, `JoystickSettings`, `JoystickState` |
| `data/model/SettingsBackup.kt` | Modify | Optional `JoystickBackup` section |
| `data/backup/SettingsBackupCodec.kt` | Modify | Drop invalid joystick section on import |
| `data/backup/SettingsBackupManager.kt` | Modify | Apply joystick section |
| `data/repository/PrefrencesRepository.kt` | Modify | Joystick state (remote) + joystick settings / overlay position (local) |
| `xposed/hooks/JoystickHookPolicy.kt` | Create | Pure staleness check + motion resolution |
| `xposed/utils/PreferencesUtil.kt` | Modify | `getJoystickState()` |
| `xposed/utils/LocationUtil.kt` | Modify | Use `GeoMath`; motion override (speed/bearing) |
| `xposed/hooks/LocationApiHooks.kt` | Modify | Hook `getBearing`/`hasSpeed`/`hasBearing`; gate speed getters on override |
| `manager/joystick/MovementEngine.kt` | Create | Pure stick → speed/bearing/position integration |
| `manager/joystick/JoystickSession.kt` | Create | Tick logic, write throttling, echo detection |
| `manager/joystick/JoystickOverlayWindow.kt` | Create | `WindowManager` + `ComposeView` host, drag/clamp |
| `manager/joystick/JoystickOverlayService.kt` | Create | Foreground service lifecycle, notification, stop conditions |
| `manager/joystick/ui/JoystickOverlay.kt` | Create | Overlay composables (pad, mode/preset chips, bubble) |
| `manager/joystick/JoystickButton.kt` | Create | Map-screen entry button + permission flow |
| `manager/ui/settings/SettingsModel.kt` | Modify | `JOYSTICK` category, joystick `NumericSetting`s, `showToggle` |
| `manager/ui/settings/SettingsViewModel.kt` | Modify | Joystick settings flow/setters, reset, export |
| `manager/ui/settings/SettingsScreen.kt` | Modify | Joystick section rows, toggle-less numeric rows |
| `manager/ui/map/MapScreen.kt` | Modify | Add `JoystickButton` to the top bar |
| `AndroidManifest.xml`, `gradle/libs.versions.toml`, `app/build.gradle.kts` | Modify | Permissions, service, dependency |

All Kotlin paths below are relative to `app/src/main/java/com/noobexon/xposedfakelocation/` (main) or `app/src/test/java/com/noobexon/xposedfakelocation/` (test) unless written in full.

---

### Task 1: GeoMath

**Files:**
- Create: `app/src/main/java/com/noobexon/xposedfakelocation/data/GeoMath.kt`
- Modify: `app/src/main/java/com/noobexon/xposedfakelocation/xposed/utils/LocationUtil.kt` (imports, `getRandomLocation`)
- Test: `app/src/test/java/com/noobexon/xposedfakelocation/data/GeoMathTest.kt`

**Interfaces:**
- Produces: `GeoMath.destination(latitude: Double, longitude: Double, bearingDegrees: Double, distanceMeters: Double): Pair<Double, Double>`, `GeoMath.normalizeLongitude(longitude: Double): Double`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.noobexon.xposedfakelocation.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {
    private val metersPerDegree = Math.toRadians(1.0) * RADIUS_EARTH

    @Test
    fun zeroDistanceReturnsSamePoint() {
        val (lat, lon) = GeoMath.destination(32.0, 34.8, 123.0, 0.0)
        assertEquals(32.0, lat, 1e-12)
        assertEquals(34.8, lon, 1e-12)
    }

    @Test
    fun hundredMetersNorthIncreasesLatitudeOnly() {
        val (lat, lon) = GeoMath.destination(0.0, 0.0, 0.0, 100.0)
        assertEquals(100.0 / metersPerDegree, lat, 1e-9)
        assertEquals(0.0, lon, 1e-12)
    }

    @Test
    fun eastwardDegreesScaleWithCosineOfLatitude() {
        val (_, lonAtEquator) = GeoMath.destination(0.0, 0.0, 90.0, 100.0)
        val (_, lonAt60) = GeoMath.destination(60.0, 0.0, 90.0, 100.0)
        assertEquals(2.0, lonAt60 / lonAtEquator, 1e-3)
    }

    @Test
    fun crossingAntimeridianWrapsLongitude() {
        val (_, lon) = GeoMath.destination(0.0, 179.9995, 90.0, 200.0)
        assertEquals(-179.9987, lon, 1e-4)
    }

    @Test
    fun movingPastNorthPoleStaysInValidRange() {
        val (lat, lon) = GeoMath.destination(89.9995, 0.0, 0.0, 200.0)
        assertTrue(lat.isFinite() && lat in -90.0..90.0)
        assertTrue(lon.isFinite() && lon >= -180.0 && lon < 180.0)
    }

    @Test
    fun normalizeLongitudeKeepsHalfOpenRange() {
        assertEquals(-180.0, GeoMath.normalizeLongitude(180.0), 0.0)
        assertEquals(179.0, GeoMath.normalizeLongitude(-181.0), 1e-9)
        assertEquals(-180.0, GeoMath.normalizeLongitude(540.0), 0.0)
        assertEquals(10.0, GeoMath.normalizeLongitude(10.0), 1e-12)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.noobexon.xposedfakelocation.data.GeoMathTest"`
Expected: compilation FAIL, `Unresolved reference 'GeoMath'`.

- [ ] **Step 3: Implement `GeoMath`**

```kotlin
package com.noobexon.xposedfakelocation.data

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Spherical-earth geometry helpers shared by the manager app and the hook side. */
object GeoMath {
    /**
     * Returns the point reached by travelling [distanceMeters] from ([latitude], [longitude]) along
     * the great circle starting at [bearingDegrees] (0 = north, 90 = east).
     *
     * @return (latitude, longitude) in decimal degrees; latitude clamped to `[-90, 90]`, longitude
     *   normalized to `[-180, 180)`.
     */
    fun destination(
        latitude: Double,
        longitude: Double,
        bearingDegrees: Double,
        distanceMeters: Double,
    ): Pair<Double, Double> {
        val angularDistance = distanceMeters / RADIUS_EARTH
        val bearing = Math.toRadians(bearingDegrees)
        val latRad = Math.toRadians(latitude)
        val lonRad = Math.toRadians(longitude)

        val sinLat = sin(latRad)
        val cosLat = cos(latRad)
        val sinDistance = sin(angularDistance)
        val cosDistance = cos(angularDistance)

        val sinNewLat = (sinLat * cosDistance + cosLat * sinDistance * cos(bearing)).coerceIn(-1.0, 1.0)
        val newLatRad = asin(sinNewLat)
        val newLonRad = lonRad + atan2(
            sin(bearing) * sinDistance * cosLat,
            cosDistance - sinLat * sinNewLat
        )

        val newLat = Math.toDegrees(newLatRad).coerceIn(-90.0, 90.0)
        return newLat to normalizeLongitude(Math.toDegrees(newLonRad))
    }

    /** Wraps [longitude] (degrees) into `[-180, 180)`. */
    fun normalizeLongitude(longitude: Double): Double = ((longitude + 180) % 360 + 360) % 360 - 180
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.noobexon.xposedfakelocation.data.GeoMathTest"`
Expected: `BUILD SUCCESSFUL`, 6 tests passed.

- [ ] **Step 5: Refactor `LocationUtil.getRandomLocation` onto `GeoMath`**

In `xposed/utils/LocationUtil.kt`:

Remove these imports:

```kotlin
import com.noobexon.xposedfakelocation.data.PI
import com.noobexon.xposedfakelocation.data.RADIUS_EARTH
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
```

Add:

```kotlin
import com.noobexon.xposedfakelocation.data.GeoMath
```

Replace the whole body of `getRandomLocation` (keep its KDoc) with:

```kotlin
    private fun getRandomLocation(lat: Double, lon: Double, radiusInMeters: Double): Pair<Double, Double> {
        val distance = radiusInMeters * sqrt(Random.nextDouble())
        val bearing = 360.0 * Random.nextDouble()
        return GeoMath.destination(lat, lon, bearing, distance)
    }
```

(`kotlin.math.sqrt` and `kotlin.random.Random` imports stay.)

- [ ] **Step 6: Run all unit tests and compile**

Run: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/noobexon/xposedfakelocation/data/GeoMath.kt app/src/test/java/com/noobexon/xposedfakelocation/data/GeoMathTest.kt app/src/main/java/com/noobexon/xposedfakelocation/xposed/utils/LocationUtil.kt
git commit -m "feat: add GeoMath destination helper and reuse it for randomize"
```

---

### Task 2: Joystick models, constants and MovementEngine

**Files:**
- Modify: `data/Constants.kt` (append a `// JOYSTICK` block)
- Create: `data/model/Joystick.kt`
- Create: `manager/joystick/MovementEngine.kt`
- Test: `app/src/test/java/com/noobexon/xposedfakelocation/manager/joystick/MovementEngineTest.kt`

**Interfaces:**
- Consumes: `GeoMath.destination` (Task 1)
- Produces:
  - Constants: `KEY_JOYSTICK_STATE`, `KEY_JOYSTICK_MODE`, `KEY_JOYSTICK_MAX_SPEED`, `KEY_JOYSTICK_PRESET_WALK/RUN/BIKE/DRIVE`, `KEY_JOYSTICK_SELECTED_PRESET`, `KEY_JOYSTICK_OVERLAY_X/Y`, `DEFAULT_JOYSTICK_MAX_SPEED`, `DEFAULT_JOYSTICK_PRESET_WALK/RUN/BIKE/DRIVE`, `JOYSTICK_STALE_MS`, `JOYSTICK_SPEED_ACCURACY`, `JOYSTICK_MAX_SPEED_MPS`, `MPS_TO_KMH`
  - `enum class JoystickMode { DYNAMIC, CONSTANT }`, `enum class JoystickPreset { WALK, RUN, BIKE, DRIVE }`
  - `data class JoystickSettings(mode, maxSpeedMps, walkSpeedMps, runSpeedMps, bikeSpeedMps, driveSpeedMps, selectedPreset)` with `presetSpeedMps(preset = selectedPreset): Float`; all params have defaults
  - `data class JoystickState(active: Boolean, bearing: Float, speed: Float, updatedAt: Long)`
  - `data class MovementInput(x: Float, y: Float)` (+ `MovementInput.NONE`), `data class MovementState(latitude, longitude, bearingDegrees: Float, speedMps: Float)`
  - `MovementEngine.DEAD_ZONE`, `speedFor(input, settings): Float`, `bearingFor(input): Float`, `step(state, input, settings, dtSeconds: Double): MovementState`

- [ ] **Step 1: Add constants**

Append to the end of `data/Constants.kt`:

```kotlin

// JOYSTICK
const val KEY_JOYSTICK_STATE = "joystick_state"
const val KEY_JOYSTICK_MODE = "joystick_mode"
const val KEY_JOYSTICK_MAX_SPEED = "joystick_max_speed"
const val KEY_JOYSTICK_PRESET_WALK = "joystick_preset_walk"
const val KEY_JOYSTICK_PRESET_RUN = "joystick_preset_run"
const val KEY_JOYSTICK_PRESET_BIKE = "joystick_preset_bike"
const val KEY_JOYSTICK_PRESET_DRIVE = "joystick_preset_drive"
const val KEY_JOYSTICK_SELECTED_PRESET = "joystick_selected_preset"
const val KEY_JOYSTICK_OVERLAY_X = "joystick_overlay_x"
const val KEY_JOYSTICK_OVERLAY_Y = "joystick_overlay_y"

const val DEFAULT_JOYSTICK_MAX_SPEED = 3.0f
const val DEFAULT_JOYSTICK_PRESET_WALK = 1.4f
const val DEFAULT_JOYSTICK_PRESET_RUN = 3.0f
const val DEFAULT_JOYSTICK_PRESET_BIKE = 5.5f
const val DEFAULT_JOYSTICK_PRESET_DRIVE = 14.0f

/** Hooks ignore a joystick state older than this, so a killed overlay can't freeze the motion override. */
const val JOYSTICK_STALE_MS = 5_000L
const val JOYSTICK_SPEED_ACCURACY = 0.5f
/** Upper bound accepted for imported joystick speeds. */
const val JOYSTICK_MAX_SPEED_MPS = 100f
const val MPS_TO_KMH = 3.6f
```

- [ ] **Step 2: Create the models**

`data/model/Joystick.kt`:

```kotlin
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
```

- [ ] **Step 3: Write the failing MovementEngine test**

```kotlin
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
```

- [ ] **Step 4: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.noobexon.xposedfakelocation.manager.joystick.MovementEngineTest"`
Expected: compilation FAIL, `Unresolved reference 'MovementEngine'`.

- [ ] **Step 5: Implement MovementEngine**

`manager/joystick/MovementEngine.kt`:

```kotlin
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
            JoystickMode.DYNAMIC -> settings.maxSpeedMps * (magnitude - DEAD_ZONE) / (1f - DEAD_ZONE)
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
```

- [ ] **Step 6: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.noobexon.xposedfakelocation.manager.joystick.MovementEngineTest"`
Expected: `BUILD SUCCESSFUL`, 7 tests passed.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/noobexon/xposedfakelocation/data/Constants.kt app/src/main/java/com/noobexon/xposedfakelocation/data/model/Joystick.kt app/src/main/java/com/noobexon/xposedfakelocation/manager/joystick/MovementEngine.kt app/src/test/java/com/noobexon/xposedfakelocation/manager/joystick/MovementEngineTest.kt
git commit -m "feat: add joystick models and movement engine"
```

---

### Task 3: Joystick state persistence and hook staleness policy

**Files:**
- Modify: `data/repository/PrefrencesRepository.kt` (new region after "Last Clicked Location")
- Modify: `xposed/utils/PreferencesUtil.kt`
- Create: `xposed/hooks/JoystickHookPolicy.kt`
- Test: `app/src/test/java/com/noobexon/xposedfakelocation/xposed/hooks/JoystickHookPolicyTest.kt`

**Interfaces:**
- Consumes: `JoystickState`, `KEY_JOYSTICK_STATE`, `JOYSTICK_STALE_MS` (Task 2)
- Produces:
  - `PreferencesRepository.saveJoystickState(state: JoystickState)` (non-suspend)
  - `PreferencesRepository.saveJoystickFrame(latitude: Double, longitude: Double, state: JoystickState)` (non-suspend, single commit)
  - `PreferencesUtil.getJoystickState(): JoystickState?`
  - `internal object JoystickHookPolicy { fun activeState(state: JoystickState?, nowMillis: Long): JoystickState? }`

- [ ] **Step 1: Write the failing test**

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.noobexon.xposedfakelocation.xposed.hooks.JoystickHookPolicyTest"`
Expected: compilation FAIL, `Unresolved reference 'JoystickHookPolicy'`.

- [ ] **Step 3: Implement the policy**

`xposed/hooks/JoystickHookPolicy.kt`:

```kotlin
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.noobexon.xposedfakelocation.xposed.hooks.JoystickHookPolicyTest"`
Expected: `BUILD SUCCESSFUL`, 6 tests passed.

- [ ] **Step 5: Add the hook-side getter**

In `xposed/utils/PreferencesUtil.kt`, add imports:

```kotlin
import com.noobexon.xposedfakelocation.data.KEY_JOYSTICK_STATE
import com.noobexon.xposedfakelocation.data.model.JoystickState
```

and, directly after `fun getHideFakeLocationToast()`, add:

```kotlin
    fun getJoystickState(): JoystickState? = getPreference(KEY_JOYSTICK_STATE)
```

(The generic `else` branch of `getPreference` parses it from JSON.)

- [ ] **Step 6: Add repository writers**

In `data/repository/PrefrencesRepository.kt`, add imports:

```kotlin
import com.noobexon.xposedfakelocation.data.KEY_JOYSTICK_STATE
import com.noobexon.xposedfakelocation.data.model.JoystickState
```

and insert this region directly after `// endregion` of "Last Clicked Location (remote)":

```kotlin

    // region Joystick State (remote)
    /** Publishes the joystick motion override without touching the marker. */
    fun saveJoystickState(state: JoystickState) =
        editRemote { putString(KEY_JOYSTICK_STATE, gson.toJson(state)) }

    /** Writes the marker position and joystick state in a single remote-prefs commit. */
    fun saveJoystickFrame(latitude: Double, longitude: Double, state: JoystickState) = editRemote {
        putString(KEY_LAST_CLICKED_LOCATION, gson.toJson(LastClickedLocation(latitude, longitude)))
        putString(KEY_JOYSTICK_STATE, gson.toJson(state))
    }
    // endregion
```

- [ ] **Step 7: Run all unit tests and compile**

Run: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/noobexon/xposedfakelocation/xposed/hooks/JoystickHookPolicy.kt app/src/test/java/com/noobexon/xposedfakelocation/xposed/hooks/JoystickHookPolicyTest.kt app/src/main/java/com/noobexon/xposedfakelocation/xposed/utils/PreferencesUtil.kt app/src/main/java/com/noobexon/xposedfakelocation/data/repository/PrefrencesRepository.kt
git commit -m "feat: persist joystick state and add hook staleness policy"
```

---

### Task 4: Hook-side motion override

**Files:**
- Modify: `xposed/hooks/JoystickHookPolicy.kt` (add `MotionValues`, `resolveMotion`)
- Modify: `xposed/utils/LocationUtil.kt` (`bearing`, `motionOverride`, `updateLocation`, `createFakeLocation`)
- Modify: `xposed/hooks/LocationApiHooks.kt` (`hookLocation`)
- Test: `app/src/test/java/com/noobexon/xposedfakelocation/xposed/hooks/JoystickHookPolicyTest.kt` (extend)

**Interfaces:**
- Consumes: `JoystickHookPolicy.activeState`, `PreferencesUtil.getJoystickState` (Task 3), `JOYSTICK_SPEED_ACCURACY` (Task 2)
- Produces:
  - `internal data class MotionValues(override: Boolean, randomize: Boolean, speed: Float, speedAccuracy: Float, bearing: Float)`
  - `JoystickHookPolicy.resolveMotion(joystick: JoystickState?, useRandomize: Boolean, useSpeed: Boolean, speed: Float, useSpeedAccuracy: Boolean, speedAccuracy: Float): MotionValues`
  - `LocationUtil.bearing: Float`, `LocationUtil.motionOverride: Boolean` (read-only)

- [ ] **Step 1: Write the failing tests**

Append inside `JoystickHookPolicyTest`:

```kotlin
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
```

and add the import `import com.noobexon.xposedfakelocation.data.JOYSTICK_SPEED_ACCURACY`.

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.noobexon.xposedfakelocation.xposed.hooks.JoystickHookPolicyTest"`
Expected: compilation FAIL, `Unresolved reference 'resolveMotion'` / `'MotionValues'`.

- [ ] **Step 3: Implement `resolveMotion`**

In `xposed/hooks/JoystickHookPolicy.kt` add the import `import com.noobexon.xposedfakelocation.data.JOYSTICK_SPEED_ACCURACY`, add above the object:

```kotlin
/** Speed/bearing values the hooks should report, plus whether randomize may run. */
internal data class MotionValues(
    val override: Boolean,
    val randomize: Boolean,
    val speed: Float,
    val speedAccuracy: Float,
    val bearing: Float,
)
```

and inside `JoystickHookPolicy`:

```kotlin
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.noobexon.xposedfakelocation.xposed.hooks.JoystickHookPolicyTest"`
Expected: `BUILD SUCCESSFUL`, 10 tests passed.

- [ ] **Step 5: Wire the override into `LocationUtil`**

In `xposed/utils/LocationUtil.kt`:

Add import `import com.noobexon.xposedfakelocation.xposed.hooks.JoystickHookPolicy`.

Directly after the `speedAccuracy` property, add:

```kotlin
    /** Current spoofed bearing in degrees. Only reported while [motionOverride] is `true`. */
    var bearing: Float = 0F
        private set
    /**
     * `true` while a live joystick session drives speed and bearing. Speed and bearing are then
     * reported even when zero, because a stationary joystick must report 0 m/s, not the real speed.
     */
    var motionOverride: Boolean = false
        private set
```

In `createFakeLocation`, replace:

```kotlin
        if (speed != 0F) {
            fakeLocation.speed = speed
        }

        if (speedAccuracy != 0F) {
            fakeLocation.speedAccuracyMetersPerSecond = speedAccuracy
        }
```

with:

```kotlin
        if (motionOverride) {
            fakeLocation.speed = speed
            fakeLocation.speedAccuracyMetersPerSecond = speedAccuracy
            fakeLocation.bearing = bearing
        } else {
            if (speed != 0F) {
                fakeLocation.speed = speed
            }

            if (speedAccuracy != 0F) {
                fakeLocation.speedAccuracyMetersPerSecond = speedAccuracy
            }
        }
```

In `updateLocation`, replace the randomize block:

```kotlin
            if (PreferencesUtil.getUseRandomize() == true) {
```

with:

```kotlin
            val motion = JoystickHookPolicy.resolveMotion(
                joystick = JoystickHookPolicy.activeState(PreferencesUtil.getJoystickState(), System.currentTimeMillis()),
                useRandomize = PreferencesUtil.getUseRandomize() == true,
                useSpeed = PreferencesUtil.getUseSpeed() == true,
                speed = PreferencesUtil.getSpeed() ?: DEFAULT_SPEED,
                useSpeedAccuracy = PreferencesUtil.getUseSpeedAccuracy() == true,
                speedAccuracy = PreferencesUtil.getSpeedAccuracy() ?: DEFAULT_SPEED_ACCURACY,
            )

            if (motion.randomize) {
```

and replace the two trailing speed blocks:

```kotlin
            if (PreferencesUtil.getUseSpeed() == true) {
                speed = PreferencesUtil.getSpeed() ?: DEFAULT_SPEED
            }

            if (PreferencesUtil.getUseSpeedAccuracy() == true) {
                speedAccuracy = PreferencesUtil.getSpeedAccuracy() ?: DEFAULT_SPEED_ACCURACY
            }
```

with:

```kotlin
            motionOverride = motion.override
            speed = motion.speed
            speedAccuracy = motion.speedAccuracy
            bearing = motion.bearing
```

Update the KDoc of `updateLocation` by appending this paragraph before the `@Synchronized` note:

```kotlin
     * While a fresh joystick state is published (see [JoystickHookPolicy.activeState]), randomize
     * and the custom speed settings are paused and the joystick's speed and bearing are used.
     *
```

Note: speed/speed accuracy now reset to 0 when their toggles are off, instead of keeping a stale value.

- [ ] **Step 6: Hook bearing and gate speed getters on the override**

In `xposed/hooks/LocationApiHooks.kt`, replace:

```kotlin
                hookMethod("getSpeed", enabled = { PreferencesUtil.getUseSpeed() == true }) { LocationUtil.speed }
                hookMethod("getSpeedAccuracyMetersPerSecond", enabled = { PreferencesUtil.getUseSpeedAccuracy() == true }) { LocationUtil.speedAccuracy }
```

with:

```kotlin
                hookMethod("getSpeed", enabled = { LocationUtil.motionOverride || PreferencesUtil.getUseSpeed() == true }) { LocationUtil.speed }
                hookMethod("getSpeedAccuracyMetersPerSecond", enabled = { LocationUtil.motionOverride || PreferencesUtil.getUseSpeedAccuracy() == true }) { LocationUtil.speedAccuracy }
                hookMethod("getBearing", enabled = { LocationUtil.motionOverride }) { LocationUtil.bearing }
                hookMethod("hasSpeed", enabled = { LocationUtil.motionOverride }) { true }
                hookMethod("hasBearing", enabled = { LocationUtil.motionOverride }) { true }
```

(`hookMethod` calls `updateLocation()` before evaluating `enabled`, so `motionOverride` is fresh.)

- [ ] **Step 7: Run all unit tests and compile**

Run: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/noobexon/xposedfakelocation/xposed app/src/test/java/com/noobexon/xposedfakelocation/xposed/hooks/JoystickHookPolicyTest.kt
git commit -m "feat: report joystick bearing and speed from hooks"
```

---

### Task 5: Joystick settings persistence and backup

**Files:**
- Modify: `data/repository/PrefrencesRepository.kt` (flow helpers, new "Joystick (local)" region)
- Modify: `data/model/SettingsBackup.kt`
- Modify: `data/backup/SettingsBackupCodec.kt`
- Modify: `data/backup/SettingsBackupManager.kt`
- Modify: `manager/ui/settings/SettingsViewModel.kt` (`currentBackup`)
- Test: `app/src/test/java/com/noobexon/xposedfakelocation/data/backup/SettingsBackupCodecTest.kt`

**Interfaces:**
- Consumes: `JoystickSettings`, `JoystickMode`, `JoystickPreset`, joystick constants (Task 2)
- Produces (all non-suspend):
  - `PreferencesRepository.getJoystickSettingsFlow(): Flow<JoystickSettings>`, `getJoystickSettings(): JoystickSettings`
  - `saveJoystickMode(mode: JoystickMode)`, `saveJoystickSelectedPreset(preset: JoystickPreset)`, `saveJoystickMaxSpeed(speedMps: Float)`, `saveJoystickPresetSpeed(preset: JoystickPreset, speedMps: Float)`, `saveJoystickSettings(settings: JoystickSettings)`
  - `getJoystickOverlayPosition(): Pair<Int, Int>?`, `saveJoystickOverlayPosition(x: Int, y: Int)`
  - `data class JoystickBackup(mode: String, maxSpeed, walkSpeed, runSpeed, bikeSpeed, driveSpeed: Float, selectedPreset: String)` with `isValid()`, `toSettings()`, `JoystickBackup.from(settings)`
  - `SettingsBackup.joystick: JoystickBackup?` (last constructor param, default `null`)

- [ ] **Step 1: Write the failing codec tests**

In `SettingsBackupCodecTest`:

Add imports:

```kotlin
import com.noobexon.xposedfakelocation.data.model.JoystickBackup
import com.noobexon.xposedfakelocation.data.model.JoystickMode
import com.noobexon.xposedfakelocation.data.model.JoystickPreset
import org.junit.Assert.assertNull
```

In `sampleBackup()`, after `appearance = ...,` add:

```kotlin
        joystick = JoystickBackup(
            mode = "CONSTANT", maxSpeed = 4f, walkSpeed = 1.5f, runSpeed = 3.2f,
            bikeSpeed = 6f, driveSpeed = 15f, selectedPreset = "BIKE",
        ),
```

Append tests:

```kotlin
    @Test
    fun fileWithoutJoystickSectionImportsWithNullJoystick() {
        val json = """
            {"version":1,"exportedAt":0,"appVersion":"x",
             $minimalSpoofingJson,
             "favorites":[],
             "appearance":{"themeOption":"","languageTag":""}}
        """.trimIndent()
        val restored = codec.fromJson(json).getOrThrow()
        assertNull(restored.joystick)
    }

    @Test
    fun invalidJoystickSpeedsDropOnlyTheJoystickSection() {
        val json = """
            {"version":1,"exportedAt":0,"appVersion":"x",
             $minimalSpoofingJson,
             "favorites":[{"name":"Good","latitude":1.0,"longitude":2.0,"description":""}],
             "appearance":{"themeOption":"","languageTag":""},
             "joystick":{"mode":"DYNAMIC","maxSpeed":-1.0,"walkSpeed":1.4,"runSpeed":3.0,
                         "bikeSpeed":5.5,"driveSpeed":14.0,"selectedPreset":"WALK"}}
        """.trimIndent()
        val restored = codec.fromJson(json).getOrThrow()
        assertNull(restored.joystick)
        assertEquals(1, restored.favorites.size)
    }

    @Test
    fun unknownJoystickEnumNamesFallBackToDefaults() {
        val settings = JoystickBackup("TELEPORT", 3f, 1.4f, 3f, 5.5f, 14f, "JETPACK").toSettings()
        assertEquals(JoystickMode.DYNAMIC, settings.mode)
        assertEquals(JoystickPreset.WALK, settings.selectedPreset)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.noobexon.xposedfakelocation.data.backup.SettingsBackupCodecTest"`
Expected: compilation FAIL, `Unresolved reference 'JoystickBackup'`.

- [ ] **Step 3: Add the backup model**

In `data/model/SettingsBackup.kt` add imports:

```kotlin
import com.noobexon.xposedfakelocation.data.JOYSTICK_MAX_SPEED_MPS
```

Change `SettingsBackup` to end with:

```kotlin
    val appearance: AppearanceBackup,
    /** Absent in files exported before the joystick feature; import then leaves joystick settings untouched. */
    val joystick: JoystickBackup? = null,
)
```

Append:

```kotlin

/** Manager-only joystick preferences (local prefs). Speeds in m/s; enums stored by name. */
data class JoystickBackup(
    val mode: String,
    val maxSpeed: Float,
    val walkSpeed: Float,
    val runSpeed: Float,
    val bikeSpeed: Float,
    val driveSpeed: Float,
    val selectedPreset: String,
) {
    fun isValid(): Boolean = listOf(maxSpeed, walkSpeed, runSpeed, bikeSpeed, driveSpeed)
        .all { it.isFinite() && it > 0f && it <= JOYSTICK_MAX_SPEED_MPS }

    fun toSettings(): JoystickSettings {
        val defaults = JoystickSettings()
        return JoystickSettings(
            mode = JoystickMode.entries.firstOrNull { it.name == mode } ?: defaults.mode,
            maxSpeedMps = maxSpeed,
            walkSpeedMps = walkSpeed,
            runSpeedMps = runSpeed,
            bikeSpeedMps = bikeSpeed,
            driveSpeedMps = driveSpeed,
            selectedPreset = JoystickPreset.entries.firstOrNull { it.name == selectedPreset } ?: defaults.selectedPreset,
        )
    }

    companion object {
        fun from(settings: JoystickSettings) = JoystickBackup(
            mode = settings.mode.name,
            maxSpeed = settings.maxSpeedMps,
            walkSpeed = settings.walkSpeedMps,
            runSpeed = settings.runSpeedMps,
            bikeSpeed = settings.bikeSpeedMps,
            driveSpeed = settings.driveSpeedMps,
            selectedPreset = settings.selectedPreset.name,
        )
    }
}
```

- [ ] **Step 4: Sanitize on import**

In `data/backup/SettingsBackupCodec.kt` replace `parsed.copy(favorites = favorites)` with:

```kotlin
        parsed.copy(favorites = favorites, joystick = parsed.joystick?.takeIf { it.isValid() })
```

- [ ] **Step 5: Run codec tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.noobexon.xposedfakelocation.data.backup.SettingsBackupCodecTest"`
Expected: `BUILD SUCCESSFUL`, 10 tests passed.

- [ ] **Step 6: Add repository support**

In `data/repository/PrefrencesRepository.kt`:

Add imports:

```kotlin
import com.noobexon.xposedfakelocation.data.KEY_JOYSTICK_MAX_SPEED
import com.noobexon.xposedfakelocation.data.KEY_JOYSTICK_MODE
import com.noobexon.xposedfakelocation.data.KEY_JOYSTICK_OVERLAY_X
import com.noobexon.xposedfakelocation.data.KEY_JOYSTICK_OVERLAY_Y
import com.noobexon.xposedfakelocation.data.KEY_JOYSTICK_PRESET_BIKE
import com.noobexon.xposedfakelocation.data.KEY_JOYSTICK_PRESET_DRIVE
import com.noobexon.xposedfakelocation.data.KEY_JOYSTICK_PRESET_RUN
import com.noobexon.xposedfakelocation.data.KEY_JOYSTICK_PRESET_WALK
import com.noobexon.xposedfakelocation.data.KEY_JOYSTICK_SELECTED_PRESET
import com.noobexon.xposedfakelocation.data.model.JoystickMode
import com.noobexon.xposedfakelocation.data.model.JoystickPreset
import com.noobexon.xposedfakelocation.data.model.JoystickSettings
```

Generalize the flow helpers to multiple keys. Replace:

```kotlin
                prefsChangeFlow(prefs, key) { read(prefs) }
```

with:

```kotlin
                prefsChangeFlow(prefs, setOf(key)) { read(prefs) }
```

Replace the `localFlow` + `prefsChangeFlow` definitions with:

```kotlin
    private fun <T> localFlow(key: String, read: (SharedPreferences) -> T): Flow<T> =
        localFlow(setOf(key), read)

    private fun <T> localFlow(keys: Set<String>, read: (SharedPreferences) -> T): Flow<T> =
        prefsChangeFlow(localPrefs, keys) { read(localPrefs) }

    private fun <T> prefsChangeFlow(
        prefs: SharedPreferences,
        keys: Set<String>,
        read: () -> T
    ): Flow<T> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == null || changedKey in keys) trySend(read())
        }
        trySend(read())
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
```

Insert this region directly before `// region Map Zoom (local)`:

```kotlin
    // region Joystick (local)
    private val joystickSettingsKeys = setOf(
        KEY_JOYSTICK_MODE,
        KEY_JOYSTICK_MAX_SPEED,
        KEY_JOYSTICK_PRESET_WALK,
        KEY_JOYSTICK_PRESET_RUN,
        KEY_JOYSTICK_PRESET_BIKE,
        KEY_JOYSTICK_PRESET_DRIVE,
        KEY_JOYSTICK_SELECTED_PRESET,
    )

    fun getJoystickSettingsFlow(): Flow<JoystickSettings> =
        localFlow(joystickSettingsKeys, ::readJoystickSettings)

    fun getJoystickSettings(): JoystickSettings = readJoystickSettings(localPrefs)

    fun saveJoystickMode(mode: JoystickMode) =
        editLocal { putString(KEY_JOYSTICK_MODE, mode.name) }

    fun saveJoystickSelectedPreset(preset: JoystickPreset) =
        editLocal { putString(KEY_JOYSTICK_SELECTED_PRESET, preset.name) }

    fun saveJoystickMaxSpeed(speedMps: Float) =
        editLocal { putFloat(KEY_JOYSTICK_MAX_SPEED, speedMps) }

    fun saveJoystickPresetSpeed(preset: JoystickPreset, speedMps: Float) =
        editLocal { putFloat(presetSpeedKey(preset), speedMps) }

    /** Replaces every joystick setting at once (used by settings import). */
    fun saveJoystickSettings(settings: JoystickSettings) = editLocal {
        putString(KEY_JOYSTICK_MODE, settings.mode.name)
        putFloat(KEY_JOYSTICK_MAX_SPEED, settings.maxSpeedMps)
        JoystickPreset.entries.forEach { putFloat(presetSpeedKey(it), settings.presetSpeedMps(it)) }
        putString(KEY_JOYSTICK_SELECTED_PRESET, settings.selectedPreset.name)
    }

    fun getJoystickOverlayPosition(): Pair<Int, Int>? =
        if (localPrefs.contains(KEY_JOYSTICK_OVERLAY_X) && localPrefs.contains(KEY_JOYSTICK_OVERLAY_Y)) {
            localPrefs.getInt(KEY_JOYSTICK_OVERLAY_X, 0) to localPrefs.getInt(KEY_JOYSTICK_OVERLAY_Y, 0)
        } else {
            null
        }

    fun saveJoystickOverlayPosition(x: Int, y: Int) = editLocal {
        putInt(KEY_JOYSTICK_OVERLAY_X, x)
        putInt(KEY_JOYSTICK_OVERLAY_Y, y)
    }

    private fun readJoystickSettings(prefs: SharedPreferences): JoystickSettings {
        val defaults = JoystickSettings()
        val modeName = prefs.getString(KEY_JOYSTICK_MODE, null)
        val presetName = prefs.getString(KEY_JOYSTICK_SELECTED_PRESET, null)
        return JoystickSettings(
            mode = JoystickMode.entries.firstOrNull { it.name == modeName } ?: defaults.mode,
            maxSpeedMps = prefs.getFloat(KEY_JOYSTICK_MAX_SPEED, defaults.maxSpeedMps),
            walkSpeedMps = prefs.getFloat(KEY_JOYSTICK_PRESET_WALK, defaults.walkSpeedMps),
            runSpeedMps = prefs.getFloat(KEY_JOYSTICK_PRESET_RUN, defaults.runSpeedMps),
            bikeSpeedMps = prefs.getFloat(KEY_JOYSTICK_PRESET_BIKE, defaults.bikeSpeedMps),
            driveSpeedMps = prefs.getFloat(KEY_JOYSTICK_PRESET_DRIVE, defaults.driveSpeedMps),
            selectedPreset = JoystickPreset.entries.firstOrNull { it.name == presetName } ?: defaults.selectedPreset,
        )
    }

    private fun presetSpeedKey(preset: JoystickPreset): String = when (preset) {
        JoystickPreset.WALK -> KEY_JOYSTICK_PRESET_WALK
        JoystickPreset.RUN -> KEY_JOYSTICK_PRESET_RUN
        JoystickPreset.BIKE -> KEY_JOYSTICK_PRESET_BIKE
        JoystickPreset.DRIVE -> KEY_JOYSTICK_PRESET_DRIVE
    }
    // endregion

```

- [ ] **Step 7: Apply and export the section**

In `data/backup/SettingsBackupManager.kt`, after `repository.saveLanguageTag(backup.appearance.languageTag)` add:

```kotlin
        backup.joystick?.let { repository.saveJoystickSettings(it.toSettings()) }
```

and update the KDoc line "Favorites and appearance (local prefs) are always applied." to "Favorites, appearance and joystick settings (local prefs) are always applied; a missing joystick section leaves them untouched."

In `manager/ui/settings/SettingsViewModel.kt` add the import `import com.noobexon.xposedfakelocation.data.model.JoystickBackup` and in `currentBackup()` after the `appearance = AppearanceBackup(...)` argument add:

```kotlin
            joystick = JoystickBackup.from(preferencesRepository.getJoystickSettings()),
```

- [ ] **Step 8: Run all unit tests and compile**

Run: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/noobexon/xposedfakelocation/data app/src/main/java/com/noobexon/xposedfakelocation/manager/ui/settings/SettingsViewModel.kt app/src/test/java/com/noobexon/xposedfakelocation/data/backup/SettingsBackupCodecTest.kt
git commit -m "feat: persist joystick settings and include them in backups"
```

---

### Task 6: Joystick section in Settings

**Files:**
- Modify: `manager/ui/settings/SettingsModel.kt`
- Modify: `manager/ui/settings/SettingsViewModel.kt`
- Modify: `manager/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`, `values-de/strings.xml`, `values-zh/strings.xml`

**Interfaces:**
- Consumes: Task 5 repository API; `MPS_TO_KMH`, `DEFAULT_JOYSTICK_*` (Task 2)
- Produces: `SettingsCategory.JOYSTICK`; `NumericSetting.JOYSTICK_MAX_SPEED`, `JOYSTICK_PRESET_WALK/RUN/BIKE/DRIVE` (values in km/h); `SettingEntry.Numeric.showToggle: Boolean = true`; `SettingsViewModel.joystickSettings: StateFlow<JoystickSettings>`, `setJoystickMaxSpeed(speedMps: Float)`, `setJoystickPresetSpeed(preset: JoystickPreset, speedMps: Float)`; string `setting_joystick_{walk,run,bike,drive}_label` (reused by the overlay chips in Task 8).

- [ ] **Step 1: Add strings**

`values/strings.xml` — after `<string name="category_movement">Movement</string>`:

```xml
    <string name="category_joystick">Joystick</string>
```

after `<string name="unit_meters_per_second" ...>`:

```xml
    <string name="unit_kilometers_per_hour" translatable="false">km/h</string>
```

after `<string name="setting_speed_accuracy_label">Speed Accuracy</string>`:

```xml

    <string name="setting_joystick_max_speed_title">Joystick max speed</string>
    <string name="setting_joystick_max_speed_description">Top speed in Dynamic mode, reached when the stick is pushed all the way</string>
    <string name="setting_joystick_max_speed_label">Max speed</string>
    <string name="setting_joystick_walk_title">Walk preset speed</string>
    <string name="setting_joystick_walk_description">Speed used by the Walk preset in Constant mode</string>
    <string name="setting_joystick_walk_label">Walk</string>
    <string name="setting_joystick_run_title">Run preset speed</string>
    <string name="setting_joystick_run_description">Speed used by the Run preset in Constant mode</string>
    <string name="setting_joystick_run_label">Run</string>
    <string name="setting_joystick_bike_title">Bike preset speed</string>
    <string name="setting_joystick_bike_description">Speed used by the Bike preset in Constant mode</string>
    <string name="setting_joystick_bike_label">Bike</string>
    <string name="setting_joystick_drive_title">Drive preset speed</string>
    <string name="setting_joystick_drive_description">Speed used by the Drive preset in Constant mode</string>
    <string name="setting_joystick_drive_label">Drive</string>
```

`values-de/strings.xml` — after `category_movement`:

```xml
    <string name="category_joystick">Joystick</string>
```

after `setting_speed_accuracy_label`:

```xml

    <string name="setting_joystick_max_speed_title">Maximale Joystick-Geschwindigkeit</string>
    <string name="setting_joystick_max_speed_description">Höchstgeschwindigkeit im dynamischen Modus bei vollem Ausschlag des Sticks</string>
    <string name="setting_joystick_max_speed_label">Max. Geschwindigkeit</string>
    <string name="setting_joystick_walk_title">Geschwindigkeit „Gehen“</string>
    <string name="setting_joystick_walk_description">Geschwindigkeit der Voreinstellung „Gehen“ im konstanten Modus</string>
    <string name="setting_joystick_walk_label">Gehen</string>
    <string name="setting_joystick_run_title">Geschwindigkeit „Laufen“</string>
    <string name="setting_joystick_run_description">Geschwindigkeit der Voreinstellung „Laufen“ im konstanten Modus</string>
    <string name="setting_joystick_run_label">Laufen</string>
    <string name="setting_joystick_bike_title">Geschwindigkeit „Fahrrad“</string>
    <string name="setting_joystick_bike_description">Geschwindigkeit der Voreinstellung „Fahrrad“ im konstanten Modus</string>
    <string name="setting_joystick_bike_label">Fahrrad</string>
    <string name="setting_joystick_drive_title">Geschwindigkeit „Auto“</string>
    <string name="setting_joystick_drive_description">Geschwindigkeit der Voreinstellung „Auto“ im konstanten Modus</string>
    <string name="setting_joystick_drive_label">Auto</string>
```

`values-zh/strings.xml` — after `category_movement`:

```xml
    <string name="category_joystick">摇杆</string>
```

after `setting_speed_accuracy_label`:

```xml

    <string name="setting_joystick_max_speed_title">摇杆最大速度</string>
    <string name="setting_joystick_max_speed_description">动态模式下摇杆推到底时的最高速度</string>
    <string name="setting_joystick_max_speed_label">最大速度</string>
    <string name="setting_joystick_walk_title">步行预设速度</string>
    <string name="setting_joystick_walk_description">恒定模式下“步行”预设使用的速度</string>
    <string name="setting_joystick_walk_label">步行</string>
    <string name="setting_joystick_run_title">跑步预设速度</string>
    <string name="setting_joystick_run_description">恒定模式下“跑步”预设使用的速度</string>
    <string name="setting_joystick_run_label">跑步</string>
    <string name="setting_joystick_bike_title">骑行预设速度</string>
    <string name="setting_joystick_bike_description">恒定模式下“骑行”预设使用的速度</string>
    <string name="setting_joystick_bike_label">骑行</string>
    <string name="setting_joystick_drive_title">驾车预设速度</string>
    <string name="setting_joystick_drive_description">恒定模式下“驾车”预设使用的速度</string>
    <string name="setting_joystick_drive_label">驾车</string>
```

- [ ] **Step 2: Extend the settings model**

In `SettingsModel.kt`:

Add imports:

```kotlin
import com.noobexon.xposedfakelocation.data.DEFAULT_JOYSTICK_MAX_SPEED
import com.noobexon.xposedfakelocation.data.DEFAULT_JOYSTICK_PRESET_BIKE
import com.noobexon.xposedfakelocation.data.DEFAULT_JOYSTICK_PRESET_DRIVE
import com.noobexon.xposedfakelocation.data.DEFAULT_JOYSTICK_PRESET_RUN
import com.noobexon.xposedfakelocation.data.DEFAULT_JOYSTICK_PRESET_WALK
import com.noobexon.xposedfakelocation.data.MPS_TO_KMH
```

In `SettingsCategory`, after `MOVEMENT(R.string.category_movement),` add:

```kotlin
    JOYSTICK(R.string.category_joystick),
```

In `NumericSetting`, change the `SPEED_ACCURACY(...)` entry's trailing `;` to `,` and append:

```kotlin
    JOYSTICK_MAX_SPEED(
        R.string.setting_joystick_max_speed_title,
        R.string.setting_joystick_max_speed_description,
        R.string.setting_joystick_max_speed_label,
        unitRes = R.string.unit_kilometers_per_hour, min = 1f, max = 200f, precision = 0.1f, default = DEFAULT_JOYSTICK_MAX_SPEED * MPS_TO_KMH
    ),
    JOYSTICK_PRESET_WALK(
        R.string.setting_joystick_walk_title,
        R.string.setting_joystick_walk_description,
        R.string.setting_joystick_walk_label,
        unitRes = R.string.unit_kilometers_per_hour, min = 1f, max = 200f, precision = 0.1f, default = DEFAULT_JOYSTICK_PRESET_WALK * MPS_TO_KMH
    ),
    JOYSTICK_PRESET_RUN(
        R.string.setting_joystick_run_title,
        R.string.setting_joystick_run_description,
        R.string.setting_joystick_run_label,
        unitRes = R.string.unit_kilometers_per_hour, min = 1f, max = 200f, precision = 0.1f, default = DEFAULT_JOYSTICK_PRESET_RUN * MPS_TO_KMH
    ),
    JOYSTICK_PRESET_BIKE(
        R.string.setting_joystick_bike_title,
        R.string.setting_joystick_bike_description,
        R.string.setting_joystick_bike_label,
        unitRes = R.string.unit_kilometers_per_hour, min = 1f, max = 200f, precision = 0.1f, default = DEFAULT_JOYSTICK_PRESET_BIKE * MPS_TO_KMH
    ),
    JOYSTICK_PRESET_DRIVE(
        R.string.setting_joystick_drive_title,
        R.string.setting_joystick_drive_description,
        R.string.setting_joystick_drive_label,
        unitRes = R.string.unit_kilometers_per_hour, min = 1f, max = 200f, precision = 0.1f, default = DEFAULT_JOYSTICK_PRESET_DRIVE * MPS_TO_KMH
    );
```

In `SettingEntry.Numeric`, add a last parameter and document it:

```kotlin
        override val onReset: (() -> Unit)? = null,
        val showToggle: Boolean = true,
```

KDoc line to add to the `Numeric` doc block:

```kotlin
     * @property showToggle When `false` the row has no enable switch and its value controls are
     *   always visible (for settings that are always in effect).
```

- [ ] **Step 3: ViewModel setters, flow and reset**

In `SettingsViewModel.kt` add imports:

```kotlin
import com.noobexon.xposedfakelocation.data.DEFAULT_JOYSTICK_MAX_SPEED
import com.noobexon.xposedfakelocation.data.DEFAULT_JOYSTICK_PRESET_BIKE
import com.noobexon.xposedfakelocation.data.DEFAULT_JOYSTICK_PRESET_DRIVE
import com.noobexon.xposedfakelocation.data.DEFAULT_JOYSTICK_PRESET_RUN
import com.noobexon.xposedfakelocation.data.DEFAULT_JOYSTICK_PRESET_WALK
import com.noobexon.xposedfakelocation.data.model.JoystickPreset
import com.noobexon.xposedfakelocation.data.model.JoystickSettings
```

After the `_enableBroadcastControl` preference, add:

```kotlin

    // ---- Joystick (local) --------------------------------------------------------------------

    /** Joystick configuration; speeds in m/s. */
    val joystickSettings: StateFlow<JoystickSettings> = preferencesRepository.getJoystickSettingsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, preferencesRepository.getJoystickSettings())

    /** @param speedMps Dynamic-mode top speed in m/s. */
    fun setJoystickMaxSpeed(speedMps: Float) = preferencesRepository.saveJoystickMaxSpeed(speedMps)

    /** @param speedMps Constant-mode speed for [preset] in m/s. */
    fun setJoystickPresetSpeed(preset: JoystickPreset, speedMps: Float) =
        preferencesRepository.saveJoystickPresetSpeed(preset, speedMps)
```

In `resetNumeric`, add branches to the `when`:

```kotlin
            NumericSetting.JOYSTICK_MAX_SPEED -> setJoystickMaxSpeed(DEFAULT_JOYSTICK_MAX_SPEED)
            NumericSetting.JOYSTICK_PRESET_WALK -> setJoystickPresetSpeed(JoystickPreset.WALK, DEFAULT_JOYSTICK_PRESET_WALK)
            NumericSetting.JOYSTICK_PRESET_RUN -> setJoystickPresetSpeed(JoystickPreset.RUN, DEFAULT_JOYSTICK_PRESET_RUN)
            NumericSetting.JOYSTICK_PRESET_BIKE -> setJoystickPresetSpeed(JoystickPreset.BIKE, DEFAULT_JOYSTICK_PRESET_BIKE)
            NumericSetting.JOYSTICK_PRESET_DRIVE -> setJoystickPresetSpeed(JoystickPreset.DRIVE, DEFAULT_JOYSTICK_PRESET_DRIVE)
```

(`resetToDefaults` already iterates `NumericSetting.entries`, so it resets joystick speeds too.)

- [ ] **Step 4: Screen — toggle-less numeric rows**

In `SettingsScreen.kt`:

In `SettingEntryRow`, the `is SettingEntry.Numeric -> NumericSettingItem(` call gains:

```kotlin
            onReset = entry.onReset,
            showToggle = entry.showToggle
```

(replace the existing `onReset = entry.onReset` line for that branch).

In `NumericSettingItem`, add the parameter `showToggle: Boolean = true` after `onReset`, add `@param showToggle` to its KDoc ("When `false`, no enable switch is shown and the value controls are always visible."), and change the body:

```kotlin
    val showValue = useValue || !showToggle
```

(declare right after `val adjustDescription = ...`), then replace:

```kotlin
            onReset = onReset?.takeIf { useValue },
            trailing = {
                SettingSwitch(title = title, checked = useValue, onCheckedChange = onUseValueChange)
            }
        )

        if (useValue) {
```

with:

```kotlin
            onReset = onReset?.takeIf { showValue },
            trailing = {
                if (showToggle) {
                    SettingSwitch(title = title, checked = useValue, onCheckedChange = onUseValueChange)
                }
            }
        )

        if (showValue) {
```

- [ ] **Step 5: Screen — Joystick section**

In `SettingsScreen.kt` add imports:

```kotlin
import com.noobexon.xposedfakelocation.data.MPS_TO_KMH
import com.noobexon.xposedfakelocation.data.model.JoystickPreset
```

Directly after `val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()` add:

```kotlin
    val joystick by settingsViewModel.joystickSettings.collectAsStateWithLifecycle()

    fun joystickSpeedEntry(setting: NumericSetting, speedMps: Float, save: (Float) -> Unit) = SettingEntry.Numeric(
        setting = setting,
        enabled = true,
        onEnabledChange = {},
        value = speedMps * MPS_TO_KMH,
        onValueChange = { kmh -> save(kmh / MPS_TO_KMH) },
        onReset = { settingsViewModel.resetNumeric(setting) },
        showToggle = false,
    )
```

In `categories`, after the `SettingsCategory.MOVEMENT to listOf(...)` entry, add:

```kotlin
        SettingsCategory.JOYSTICK to listOf(
            joystickSpeedEntry(NumericSetting.JOYSTICK_MAX_SPEED, joystick.maxSpeedMps, settingsViewModel::setJoystickMaxSpeed),
            joystickSpeedEntry(NumericSetting.JOYSTICK_PRESET_WALK, joystick.walkSpeedMps) { settingsViewModel.setJoystickPresetSpeed(JoystickPreset.WALK, it) },
            joystickSpeedEntry(NumericSetting.JOYSTICK_PRESET_RUN, joystick.runSpeedMps) { settingsViewModel.setJoystickPresetSpeed(JoystickPreset.RUN, it) },
            joystickSpeedEntry(NumericSetting.JOYSTICK_PRESET_BIKE, joystick.bikeSpeedMps) { settingsViewModel.setJoystickPresetSpeed(JoystickPreset.BIKE, it) },
            joystickSpeedEntry(NumericSetting.JOYSTICK_PRESET_DRIVE, joystick.driveSpeedMps) { settingsViewModel.setJoystickPresetSpeed(JoystickPreset.DRIVE, it) },
        ),
```

- [ ] **Step 6: Compile and test**

Run: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Manual check**

Install the debug APK, open Settings. Expected: a "Joystick" card under "Movement" with five rows, no switches, values 10.8 / 5.0 / 10.8 / 19.8 / 50.4 km/h. Change Walk to 6 km/h, leave and return: value persists. Reset icon restores 5.0. Search "walk" finds the row.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/noobexon/xposedfakelocation/manager/ui/settings app/src/main/res/values/strings.xml app/src/main/res/values-de/strings.xml app/src/main/res/values-zh/strings.xml
git commit -m "feat: add joystick speed settings section"
```

---

### Task 7: JoystickSession (tick, throttle, echo detection)

**Files:**
- Create: `manager/joystick/JoystickSession.kt`
- Test: `app/src/test/java/com/noobexon/xposedfakelocation/manager/joystick/JoystickSessionTest.kt`

**Interfaces:**
- Consumes: `MovementEngine`, `MovementInput`, `MovementState` (Task 2), `JoystickSettings`, `JoystickState`
- Produces:
  - `data class JoystickFrame(latitude: Double, longitude: Double, state: JoystickState)`
  - `internal object JoystickWritePolicy { MOVING_INTERVAL_MS = 250L; HEARTBEAT_INTERVAL_MS = 2_000L; fun shouldWrite(nowMs: Long, lastWriteMs: Long, moving: Boolean, wasMoving: Boolean): Boolean }`
  - `internal class RecentPositions(capacity: Int = 16) { add(lat, lon); contains(lat, lon): Boolean }`
  - `class JoystickSession(scope: CoroutineScope, startLatitude: Double, startLongitude: Double, settings: StateFlow<JoystickSettings>, publish: (JoystickFrame) -> Unit, monotonicClock: () -> Long, wallClock: () -> Long = System::currentTimeMillis, ioDispatcher: CoroutineDispatcher = Dispatchers.IO)` with `currentState: MovementState`, `start()`, `setInput(x: Float, y: Float)`, `onExternalMarker(latitude, longitude)`, `stop(): JoystickFrame`, `internal fun tick(nowMs: Long): JoystickFrame?`

- [ ] **Step 1: Write the failing test**

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.noobexon.xposedfakelocation.manager.joystick.JoystickSessionTest"`
Expected: compilation FAIL, `Unresolved reference 'JoystickSession'`.

- [ ] **Step 3: Implement**

`manager/joystick/JoystickSession.kt`:

```kotlin
package com.noobexon.xposedfakelocation.manager.joystick

import com.noobexon.xposedfakelocation.data.model.JoystickSettings
import com.noobexon.xposedfakelocation.data.model.JoystickState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/** A position plus the joystick state published with it. */
data class JoystickFrame(val latitude: Double, val longitude: Double, val state: JoystickState)

/** Decides when a [JoystickSession] publishes a frame. */
internal object JoystickWritePolicy {
    const val MOVING_INTERVAL_MS = 250L
    const val HEARTBEAT_INTERVAL_MS = 2_000L

    fun shouldWrite(nowMs: Long, lastWriteMs: Long, moving: Boolean, wasMoving: Boolean): Boolean = when {
        wasMoving && !moving -> true
        moving -> nowMs - lastWriteMs >= MOVING_INTERVAL_MS
        else -> nowMs - lastWriteMs >= HEARTBEAT_INTERVAL_MS
    }
}

/**
 * Positions this session recently published, so their echo through the last-clicked-location
 * flow isn't mistaken for the user moving the marker.
 */
internal class RecentPositions(private val capacity: Int = 16) {
    private val items = ArrayDeque<Pair<Double, Double>>()

    fun add(latitude: Double, longitude: Double) {
        if (items.size == capacity) items.removeFirst()
        items.addLast(latitude to longitude)
    }

    fun contains(latitude: Double, longitude: Double): Boolean =
        items.any { (lat, lon) -> abs(lat - latitude) < EPSILON && abs(lon - longitude) < EPSILON }

    private companion object {
        const val EPSILON = 1e-9
    }
}

/**
 * Integrates joystick input into a moving position and hands frames to [publish].
 *
 * Not thread-safe: [setInput], [onExternalMarker], [stop] and the tick loop must all run on the
 * single thread backing [scope] (the service's main-thread scope). Only [publish] runs on
 * [ioDispatcher].
 */
class JoystickSession(
    private val scope: CoroutineScope,
    startLatitude: Double,
    startLongitude: Double,
    private val settings: StateFlow<JoystickSettings>,
    private val publish: (JoystickFrame) -> Unit,
    private val monotonicClock: () -> Long,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private var state = MovementState(startLatitude, startLongitude, bearingDegrees = 0f, speedMps = 0f)
    private var input = MovementInput.NONE
    private var lastTickMs: Long? = null
    private var lastWriteMs: Long? = null
    private var wasMoving = false
    private val recent = RecentPositions()
    private val frames = Channel<JoystickFrame>(Channel.CONFLATED)
    private var jobs: List<Job> = emptyList()

    val currentState: MovementState get() = state

    fun start() {
        jobs = listOf(
            scope.launch(ioDispatcher) { for (frame in frames) publish(frame) },
            scope.launch {
                while (true) {
                    tick(monotonicClock())?.let { frames.trySend(it) }
                    delay(TICK_INTERVAL_MS)
                }
            },
        )
    }

    fun setInput(x: Float, y: Float) {
        input = MovementInput(x, y)
    }

    /** Adopts a marker position set elsewhere (map tap, favorite, broadcast) unless it is our own echo. */
    fun onExternalMarker(latitude: Double, longitude: Double) {
        if (recent.contains(latitude, longitude)) return
        state = state.copy(latitude = latitude, longitude = longitude)
    }

    /** Cancels the loop and returns the final inactive frame for the caller to publish. */
    fun stop(): JoystickFrame {
        jobs.forEach { it.cancel() }
        frames.close()
        return frame(active = false)
    }

    internal fun tick(nowMs: Long): JoystickFrame? {
        val dtSeconds = lastTickMs?.let { (nowMs - it) / 1000.0 } ?: 0.0
        lastTickMs = nowMs
        state = MovementEngine.step(state, input, settings.value, dtSeconds)

        val moving = state.speedMps > 0f
        val lastWrite = lastWriteMs
        val write = lastWrite == null || JoystickWritePolicy.shouldWrite(nowMs, lastWrite, moving, wasMoving)
        wasMoving = moving
        if (!write) return null

        lastWriteMs = nowMs
        recent.add(state.latitude, state.longitude)
        return frame(active = true)
    }

    private fun frame(active: Boolean) = JoystickFrame(
        latitude = state.latitude,
        longitude = state.longitude,
        state = JoystickState(
            active = active,
            bearing = state.bearingDegrees,
            speed = if (active) state.speedMps else 0f,
            updatedAt = wallClock(),
        ),
    )

    private companion object {
        const val TICK_INTERVAL_MS = 50L
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.noobexon.xposedfakelocation.manager.joystick.JoystickSessionTest"`
Expected: `BUILD SUCCESSFUL`, 9 tests passed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/noobexon/xposedfakelocation/manager/joystick/JoystickSession.kt app/src/test/java/com/noobexon/xposedfakelocation/manager/joystick/JoystickSessionTest.kt
git commit -m "feat: add joystick session with throttled publishing"
```

---

### Task 8: Overlay service, window and UI

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `manager/joystick/JoystickOverlayWindow.kt`
- Create: `manager/joystick/ui/JoystickOverlay.kt`
- Create: `manager/joystick/JoystickOverlayService.kt`
- Modify: `values/`, `values-de/`, `values-zh/strings.xml`

**Interfaces:**
- Consumes: `JoystickSession`, `JoystickFrame` (Task 7); repository joystick API (Tasks 3, 5); `XposedFakeLocationTheme`; `setting_joystick_*_label` strings (Task 6)
- Produces: `JoystickOverlayService.start(context: Context)` (used by Task 9)

- [ ] **Step 1: Add the dependency**

`gradle/libs.versions.toml`, under `[libraries]` after `androidx-lifecycle-viewmodel-compose`:

```toml
androidx-lifecycle-service = { group = "androidx.lifecycle", name = "lifecycle-service", version.ref = "lifecycleViewmodelCompose" }
```

`app/build.gradle.kts`, after `implementation(libs.androidx.lifecycle.viewmodel.compose)`:

```kotlin
    implementation(libs.androidx.lifecycle.service)
```

- [ ] **Step 2: Manifest**

Add after the existing `<uses-permission>` entries:

```xml
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

Add inside `<application>`, after the `<receiver>`:

```xml

        <service
            android:name=".manager.joystick.JoystickOverlayService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="Floating joystick overlay for manual control of the spoofed location" />
        </service>
```

- [ ] **Step 3: Strings**

`values/strings.xml`, append before `</resources>`:

```xml

    <string name="joystick_mode_dynamic">Dynamic</string>
    <string name="joystick_mode_constant">Constant</string>
    <string name="cd_joystick_drag">Move joystick</string>
    <string name="cd_joystick_collapse">Minimize joystick</string>
    <string name="cd_joystick_expand">Expand joystick</string>
    <string name="cd_joystick_close">Close joystick</string>
    <string name="joystick_notification_channel">Joystick overlay</string>
    <string name="joystick_notification_title">Joystick active</string>
    <string name="joystick_notification_text">Tap to open the app. Use Stop to close the joystick.</string>
    <string name="joystick_notification_stop">Stop joystick</string>
```

`values-de/strings.xml`:

```xml

    <string name="joystick_mode_dynamic">Dynamisch</string>
    <string name="joystick_mode_constant">Konstant</string>
    <string name="cd_joystick_drag">Joystick verschieben</string>
    <string name="cd_joystick_collapse">Joystick minimieren</string>
    <string name="cd_joystick_expand">Joystick erweitern</string>
    <string name="cd_joystick_close">Joystick schließen</string>
    <string name="joystick_notification_channel">Joystick-Overlay</string>
    <string name="joystick_notification_title">Joystick aktiv</string>
    <string name="joystick_notification_text">Tippe, um die App zu öffnen. „Stopp“ schließt den Joystick.</string>
    <string name="joystick_notification_stop">Joystick stoppen</string>
```

`values-zh/strings.xml`:

```xml

    <string name="joystick_mode_dynamic">动态</string>
    <string name="joystick_mode_constant">恒定</string>
    <string name="cd_joystick_drag">移动摇杆</string>
    <string name="cd_joystick_collapse">最小化摇杆</string>
    <string name="cd_joystick_expand">展开摇杆</string>
    <string name="cd_joystick_close">关闭摇杆</string>
    <string name="joystick_notification_channel">摇杆悬浮窗</string>
    <string name="joystick_notification_title">摇杆已启用</string>
    <string name="joystick_notification_text">点击打开应用。点击“停止”关闭摇杆。</string>
    <string name="joystick_notification_stop">停止摇杆</string>
```

- [ ] **Step 4: Overlay window host**

`manager/joystick/JoystickOverlayWindow.kt`:

```kotlin
package com.noobexon.xposedfakelocation.manager.joystick

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Hosts Compose [content] in a draggable `TYPE_APPLICATION_OVERLAY` window. The window is
 * not focusable, so the app underneath keeps keyboard focus and touches outside the overlay.
 */
class JoystickOverlayWindow(
    private val context: Context,
    lifecycleOwner: LifecycleOwner,
    savedStateRegistryOwner: SavedStateRegistryOwner,
    initialPosition: Pair<Int, Int>?,
    private val onPositionCommitted: (x: Int, y: Int) -> Unit,
    content: @Composable () -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        val (startX, startY) = initialPosition ?: defaultPosition()
        x = startX
        y = startY
    }

    private val view = ComposeView(context).apply {
        setViewTreeLifecycleOwner(lifecycleOwner)
        setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
        setContent(content)
    }

    private var attached = false

    fun show() {
        if (attached) return
        windowManager.addView(view, params)
        attached = true
        view.post { clampToScreen() }
    }

    fun moveBy(dx: Float, dy: Float) {
        params.x += dx.roundToInt()
        params.y += dy.roundToInt()
        clampToScreen()
    }

    fun commitPosition() = onPositionCommitted(params.x, params.y)

    /** Keeps the overlay fully on screen; also call after rotation or a display size change. */
    fun clampToScreen() {
        if (!attached) return
        val (width, height) = screenSize()
        params.x = params.x.coerceIn(0, max(0, width - view.width))
        params.y = params.y.coerceIn(0, max(0, height - view.height))
        windowManager.updateViewLayout(view, params)
    }

    fun remove() {
        if (attached) {
            windowManager.removeView(view)
            attached = false
        }
        view.disposeComposition()
    }

    private fun screenSize(): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.let { it.width() to it.height() }
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }

    private fun defaultPosition(): Pair<Int, Int> {
        val (_, height) = screenSize()
        val margin = (DEFAULT_MARGIN_DP * context.resources.displayMetrics.density).roundToInt()
        return margin to (height * DEFAULT_Y_FRACTION).roundToInt()
    }

    private companion object {
        const val DEFAULT_MARGIN_DP = 24
        const val DEFAULT_Y_FRACTION = 0.55f
    }
}
```

- [ ] **Step 5: Overlay UI**

`manager/joystick/ui/JoystickOverlay.kt`:

```kotlin
package com.noobexon.xposedfakelocation.manager.joystick.ui

import android.view.MotionEvent
import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.model.JoystickMode
import com.noobexon.xposedfakelocation.data.model.JoystickPreset
import com.noobexon.xposedfakelocation.data.model.JoystickSettings
import kotlin.math.hypot

/** Root of the floating overlay: an expanded control panel or a collapsed bubble. */
@Composable
fun JoystickOverlay(
    settings: JoystickSettings,
    collapsed: Boolean,
    onInput: (x: Float, y: Float) -> Unit,
    onRelease: () -> Unit,
    onModeChange: (JoystickMode) -> Unit,
    onPresetChange: (JoystickPreset) -> Unit,
    onCollapsedChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    if (collapsed) {
        CollapsedBubble(onExpand = { onCollapsedChange(false) }, onDrag = onDrag, onDragEnd = onDragEnd)
    } else {
        ExpandedPanel(
            settings = settings,
            onInput = onInput,
            onRelease = onRelease,
            onModeChange = onModeChange,
            onPresetChange = onPresetChange,
            onCollapse = {
                onRelease()
                onCollapsedChange(true)
            },
            onClose = onClose,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpandedPanel(
    settings: JoystickSettings,
    onInput: (x: Float, y: Float) -> Unit,
    onRelease: () -> Unit,
    onModeChange: (JoystickMode) -> Unit,
    onPresetChange: (JoystickPreset) -> Unit,
    onCollapse: () -> Unit,
    onClose: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.width(232.dp).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.Default.DragIndicator,
                    contentDescription = stringResource(R.string.cd_joystick_drag),
                    modifier = Modifier.size(32.dp).then(rememberOverlayDragModifier(onDrag, onDragEnd)),
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.cd_joystick_collapse))
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_joystick_close))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.mode == JoystickMode.DYNAMIC,
                    onClick = { onModeChange(JoystickMode.DYNAMIC) },
                    label = { Text(stringResource(R.string.joystick_mode_dynamic)) },
                )
                FilterChip(
                    selected = settings.mode == JoystickMode.CONSTANT,
                    onClick = { onModeChange(JoystickMode.CONSTANT) },
                    label = { Text(stringResource(R.string.joystick_mode_constant)) },
                )
            }
            if (settings.mode == JoystickMode.CONSTANT) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    JoystickPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = settings.selectedPreset == preset,
                            onClick = { onPresetChange(preset) },
                            label = { Text(stringResource(preset.labelRes)) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            JoystickPad(onInput = onInput, onRelease = onRelease)
        }
    }
}

@Composable
private fun CollapsedBubble(onExpand: () -> Unit, onDrag: (Float, Float) -> Unit, onDragEnd: () -> Unit) {
    val expandDescription = stringResource(R.string.cd_joystick_expand)
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 6.dp,
        modifier = Modifier
            .size(56.dp)
            .then(rememberOverlayDragModifier(onDrag, onDragEnd, onTap = onExpand))
            .semantics {
                contentDescription = expandDescription
                role = Role.Button
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.SportsEsports, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

/**
 * Circular stick. Emits deflection in `[-1, 1]` with positive y = up (north) while touched and
 * calls [onRelease] when the finger lifts.
 */
@Composable
fun JoystickPad(
    onInput: (x: Float, y: Float) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 160.dp,
) {
    val currentOnInput by rememberUpdatedState(onInput)
    val currentOnRelease by rememberUpdatedState(onRelease)
    var thumb by remember { mutableStateOf(Offset.Zero) }
    val baseColor = MaterialTheme.colorScheme.surfaceVariant
    val ringColor = MaterialTheme.colorScheme.outline
    val thumbColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .size(diameter)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val radius = size.width / 2f
                    val travel = radius * (1f - THUMB_RATIO)
                    val center = Offset(radius, size.height / 2f)
                    fun update(position: Offset) {
                        thumb = clampToRadius(position - center, travel)
                        currentOnInput(thumb.x / travel, -thumb.y / travel)
                    }

                    val down = awaitFirstDown()
                    update(down.position)
                    drag(down.id) { change ->
                        change.consume()
                        update(change.position)
                    }
                    thumb = Offset.Zero
                    currentOnRelease()
                }
            }
            .drawBehind {
                val radius = size.minDimension / 2f
                drawCircle(baseColor.copy(alpha = 0.85f), radius)
                drawCircle(ringColor, radius, style = Stroke(width = 2.dp.toPx()))
                drawCircle(thumbColor, radius * THUMB_RATIO, center = center + thumb)
            }
    )
}

private const val THUMB_RATIO = 0.35f

private fun clampToRadius(offset: Offset, radius: Float): Offset {
    val distance = offset.getDistance()
    return if (distance <= radius) offset else offset * (radius / distance)
}

private class DragTracker {
    var lastX = 0f
    var lastY = 0f
    var travelled = 0f
}

/**
 * Moves the overlay window using raw screen coordinates. Local pointer coordinates can't be used
 * because the window itself moves under the finger.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun rememberOverlayDragModifier(
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    onTap: (() -> Unit)? = null,
): Modifier {
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnTap by rememberUpdatedState(onTap)
    return remember(touchSlop) {
        val tracker = DragTracker()
        Modifier.pointerInteropFilter { event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    tracker.lastX = event.rawX
                    tracker.lastY = event.rawY
                    tracker.travelled = 0f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - tracker.lastX
                    val dy = event.rawY - tracker.lastY
                    tracker.lastX = event.rawX
                    tracker.lastY = event.rawY
                    tracker.travelled += hypot(dx, dy)
                    currentOnDrag(dx, dy)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val tap = currentOnTap
                    if (tap != null && tracker.travelled < touchSlop) tap() else currentOnDragEnd()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    currentOnDragEnd()
                    true
                }
                else -> false
            }
        }
    }
}

@get:StringRes
private val JoystickPreset.labelRes: Int
    get() = when (this) {
        JoystickPreset.WALK -> R.string.setting_joystick_walk_label
        JoystickPreset.RUN -> R.string.setting_joystick_run_label
        JoystickPreset.BIKE -> R.string.setting_joystick_bike_label
        JoystickPreset.DRIVE -> R.string.setting_joystick_drive_label
    }
```

- [ ] **Step 6: The service**

`manager/joystick/JoystickOverlayService.kt`:

```kotlin
package com.noobexon.xposedfakelocation.manager.joystick

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.repository.PreferencesRepository
import com.noobexon.xposedfakelocation.manager.App
import com.noobexon.xposedfakelocation.manager.MainActivity
import com.noobexon.xposedfakelocation.manager.joystick.ui.JoystickOverlay
import com.noobexon.xposedfakelocation.manager.ui.theme.XposedFakeLocationTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Foreground service owning the joystick overlay and its [JoystickSession].
 *
 * Runs only while spoofing is on and a marker exists; ends itself when either stops being true,
 * when the overlay's close button or the notification's Stop action is used, or on destroy.
 */
class JoystickOverlayService : LifecycleService(), SavedStateRegistryOwner {

    companion object {
        private const val TAG = "JoystickOverlayService"
        private const val ACTION_START = "com.noobexon.xposedfakelocation.joystick.START"
        private const val ACTION_STOP = "com.noobexon.xposedfakelocation.joystick.STOP"
        private const val CHANNEL_ID = "joystick_overlay"
        private const val NOTIFICATION_ID = 4201

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, JoystickOverlayService::class.java).setAction(ACTION_START)
            )
        }
    }

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private lateinit var repository: PreferencesRepository
    private var session: JoystickSession? = null
    private var window: JoystickOverlayWindow? = null
    private val collapsed = MutableStateFlow(false)

    override fun onCreate() {
        savedStateRegistryController.performRestore(null)
        super.onCreate()
        repository = PreferencesRepository(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startInForeground()
        when {
            intent?.action == ACTION_STOP -> endSession(keepPosition = true)
            session == null -> beginSession()
            else -> collapsed.value = false
        }
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        window?.clampToScreen()
    }

    override fun onDestroy() {
        releaseSession(keepPosition = true)
        super.onDestroy()
    }

    private fun beginSession() {
        val start = repository.getLastClickedLocation()
        if (App.service == null || start == null || !repository.getIsPlaying() || !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Joystick preconditions not met; stopping")
            stopSelfCompletely()
            return
        }

        val settings = repository.getJoystickSettingsFlow()
            .stateIn(lifecycleScope, SharingStarted.Eagerly, repository.getJoystickSettings())
        val newSession = JoystickSession(
            scope = lifecycleScope,
            startLatitude = start.latitude,
            startLongitude = start.longitude,
            settings = settings,
            publish = { frame -> repository.saveJoystickFrame(frame.latitude, frame.longitude, frame.state) },
            monotonicClock = SystemClock::elapsedRealtime,
        )
        session = newSession

        window = JoystickOverlayWindow(
            context = this,
            lifecycleOwner = this,
            savedStateRegistryOwner = this,
            initialPosition = repository.getJoystickOverlayPosition(),
            onPositionCommitted = repository::saveJoystickOverlayPosition,
        ) {
            XposedFakeLocationTheme {
                val currentSettings by settings.collectAsState()
                val isCollapsed by collapsed.collectAsState()
                JoystickOverlay(
                    settings = currentSettings,
                    collapsed = isCollapsed,
                    onInput = newSession::setInput,
                    onRelease = { newSession.setInput(0f, 0f) },
                    onModeChange = repository::saveJoystickMode,
                    onPresetChange = repository::saveJoystickSelectedPreset,
                    onCollapsedChange = { collapsed.value = it },
                    onClose = { endSession(keepPosition = true) },
                    onDrag = { dx, dy -> window?.moveBy(dx, dy) },
                    onDragEnd = { window?.commitPosition() },
                )
            }
        }.also { it.show() }

        newSession.start()
        observeStopConditions()
    }

    private fun observeStopConditions() {
        // Emits false as soon as the Xposed service disconnects, too.
        lifecycleScope.launch {
            repository.getIsPlayingFlow().collect { isPlaying ->
                if (!isPlaying) endSession(keepPosition = true)
            }
        }
        lifecycleScope.launch {
            repository.getLastClickedLocationFlow().collect { location ->
                if (location == null) {
                    endSession(keepPosition = false)
                } else {
                    session?.onExternalMarker(location.latitude, location.longitude)
                }
            }
        }
    }

    /**
     * @param keepPosition `false` when the marker was cleared: publishing the final position would
     *   put the marker back.
     */
    private fun endSession(keepPosition: Boolean) {
        releaseSession(keepPosition)
        stopSelfCompletely()
    }

    private fun releaseSession(keepPosition: Boolean) {
        session?.let { active ->
            val last = active.stop()
            if (keepPosition) {
                repository.saveJoystickFrame(last.latitude, last.longitude, last.state)
            } else {
                repository.saveJoystickState(last.state)
            }
        }
        session = null
        window?.remove()
        window = null
    }

    private fun stopSelfCompletely() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.joystick_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, JoystickOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(getString(R.string.joystick_notification_title))
            .setContentText(getString(R.string.joystick_notification_text))
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.joystick_notification_stop), stopIntent)
            .setOngoing(true)
            .build()
    }
}
```

- [ ] **Step 7: Compile and test**

Run: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. If `setViewTreeSavedStateRegistryOwner` doesn't resolve, the savedstate artifact on the classpath predates the Kotlin extension; use `androidx.savedstate.ViewTreeSavedStateRegistryOwner.set(view, owner)` instead (same for `ViewTreeLifecycleOwner.set`).

The service is not exported, so there is no way to start it from `adb shell`; its on-device behaviour is checked through the map button in Task 9 and Task 10.

- [ ] **Step 8: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/java/com/noobexon/xposedfakelocation/manager/joystick app/src/main/res/values/strings.xml app/src/main/res/values-de/strings.xml app/src/main/res/values-zh/strings.xml
git commit -m "feat: add joystick overlay foreground service and UI"
```

---

### Task 9: Map-screen entry point and permission flow

**Files:**
- Create: `manager/joystick/JoystickButton.kt`
- Modify: `manager/ui/map/MapScreen.kt`
- Modify: `values/`, `values-de/`, `values-zh/strings.xml`

**Interfaces:**
- Consumes: `JoystickOverlayService.start(context)` (Task 8); existing strings `system_hooks_module_inactive`, `permissions_open_settings`, `action_cancel`
- Produces: `@Composable fun JoystickButton(enabled: Boolean)`

- [ ] **Step 1: Strings**

`values/strings.xml`, append before `</resources>`:

```xml
    <string name="cd_joystick_open">Open joystick</string>
    <string name="joystick_overlay_permission_title">Allow display over other apps</string>
    <string name="joystick_overlay_permission_message">The joystick floats over other apps so you can steer while a target app is open. Grant \"Display over other apps\" on the next screen.</string>
```

`values-de/strings.xml`:

```xml
    <string name="cd_joystick_open">Joystick öffnen</string>
    <string name="joystick_overlay_permission_title">Über anderen Apps anzeigen erlauben</string>
    <string name="joystick_overlay_permission_message">Der Joystick wird über anderen Apps angezeigt, damit du steuern kannst, während eine Ziel-App geöffnet ist. Erlaube auf dem nächsten Bildschirm „Über anderen Apps einblenden“.</string>
```

`values-zh/strings.xml`:

```xml
    <string name="cd_joystick_open">打开摇杆</string>
    <string name="joystick_overlay_permission_title">允许显示在其他应用上层</string>
    <string name="joystick_overlay_permission_message">摇杆会悬浮在其他应用上方，方便你在目标应用打开时进行控制。请在下一个页面授予“显示在其他应用上层”权限。</string>
```

- [ ] **Step 2: The button**

`manager/joystick/JoystickButton.kt`:

```kotlin
package com.noobexon.xposedfakelocation.manager.joystick

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.manager.App

/**
 * Top-bar action that launches the joystick overlay, walking the user through the overlay
 * permission (required) and notification permission (optional, Android 13+) first.
 *
 * @param enabled `true` only while spoofing is on and a marker is placed.
 */
@Composable
fun JoystickButton(enabled: Boolean) {
    val context = LocalContext.current
    var showOverlayRationale by remember { mutableStateOf(false) }
    val moduleInactive = stringResource(R.string.system_hooks_module_inactive)

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { JoystickOverlayService.start(context) }

    fun startWithNotificationPermission() {
        val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            JoystickOverlayService.start(context)
        }
    }

    val overlaySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(context)) startWithNotificationPermission()
    }

    IconButton(
        enabled = enabled,
        onClick = {
            when {
                App.service == null -> Toast.makeText(context, moduleInactive, Toast.LENGTH_SHORT).show()
                !Settings.canDrawOverlays(context) -> showOverlayRationale = true
                else -> startWithNotificationPermission()
            }
        }
    ) {
        Icon(Icons.Default.SportsEsports, contentDescription = stringResource(R.string.cd_joystick_open))
    }

    if (showOverlayRationale) {
        AlertDialog(
            onDismissRequest = { showOverlayRationale = false },
            title = { Text(stringResource(R.string.joystick_overlay_permission_title)) },
            text = { Text(stringResource(R.string.joystick_overlay_permission_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showOverlayRationale = false
                    overlaySettingsLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    )
                }) { Text(stringResource(R.string.permissions_open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayRationale = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
```

- [ ] **Step 3: Wire into the map top bar**

In `manager/ui/map/MapScreen.kt` add the import `import com.noobexon.xposedfakelocation.manager.joystick.JoystickButton` and, as the first item inside `actions = {`, add:

```kotlin
                        JoystickButton(enabled = isPlaying && uiState.lastClickedLocation != null)
```

- [ ] **Step 4: Compile and test**

Run: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/noobexon/xposedfakelocation/manager/joystick/JoystickButton.kt app/src/main/java/com/noobexon/xposedfakelocation/manager/ui/map/MapScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-de/strings.xml app/src/main/res/values-zh/strings.xml
git commit -m "feat: launch joystick overlay from the map screen"
```

---

### Task 10: On-device verification

**Files:** none (fix-ups only if a check fails, committed separately).

- [ ] **Step 1: Install a fresh build**

Run: `.\gradlew.bat :app:assembleDebug` then `adb install -r app\build\outputs\apk\debug\app-debug.apk`; enable the module in LSPosed with a GPS test app (e.g. "GPS Test") as a target, force-stop the test app.

- [ ] **Step 2: Permission flow**

Revoke overlay permission (`adb shell appops set com.noobexon.xposedfakelocation SYSTEM_ALERT_WINDOW deny`). With spoofing off, the joystick icon is disabled. Place a marker, press Play: icon enabled. Tap it → rationale dialog → Open Settings → grant → return. Expected: notification prompt on Android 13+, then the overlay appears.

- [ ] **Step 3: Movement and realism**

Open the GPS test app with the overlay on top. In Dynamic mode push up halfway, then fully: reported speed ≈ 1.5 then 3.0 m/s, bearing ≈ 0°. Push right: bearing ≈ 90°. Release: speed 0, position steady, bearing stays at last value. Switch to Constant → Bike: any push gives 5.5 m/s. Enable "Randomize" in Settings, repeat: no jitter while the overlay is open.

- [ ] **Step 4: Map sync and teleport**

Return to the map while moving: the marker follows. While the overlay is open, tap a new map point: the joystick continues from the new point.

- [ ] **Step 5: Stop paths**

Each of these removes the overlay and notification and leaves the marker at the last position: overlay Close; notification Stop; map Stop button (spoofing off). "Clear location" removes overlay and keeps the marker cleared.

- [ ] **Step 6: Crash fallback**

While holding the stick, run `adb shell am force-stop com.noobexon.xposedfakelocation`. Within ~5 s the GPS test app reverts to the user's normal settings (randomize jitter returns if enabled; speed no longer the joystick's).

- [ ] **Step 7: Rotation and persistence**

Drag the overlay to the bottom-right, rotate the device: it stays on screen. Close and reopen: it opens at the saved spot, with the last mode and preset.

- [ ] **Step 8: Backup**

Export settings, change a joystick speed, import the file: speed restored. Import a backup created before this feature: joystick settings unchanged.
