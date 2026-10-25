# Custom Route Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a third operating mode where the user builds an ordered route of points on the map and the app moves the spoofed location along it with realistic speed and bearing, controlled from a music-player style overlay.

**Architecture:**
- A pure-Kotlin engine (`RoutePlan`, `RoutePlayer`, `RouteSession`) computes the position along the route.
- A new foreground `RoutePlaybackService` runs that engine. It publishes through the joystick's existing `saveJoystickFrame` pipeline, so the hooks are unchanged.
- Routes and the editing draft live in local prefs as Gson JSON.
- The map screen gets a Location / Route switch, and Route mode has its own on-map editing UI.

**Tech Stack:** Kotlin 2.2, Jetpack Compose (BOM 2024.12.01, Material 3), osmdroid 6.1.20, Gson 2.14, kotlinx.coroutines, JUnit 4, `sh.calvin.reorderable` 2.4.3 (new).

**Spec:** `docs/superpowers/specs/2026-10-25-custom-route-design.md`

## Global Constraints

- **Build rule (user):** Do **not** run Gradle builds or tests on your own. The user decides when to build. The "Run" steps below show the exact command to use when the user asks for a build.
- **Lock errors (user):** If Gradle reports `Couldn't delete ... R.jar` or "being used by another process", stop. Do not kill processes or try to fix it. Report it to the user and give them the command.
- **Commands:**
  - Full: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`
  - Focused: `.\gradlew.bat :app:testDebugUnitTest --tests "*ClassName"`
- **Before every commit:** run `git diff --ignore-cr-at-eol --stat` and stage only the files named in the task. The working tree can show CRLF-only noise, and `.github/workflows/release.yml` has an unrelated user change that must not be committed.
- **Units:** speeds are stored in m/s and shown in km/h (`MPS_TO_KMH = 3.6f`).
- **Speed range:** `JOYSTICK_MIN_SPEED_MPS..JOYSTICK_MAX_SPEED_MPS` (1–200 km/h).
- **Route limits:** wait 0–3600 s, variation 0–50 %, at least 2 points to play or save.
- **Strings:** every new string goes in `values/`, `values-de/` and `values-zh/strings.xml`.
- **Hooks:** must not change. Playback publishes `JoystickState(active, bearing, speed, updatedAt)` through `PreferencesRepository.saveJoystickFrame`.
- **Joystick:** the only joystick behavior change is the new "no route playing" start precondition (Task 11).
- **Style:** match surrounding code style. KDoc on public types. Comments only for constraints the code can't show.

## File Map

| File | Responsibility |
|---|---|
| `data/GeoMath.kt` (modify) | + `distanceMeters`, `initialBearing` |
| `data/Constants.kt` (modify) | Route keys and limits |
| `data/model/Route.kt` (new) | `Route`, `RoutePoint`, `SegmentSettings`, `SpeedChoice`, `RouteDraft`, `MapMode`, `RouteSegment`, `segmentKey`, `travelSegments`, `resolveMps`, validation |
| `data/model/RouteJson.kt` (new) | Gson with the `SpeedChoice` adapter; `parseRoutes`, `parseDraft` |
| `data/model/SettingsBackup.kt` (modify) | + `routes` section |
| `data/backup/SettingsBackupCodec.kt`, `SettingsBackupManager.kt` (modify) | Route validation and import |
| `data/repository/PrefrencesRepository.kt` (modify) | Routes, draft, map mode, route overlay position |
| `manager/route/RouteEditor.kt` (new) | Pure draft edits |
| `manager/route/RoutePlan.kt` (new) | `Leg`, `RoutePlan`, `RouteSummary` |
| `manager/route/RoutePlayer.kt` (new) | `RoutePhase`, `PlayerState`, `RouteStep`, `RoutePlayer` |
| `manager/route/RouteSession.kt` (new) | Tick loop, publishing, controls, `RouteStatus` |
| `manager/route/RouteFormat.kt` (new) | Number formatting for distance, time and multiplier |
| `manager/route/RoutePlaybackState.kt` (new) | Process-wide playback status |
| `manager/joystick/JoystickRunningState.kt` (new) | Process-wide joystick running flag |
| `manager/overlay/OverlayWindowHost.kt` (moved from `joystick/JoystickOverlayWindow.kt`) | Shared overlay window |
| `manager/overlay/OverlayDrag.kt` (new, extracted) | Shared drag modifier |
| `manager/route/RoutePlaybackService.kt` (new) | Foreground service, notification, overlay |
| `manager/route/ui/RouteOverlay.kt` (new) | Music-player overlay |
| `manager/route/RoutePlayButton.kt` (new) | Play/Stop button with permission flow |
| `manager/ui/map/route/RouteEditorViewModel.kt` (new) | Draft, mode, sheet and dialog state, save/load |
| `manager/ui/map/route/RouteMapOverlays.kt` (new) | osmdroid markers, lines, arrows; fit-to-route |
| `manager/ui/map/route/RouteMarkerIcons.kt` (new) | Numbered-circle and arrow bitmaps |
| `manager/ui/map/route/RouteText.kt` (new) | Composable distance, duration and speed labels |
| `manager/ui/map/route/MapModeSwitch.kt` (new) | Location / Route segmented button |
| `manager/ui/map/route/SpeedChoicePicker.kt` (new) | Speed chips plus custom km/h field |
| `manager/ui/map/route/RouteSheets.kt` (new) | Segment sheet, point-actions sheet |
| `manager/ui/map/route/RouteDialogs.kt` (new) | Save, set number, default speed, confirm dialogs |
| `manager/ui/map/route/RoutePanel.kt` (new) | Bottom route card with the reorderable list |
| `manager/ui/map/route/RouteModeUi.kt` (new) | Wires the panel, sheets and dialogs to the ViewModel |
| `manager/ui/map/MapViewContainer.kt`, `MapScreen.kt` (modify) | Route-mode integration |
| `manager/ui/navigation/NavGraph.kt` (modify) | Shared `RouteEditorViewModel`, favorites wiring |
| `manager/ui/favorites/FavoritesScreen.kt`, `FavoritesViewModel.kt` (modify), `RouteFavorites.kt` (new) | Routes tab |
| `manager/ui/settings/SettingsViewModel.kt` (modify) | Export routes |
| `AndroidManifest.xml`, `gradle/libs.versions.toml`, `app/build.gradle.kts` (modify) | Service and reorderable dependency |

Paths below are relative to `app/src/main/java/com/noobexon/xposedfakelocation/` (main) or `app/src/test/java/com/noobexon/xposedfakelocation/` (test), unless they start with `app/` or `docs/`.

---

### Task 1: Great-circle distance and initial bearing

**Files:**
- Modify: `data/GeoMath.kt`
- Test: `data/GeoMathTest.kt` (existing file; add tests inside the class)

**Interfaces:**
- Produces: `GeoMath.distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double` and `GeoMath.initialBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double`, which returns degrees in `[0, 360)`.

- [ ] **Step 1: Write the failing tests.** Add these inside `class GeoMathTest`, and add `import org.junit.Assert.assertEquals` if it's missing:

```kotlin
    @Test
    fun distanceOfOneDegreeLatitudeMatchesEarthRadius() {
        val expected = Math.toRadians(1.0) * RADIUS_EARTH
        assertEquals(expected, GeoMath.distanceMeters(0.0, 0.0, 1.0, 0.0), 1e-6)
    }

    @Test
    fun distanceToSelfIsZero() {
        assertEquals(0.0, GeoMath.distanceMeters(10.0, 20.0, 10.0, 20.0), 0.0)
    }

    @Test
    fun initialBearingPointsAlongCardinalDirections() {
        assertEquals(0.0, GeoMath.initialBearing(0.0, 0.0, 1.0, 0.0), 1e-9)
        assertEquals(90.0, GeoMath.initialBearing(0.0, 0.0, 0.0, 1.0), 1e-9)
        assertEquals(180.0, GeoMath.initialBearing(1.0, 0.0, 0.0, 0.0), 1e-9)
        assertEquals(270.0, GeoMath.initialBearing(0.0, 1.0, 0.0, 0.0), 1e-9)
    }

    @Test
    fun destinationRoundTripsThroughDistanceAndBearing() {
        val (lat, lon) = GeoMath.destination(48.0, 11.0, 37.0, 1_500.0)
        assertEquals(1_500.0, GeoMath.distanceMeters(48.0, 11.0, lat, lon), 1e-3)
        assertEquals(37.0, GeoMath.initialBearing(48.0, 11.0, lat, lon), 1e-6)
    }
```

- [ ] **Step 2: Run the tests and verify they fail.** Run `.\gradlew.bat :app:testDebugUnitTest --tests "*GeoMathTest"`. Expected: compile error, unresolved `distanceMeters` / `initialBearing`.

- [ ] **Step 3: Implement.** In `data/GeoMath.kt`, add `import kotlin.math.pow` and `import kotlin.math.sqrt`, then add these inside `object GeoMath`, after `destination`:

```kotlin
    /** Great-circle (haversine) distance in meters between two points. */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = phi2 - phi1
        val dLambda = Math.toRadians(lon2 - lon1)
        val a = sin(dPhi / 2).pow(2) + cos(phi1) * cos(phi2) * sin(dLambda / 2).pow(2)
        return 2 * RADIUS_EARTH * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /** Initial great-circle bearing from the first point to the second, in `[0, 360)` (0 = north). */
    fun initialBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLambda = Math.toRadians(lon2 - lon1)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }
```

- [ ] **Step 4: Run the tests and verify they pass.** Same command. Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/noobexon/xposedfakelocation/data/GeoMath.kt app/src/test/java/com/noobexon/xposedfakelocation/data/GeoMathTest.kt
git commit -m "feat: add great-circle distance and bearing helpers"
```

---

### Task 2: Route model, validation and JSON

**Files:**
- Modify: `data/Constants.kt`
- Create: `data/model/Route.kt`, `data/model/RouteJson.kt`
- Test: `data/model/RouteModelTest.kt`, `data/model/RouteJsonTest.kt`

**Interfaces:**
- Produces:
  - Types: `RoutePoint(id, latitude, longitude)`; `SpeedChoice.Preset(preset: JoystickPreset)` and `SpeedChoice.Custom(speedMps: Float)`; `SegmentSettings(speed: SpeedChoice? = null, dwellSeconds: Int = 0, variationPercent: Int = 0)`.
  - `Route(id, name = "", points = emptyList(), segments = emptyMap(), defaultSpeed = Preset(WALK), loop = false, reversed = false)`.
  - `RouteDraft(route, sourceRouteId: String? = null, dirty = false)`, `MapMode { LOCATION, ROUTE }`, and `RouteSegment(from, to, fromNumber, toNumber, closing)`.
  - Helpers: `segmentKey(a, b)`, `Route.segmentSettings(aId, bId)`, `Route.travelSegments()`, `SpeedChoice.resolveMps(settings)`, `Route.isValid(requireName = true, minPoints = ROUTE_MIN_POINTS)`, `SpeedChoice.isValid()`, `SegmentSettings.isValid()`.
  - JSON: `RouteJson.gson`, `RouteJson.parseRoutes(json: String?): List<Route>`, `RouteJson.parseDraft(json: String?): RouteDraft?`.
  - Constants: `ROUTE_MIN_POINTS = 2`, `ROUTE_MAX_DWELL_SECONDS = 3600`, `ROUTE_MAX_VARIATION_PERCENT = 50`.

- [ ] **Step 1: Add the limits to `data/Constants.kt`.** Put them right after `JOYSTICK_MAX_SPEED_MPS`:

```kotlin
const val ROUTE_MIN_POINTS = 2
const val ROUTE_MAX_DWELL_SECONDS = 3_600
const val ROUTE_MAX_VARIATION_PERCENT = 50
```

- [ ] **Step 2: Write the failing model tests.** Create `data/model/RouteModelTest.kt`:

```kotlin
package com.noobexon.xposedfakelocation.data.model

import com.noobexon.xposedfakelocation.data.JOYSTICK_MAX_SPEED_MPS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteModelTest {
    private fun route(vararg ids: String) =
        Route(id = "r", name = "Trip", points = ids.map { RoutePoint(it, 1.0, 2.0) })

    @Test
    fun segmentKeyIgnoresDirection() {
        assertEquals(segmentKey("a", "b"), segmentKey("b", "a"))
        assertNotEquals(segmentKey("a", "b"), segmentKey("a", "c"))
    }

    @Test
    fun travelSegmentsFollowListOrder() {
        val segments = route("a", "b", "c").travelSegments()
        assertEquals(listOf("a" to "b", "b" to "c"), segments.map { it.from.id to it.to.id })
        assertEquals(listOf(2, 3), segments.map { it.toNumber })
        assertFalse(segments.any { it.closing })
    }

    @Test
    fun travelSegmentsReversedAndLoopedAddClosingSegment() {
        val segments = route("a", "b", "c").copy(reversed = true, loop = true).travelSegments()
        assertEquals(listOf("c" to "b", "b" to "a", "a" to "c"), segments.map { it.from.id to it.to.id })
        assertEquals(listOf(2, 1, 3), segments.map { it.toNumber })
        assertEquals(listOf(false, false, true), segments.map { it.closing })
    }

    @Test
    fun travelSegmentsEmptyBelowTwoPoints() {
        assertTrue(route("a").travelSegments().isEmpty())
        assertTrue(route("a").copy(loop = true).travelSegments().isEmpty())
    }

    @Test
    fun missingSegmentSettingsAreDefaults() {
        val settings = SegmentSettings(dwellSeconds = 5)
        val r = route("a", "b").copy(segments = mapOf(segmentKey("a", "b") to settings))
        assertEquals(settings, r.segmentSettings("b", "a"))
        assertEquals(SegmentSettings(), r.segmentSettings("a", "c"))
    }

    @Test
    fun presetResolvesFromSettingsAndCustomIsClamped() {
        val settings = JoystickSettings(runSpeedMps = 3.5f)
        assertEquals(3.5f, SpeedChoice.Preset(JoystickPreset.RUN).resolveMps(settings), 0f)
        assertEquals(JOYSTICK_MAX_SPEED_MPS, SpeedChoice.Custom(1_000f).resolveMps(settings), 0f)
    }

    @Test
    fun validRouteNeedsTwoPointsAndAName() {
        assertTrue(route("a", "b").isValid())
        assertFalse(route("a").isValid())
        assertFalse(route("a", "b").copy(name = " ").isValid())
        assertTrue(route("a").copy(name = "").isValid(requireName = false, minPoints = 0))
    }

    @Test
    fun outOfRangeValuesFailValidation() {
        val base = route("a", "b")
        assertFalse(base.copy(points = listOf(RoutePoint("a", Double.NaN, 0.0), RoutePoint("b", 0.0, 0.0))).isValid())
        assertFalse(base.copy(defaultSpeed = SpeedChoice.Custom(0f)).isValid())
        val key = segmentKey("a", "b")
        assertFalse(base.copy(segments = mapOf(key to SegmentSettings(dwellSeconds = -1))).isValid())
        assertFalse(base.copy(segments = mapOf(key to SegmentSettings(variationPercent = 60))).isValid())
    }
}
```

- [ ] **Step 3: Write the failing JSON tests.** Create `data/model/RouteJsonTest.kt`:

```kotlin
package com.noobexon.xposedfakelocation.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteJsonTest {
    private val route = Route(
        id = "r1",
        name = "Park loop",
        points = listOf(RoutePoint("a", 1.0, 2.0), RoutePoint("b", 1.5, 2.5)),
        segments = mapOf(segmentKey("a", "b") to SegmentSettings(SpeedChoice.Custom(4.2f), dwellSeconds = 30, variationPercent = 10)),
        defaultSpeed = SpeedChoice.Preset(JoystickPreset.BIKE),
        loop = true,
        reversed = true,
    )

    @Test
    fun routesRoundTrip() {
        assertEquals(listOf(route), RouteJson.parseRoutes(RouteJson.gson.toJson(listOf(route))))
    }

    @Test
    fun speedChoiceUsesTypedShape() {
        val json = RouteJson.gson.toJson(route)
        assertTrue(json.contains("\"type\":\"preset\""))
        assertTrue(json.contains("\"preset\":\"BIKE\""))
        assertTrue(json.contains("\"type\":\"custom\""))
    }

    @Test
    fun invalidRoutesAreDropped() {
        val bad = route.copy(id = "r2", points = route.points.take(1))
        assertEquals(listOf(route), RouteJson.parseRoutes(RouteJson.gson.toJson(listOf(route, bad))))
    }

    @Test
    fun unknownSpeedTypeInvalidatesRoute() {
        val json = RouteJson.gson.toJson(listOf(route)).replace("\"type\":\"preset\"", "\"type\":\"teleport\"")
        assertTrue(RouteJson.parseRoutes(json).isEmpty())
    }

    @Test
    fun garbageParsesToEmptyList() {
        assertTrue(RouteJson.parseRoutes("not json").isEmpty())
        assertTrue(RouteJson.parseRoutes(null).isEmpty())
    }

    @Test
    fun draftRoundTripsWithFewPointsAndNoName() {
        val draft = RouteDraft(route.copy(name = "", points = route.points.take(1)), sourceRouteId = "r1", dirty = true)
        assertEquals(draft, RouteJson.parseDraft(RouteJson.gson.toJson(draft)))
    }

    @Test
    fun brokenDraftParsesToNull() {
        assertNull(RouteJson.parseDraft("{}"))
        assertNull(RouteJson.parseDraft("["))
        assertNull(RouteJson.parseDraft(null))
    }
}
```

- [ ] **Step 4: Run the tests and verify they fail.** Run `.\gradlew.bat :app:testDebugUnitTest --tests "*RouteModelTest" --tests "*RouteJsonTest"`. Expected: compile errors, unresolved `Route` etc.

- [ ] **Step 5: Create `data/model/Route.kt`**

```kotlin
package com.noobexon.xposedfakelocation.data.model

import com.noobexon.xposedfakelocation.data.JOYSTICK_MAX_SPEED_MPS
import com.noobexon.xposedfakelocation.data.JOYSTICK_MIN_SPEED_MPS
import com.noobexon.xposedfakelocation.data.ROUTE_MAX_DWELL_SECONDS
import com.noobexon.xposedfakelocation.data.ROUTE_MAX_VARIATION_PERCENT
import com.noobexon.xposedfakelocation.data.ROUTE_MIN_POINTS

/** One route point. [id] stays the same across reorders and moves, so segment settings follow it. */
data class RoutePoint(val id: String, val latitude: Double, val longitude: Double)

/** How fast a route, or one of its segments, moves. */
sealed interface SpeedChoice {
    /** Resolved from the joystick preset speeds in Settings when playback starts. */
    data class Preset(val preset: JoystickPreset) : SpeedChoice
    data class Custom(val speedMps: Float) : SpeedChoice
}

/** Per-segment overrides; the defaults mean "use the route's settings". */
data class SegmentSettings(
    /** `null` = the route's default speed. */
    val speed: SpeedChoice? = null,
    /** Wait at the end point reached in the current travel direction. */
    val dwellSeconds: Int = 0,
    /** Speed jitter of +/- this many percent. */
    val variationPercent: Int = 0,
)

/** A user-built route. List order of [points] is travel order (1..N). */
data class Route(
    val id: String,
    val name: String = "",
    val points: List<RoutePoint> = emptyList(),
    /** Keyed by [segmentKey]; a missing entry means [SegmentSettings] defaults. */
    val segments: Map<String, SegmentSettings> = emptyMap(),
    val defaultSpeed: SpeedChoice = SpeedChoice.Preset(JoystickPreset.WALK),
    /** Adds a closing segment from the last point back to the first. */
    val loop: Boolean = false,
    /** Plays N -> 1 without renumbering the points. */
    val reversed: Boolean = false,
)

/** The route being edited on the map. [sourceRouteId] is the saved route it came from, if any. */
data class RouteDraft(val route: Route, val sourceRouteId: String? = null, val dirty: Boolean = false)

/** Which mode the map screen edits. */
enum class MapMode { LOCATION, ROUTE }

/** A segment in travel order, with the 1-based displayed numbers of its ends. */
data class RouteSegment(
    val from: RoutePoint,
    val to: RoutePoint,
    val fromNumber: Int,
    val toNumber: Int,
    val closing: Boolean,
)

/** Direction-independent key, so a segment's settings apply in reverse too. */
fun segmentKey(a: String, b: String): String = if (a <= b) "$a|$b" else "$b|$a"

fun Route.segmentSettings(aId: String, bId: String): SegmentSettings =
    segments[segmentKey(aId, bId)] ?: SegmentSettings()

/** Segments in travel order, honouring [Route.reversed] and [Route.loop]. Empty below two points. */
fun Route.travelSegments(): List<RouteSegment> {
    if (points.size < 2) return emptyList()
    val numbered = points.mapIndexed { index, point -> index + 1 to point }
    val ordered = if (reversed) numbered.asReversed() else numbered
    val open = ordered.zipWithNext().map { (a, b) ->
        RouteSegment(a.second, b.second, a.first, b.first, closing = false)
    }
    if (!loop) return open
    val (lastNumber, last) = ordered.last()
    val (firstNumber, first) = ordered.first()
    return open + RouteSegment(last, first, lastNumber, firstNumber, closing = true)
}

fun SpeedChoice.resolveMps(settings: JoystickSettings): Float = when (this) {
    is SpeedChoice.Preset -> settings.presetSpeedMps(preset)
    is SpeedChoice.Custom -> speedMps
}.coerceIn(JOYSTICK_MIN_SPEED_MPS, JOYSTICK_MAX_SPEED_MPS)

// Gson bypasses Kotlin null-safety, so stored routes are checked for nulls explicitly.
@Suppress("SENSELESS_COMPARISON")
fun SpeedChoice.isValid(): Boolean = when (this) {
    is SpeedChoice.Preset -> preset != null
    is SpeedChoice.Custom -> speedMps.isFinite() && speedMps in JOYSTICK_MIN_SPEED_MPS..JOYSTICK_MAX_SPEED_MPS
}

fun SegmentSettings.isValid(): Boolean =
    (speed?.isValid() ?: true) &&
        dwellSeconds in 0..ROUTE_MAX_DWELL_SECONDS &&
        variationPercent in 0..ROUTE_MAX_VARIATION_PERCENT

@Suppress("SENSELESS_COMPARISON")
fun Route.isValid(requireName: Boolean = true, minPoints: Int = ROUTE_MIN_POINTS): Boolean {
    if (id == null || id.isBlank() || name == null || points == null || segments == null || defaultSpeed == null) return false
    if (requireName && name.isBlank()) return false
    if (points.size < minPoints) return false
    val pointsOk = points.all {
        it != null && it.id != null &&
            it.latitude.isFinite() && it.longitude.isFinite() &&
            it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0
    }
    return pointsOk && defaultSpeed.isValid() && segments.values.all { it != null && it.isValid() }
}
```

- [ ] **Step 6: Create `data/model/RouteJson.kt`**

```kotlin
package com.noobexon.xposedfakelocation.data.model

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

/** Gson setup and tolerant parsing for stored and backed-up routes. */
object RouteJson {
    val gson: Gson = GsonBuilder()
        .registerTypeHierarchyAdapter(SpeedChoice::class.java, SpeedChoiceAdapter)
        .create()

    private val routeListType: Type = object : TypeToken<List<Route?>>() {}.type

    /** Saved routes from [json]; malformed input gives an empty list and invalid routes are dropped. */
    fun parseRoutes(json: String?): List<Route> {
        if (json.isNullOrBlank()) return emptyList()
        val parsed: List<Route?> = try {
            gson.fromJson<List<Route?>?>(json, routeListType)
        } catch (e: RuntimeException) {
            null
        } ?: return emptyList()
        return parsed.filterNotNull().filter { it.isValid() }
    }

    /** The editing draft from [json], or `null` when it is missing or broken. */
    fun parseDraft(json: String?): RouteDraft? {
        if (json.isNullOrBlank()) return null
        val draft: RouteDraft = try {
            gson.fromJson<RouteDraft?>(json, RouteDraft::class.java)
        } catch (e: RuntimeException) {
            null
        } ?: return null
        @Suppress("SENSELESS_COMPARISON")
        val routeOk = draft.route != null && draft.route.isValid(requireName = false, minPoints = 0)
        return draft.takeIf { routeOk }
    }
}

/** Writes `{"type":"preset","preset":"WALK"}` or `{"type":"custom","speedMps":4.2}`; unknown shapes read as `null`. */
private object SpeedChoiceAdapter : JsonSerializer<SpeedChoice>, JsonDeserializer<SpeedChoice> {
    override fun serialize(src: SpeedChoice, typeOfSrc: Type, context: JsonSerializationContext): JsonElement =
        JsonObject().apply {
            when (src) {
                is SpeedChoice.Preset -> {
                    addProperty("type", "preset")
                    addProperty("preset", src.preset.name)
                }
                is SpeedChoice.Custom -> {
                    addProperty("type", "custom")
                    addProperty("speedMps", src.speedMps)
                }
            }
        }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): SpeedChoice? {
        val obj = json.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        return when (obj.string("type")) {
            "preset" -> JoystickPreset.entries.firstOrNull { it.name == obj.string("preset") }?.let(SpeedChoice::Preset)
            "custom" -> obj.get("speedMps")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                ?.asFloat
                ?.let(SpeedChoice::Custom)
            else -> null
        }
    }

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
}
```

- [ ] **Step 7: Run the tests and verify they pass.** Same command as Step 4. Expected: PASS.

- [ ] **Step 8: Commit**

```powershell
git add app/src/main/java/com/noobexon/xposedfakelocation/data/Constants.kt app/src/main/java/com/noobexon/xposedfakelocation/data/model/Route.kt app/src/main/java/com/noobexon/xposedfakelocation/data/model/RouteJson.kt app/src/test/java/com/noobexon/xposedfakelocation/data/model/RouteModelTest.kt app/src/test/java/com/noobexon/xposedfakelocation/data/model/RouteJsonTest.kt
git commit -m "feat: add route model, validation and JSON format"
```

---

### Task 3: Draft editing functions

**Files:**
- Create: `manager/route/RouteEditor.kt`
- Test: `manager/route/RouteEditorTest.kt`

**Interfaces:**
- Consumes: from Task 2, `Route`, `RoutePoint`, `SegmentSettings`, `segmentKey`, `travelSegments`.
- Produces: `object RouteEditor` with `append(route, latitude, longitude, id)`, `delete(route, id)`, `move(route, id, latitude, longitude)`, `setOrder(route, ids: List<String>)`, `moveTo(route, id, number: Int /* 1-based */)`, `moveEarlier(route, id)`, `moveLater(route, id)`, `setSegment(route, aId, bId, settings)` and `pruneSegments(route)`. All return `Route`.

- [ ] **Step 1: Write the failing tests.** Create `manager/route/RouteEditorTest.kt`:

```kotlin
package com.noobexon.xposedfakelocation.manager.route

import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.data.model.RoutePoint
import com.noobexon.xposedfakelocation.data.model.SegmentSettings
import com.noobexon.xposedfakelocation.data.model.segmentKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RouteEditorTest {
    private fun route(vararg ids: String) =
        Route(id = "r", points = ids.mapIndexed { i, id -> RoutePoint(id, i.toDouble(), 0.0) })

    private fun ids(route: Route) = route.points.map { it.id }

    @Test
    fun appendAddsPointAtTheEnd() {
        val updated = RouteEditor.append(route("a"), 5.0, 6.0, "b")
        assertEquals(listOf("a", "b"), ids(updated))
        assertEquals(RoutePoint("b", 5.0, 6.0), updated.points.last())
    }

    @Test
    fun deleteRemovesPointButKeepsSegmentSettingsUntilPruned() {
        val withSettings = route("a", "b", "c").copy(
            segments = mapOf(
                segmentKey("a", "b") to SegmentSettings(dwellSeconds = 1),
                segmentKey("b", "c") to SegmentSettings(dwellSeconds = 2),
            )
        )
        val deleted = RouteEditor.delete(withSettings, "c")
        assertEquals(listOf("a", "b"), ids(deleted))
        assertEquals(2, deleted.segments.size)
        assertEquals(setOf(segmentKey("a", "b")), RouteEditor.pruneSegments(deleted).segments.keys)
    }

    @Test
    fun moveKeepsIdAndOrder() {
        val moved = RouteEditor.move(route("a", "b"), "a", 9.0, 8.0)
        assertEquals(listOf("a", "b"), ids(moved))
        assertEquals(RoutePoint("a", 9.0, 8.0), moved.points.first())
    }

    @Test
    fun moveToEarlierAndLaterReorder() {
        val r = route("a", "b", "c", "d")
        assertEquals(listOf("c", "a", "b", "d"), ids(RouteEditor.moveTo(r, "c", 1)))
        assertEquals(listOf("b", "c", "d", "a"), ids(RouteEditor.moveTo(r, "a", 99)))
        assertEquals(listOf("a", "c", "b", "d"), ids(RouteEditor.moveEarlier(r, "c")))
        assertEquals(listOf("a", "c", "b", "d"), ids(RouteEditor.moveLater(r, "b")))
        assertSame(r, RouteEditor.moveEarlier(r, "a"))
        assertSame(r, RouteEditor.moveLater(r, "d"))
    }

    @Test
    fun setOrderAppliesIdsAndIgnoresMismatchedLists() {
        val r = route("a", "b", "c")
        assertEquals(listOf("c", "a", "b"), ids(RouteEditor.setOrder(r, listOf("c", "a", "b"))))
        assertSame(r, RouteEditor.setOrder(r, listOf("a", "b")))
    }

    @Test
    fun setSegmentStoresOverridesAndDropsDefaults() {
        val r = route("a", "b")
        val custom = SegmentSettings(variationPercent = 20)
        val withCustom = RouteEditor.setSegment(r, "b", "a", custom)
        assertEquals(custom, withCustom.segments[segmentKey("a", "b")])
        assertEquals(emptyMap<String, SegmentSettings>(), RouteEditor.setSegment(withCustom, "a", "b", SegmentSettings()).segments)
    }

    @Test
    fun pruneKeepsClosingSegmentWhenLooping() {
        val r = route("a", "b", "c").copy(
            loop = true,
            segments = mapOf(segmentKey("c", "a") to SegmentSettings(dwellSeconds = 3)),
        )
        assertEquals(r.segments, RouteEditor.pruneSegments(r).segments)
        assertEquals(emptyMap<String, SegmentSettings>(), RouteEditor.pruneSegments(r.copy(loop = false)).segments)
    }
}
```

- [ ] **Step 2: Run the tests and verify they fail.** Run `.\gradlew.bat :app:testDebugUnitTest --tests "*RouteEditorTest"`. Expected: unresolved `RouteEditor`.

- [ ] **Step 3: Implement.** Create `manager/route/RouteEditor.kt`:

```kotlin
package com.noobexon.xposedfakelocation.manager.route

import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.data.model.RoutePoint
import com.noobexon.xposedfakelocation.data.model.SegmentSettings
import com.noobexon.xposedfakelocation.data.model.segmentKey
import com.noobexon.xposedfakelocation.data.model.travelSegments

/**
 * Pure edits on a [Route]. Segment settings are keyed by point IDs, so they survive reorders for
 * pairs that are still neighbours; [pruneSegments] drops the rest when a route is saved.
 */
object RouteEditor {
    fun append(route: Route, latitude: Double, longitude: Double, id: String): Route =
        route.copy(points = route.points + RoutePoint(id, latitude, longitude))

    fun delete(route: Route, id: String): Route =
        route.copy(points = route.points.filterNot { it.id == id })

    fun move(route: Route, id: String, latitude: Double, longitude: Double): Route =
        route.copy(points = route.points.map { if (it.id == id) it.copy(latitude = latitude, longitude = longitude) else it })

    /** Reorders to [ids]; returns [route] unchanged unless [ids] lists exactly its points. */
    fun setOrder(route: Route, ids: List<String>): Route {
        val byId = route.points.associateBy { it.id }
        val ordered = ids.mapNotNull { byId[it] }
        if (ordered.size != route.points.size || ordered.toSet().size != ordered.size) return route
        return route.copy(points = ordered)
    }

    /** Moves the point to 1-based position [number], clamped to the route. */
    fun moveTo(route: Route, id: String, number: Int): Route {
        val from = route.points.indexOfFirst { it.id == id }
        if (from < 0) return route
        val to = (number - 1).coerceIn(0, route.points.lastIndex)
        if (from == to) return route
        val points = route.points.toMutableList()
        points.add(to, points.removeAt(from))
        return route.copy(points = points)
    }

    fun moveEarlier(route: Route, id: String): Route {
        val index = route.points.indexOfFirst { it.id == id }
        return if (index <= 0) route else moveTo(route, id, number = index)
    }

    fun moveLater(route: Route, id: String): Route {
        val index = route.points.indexOfFirst { it.id == id }
        return if (index < 0 || index == route.points.lastIndex) route else moveTo(route, id, number = index + 2)
    }

    fun setSegment(route: Route, aId: String, bId: String, settings: SegmentSettings): Route {
        val key = segmentKey(aId, bId)
        val segments = if (settings == SegmentSettings()) route.segments - key else route.segments + (key to settings)
        return route.copy(segments = segments)
    }

    fun pruneSegments(route: Route): Route {
        val live = route.travelSegments().map { segmentKey(it.from.id, it.to.id) }.toSet()
        return route.copy(segments = route.segments.filterKeys { it in live })
    }
}
```

- [ ] **Step 4: Run the tests and verify they pass.** Same command. Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/noobexon/xposedfakelocation/manager/route/RouteEditor.kt app/src/test/java/com/noobexon/xposedfakelocation/manager/route/RouteEditorTest.kt
git commit -m "feat: add pure route draft editing"
```

---

### Task 4: Route plan (legs, totals, summary)

**Files:**
- Create: `manager/route/RoutePlan.kt`
- Test: `manager/route/RoutePlanTest.kt`

**Interfaces:**
- Consumes:
  - From Task 1: `GeoMath.distanceMeters`, `GeoMath.initialBearing`.
  - From Task 2: `travelSegments`, `segmentSettings`, `resolveMps`.
- Produces:
  - `data class Leg(startLatitude, startLongitude, endLatitude, endLongitude: Double, distanceM: Double, bearingDegrees: Float, speedMps: Float, variation: Double /* 0.0..0.5 */, dwellSeconds: Double, endPointNumber: Int)`.
  - `class RoutePlan(legs: List<Leg>, loop: Boolean)` with `totalDistanceM`, `estimatedDurationS`, `startLatitude`, `startLongitude` and `legStartM(index)`. `RoutePlan.from(route, settings)` throws `IllegalArgumentException` below 2 points.
  - `data class RouteSummary(pointCount: Int, distanceM: Double, durationS: Double)`, with `RouteSummary.of(route, settings)`.

- [ ] **Step 1: Write the failing tests.** Create `manager/route/RoutePlanTest.kt`:

```kotlin
package com.noobexon.xposedfakelocation.manager.route

import com.noobexon.xposedfakelocation.data.GeoMath
import com.noobexon.xposedfakelocation.data.model.JoystickPreset
import com.noobexon.xposedfakelocation.data.model.JoystickSettings
import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.data.model.RoutePoint
import com.noobexon.xposedfakelocation.data.model.SegmentSettings
import com.noobexon.xposedfakelocation.data.model.SpeedChoice
import com.noobexon.xposedfakelocation.data.model.segmentKey
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutePlanTest {
    private val a = RoutePoint("a", 0.0, 0.0)
    private val b = RoutePoint("b", 0.001, 0.0)
    private val c = RoutePoint("c", 0.001, 0.001)
    private val settings = JoystickSettings(walkSpeedMps = 1.5f, runSpeedMps = 3.5f)
    private fun route(vararg points: RoutePoint) = Route(id = "r", points = points.toList())

    @Test
    fun buildsOneLegPerSegmentWithDistanceAndBearing() {
        val plan = RoutePlan.from(route(a, b, c), settings)
        assertEquals(2, plan.legs.size)
        assertEquals(GeoMath.distanceMeters(0.0, 0.0, 0.001, 0.0), plan.legs[0].distanceM, 1e-9)
        assertEquals(0f, plan.legs[0].bearingDegrees, 1e-3f)
        assertEquals(90f, plan.legs[1].bearingDegrees, 1e-3f)
        assertEquals(listOf(2, 3), plan.legs.map { it.endPointNumber })
        assertEquals(plan.legs.sumOf { it.distanceM }, plan.totalDistanceM, 1e-9)
        assertEquals(plan.legs[0].distanceM, plan.legStartM(1), 1e-9)
    }

    @Test
    fun reversedLoopStartsAtLastPointAndClosesTheCircuit() {
        val plan = RoutePlan.from(route(a, b, c).copy(reversed = true, loop = true), settings)
        assertEquals(3, plan.legs.size)
        assertEquals(c.latitude, plan.startLatitude, 0.0)
        assertEquals(c.longitude, plan.startLongitude, 0.0)
        assertEquals(3, plan.legs.last().endPointNumber)
        assertEquals(true, plan.loop)
    }

    @Test
    fun speedComesFromOverrideElseDefaultAndIsClamped() {
        val r = route(a, b, c).copy(
            defaultSpeed = SpeedChoice.Preset(JoystickPreset.RUN),
            segments = mapOf(segmentKey("b", "c") to SegmentSettings(speed = SpeedChoice.Custom(10f))),
        )
        val plan = RoutePlan.from(r, settings)
        assertEquals(3.5f, plan.legs[0].speedMps, 0f)
        assertEquals(10f, plan.legs[1].speedMps, 0f)
    }

    @Test
    fun durationIncludesWaitsAndVariationIsAFraction() {
        val r = route(a, b).copy(
            defaultSpeed = SpeedChoice.Custom(10f),
            segments = mapOf(segmentKey("a", "b") to SegmentSettings(dwellSeconds = 30, variationPercent = 20)),
        )
        val plan = RoutePlan.from(r, settings)
        assertEquals(plan.totalDistanceM / 10.0 + 30.0, plan.estimatedDurationS, 1e-6)
        assertEquals(0.2, plan.legs[0].variation, 1e-9)
        assertEquals(30.0, plan.legs[0].dwellSeconds, 0.0)
    }

    @Test
    fun summaryOfShortRouteHasNoDistance() {
        assertEquals(RouteSummary(1, 0.0, 0.0), RouteSummary.of(route(a), settings))
        assertEquals(2, RouteSummary.of(route(a, b), settings).pointCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun planNeedsTwoPoints() {
        RoutePlan.from(route(a), settings)
    }
}
```

- [ ] **Step 2: Run the tests and verify they fail.** Run `.\gradlew.bat :app:testDebugUnitTest --tests "*RoutePlanTest"`. Expected: unresolved `RoutePlan`.

- [ ] **Step 3: Implement.** Create `manager/route/RoutePlan.kt`:

```kotlin
package com.noobexon.xposedfakelocation.manager.route

import com.noobexon.xposedfakelocation.data.GeoMath
import com.noobexon.xposedfakelocation.data.ROUTE_MAX_DWELL_SECONDS
import com.noobexon.xposedfakelocation.data.ROUTE_MAX_VARIATION_PERCENT
import com.noobexon.xposedfakelocation.data.ROUTE_MIN_POINTS
import com.noobexon.xposedfakelocation.data.model.JoystickSettings
import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.data.model.RouteSegment
import com.noobexon.xposedfakelocation.data.model.resolveMps
import com.noobexon.xposedfakelocation.data.model.segmentSettings
import com.noobexon.xposedfakelocation.data.model.travelSegments

/** One straight stretch of a [RoutePlan], in travel direction. */
data class Leg(
    val startLatitude: Double,
    val startLongitude: Double,
    val endLatitude: Double,
    val endLongitude: Double,
    val distanceM: Double,
    val bearingDegrees: Float,
    val speedMps: Float,
    /** Speed jitter as a fraction, 0.0..0.5. */
    val variation: Double,
    val dwellSeconds: Double,
    /** 1-based displayed number of the point this leg arrives at. */
    val endPointNumber: Int,
)

/** A route resolved for playback: legs in travel order with speeds fixed from the current settings. */
class RoutePlan(val legs: List<Leg>, val loop: Boolean) {
    init {
        require(legs.isNotEmpty()) { "A route plan needs at least one leg" }
    }

    private val legStarts = DoubleArray(legs.size).also { starts ->
        var acc = 0.0
        legs.forEachIndexed { i, leg ->
            starts[i] = acc
            acc += leg.distanceM
        }
    }

    val totalDistanceM: Double = legs.sumOf { it.distanceM }

    /** Base speeds plus waits; ignores variation and the playback multiplier. */
    val estimatedDurationS: Double = legs.sumOf { it.distanceM / it.speedMps + it.dwellSeconds }

    val startLatitude: Double get() = legs.first().startLatitude
    val startLongitude: Double get() = legs.first().startLongitude

    /** Distance from the route start to the start of leg [index]. */
    fun legStartM(index: Int): Double = legStarts[index]

    companion object {
        fun from(route: Route, settings: JoystickSettings): RoutePlan =
            RoutePlan(route.travelSegments().map { leg(route, it, settings) }, route.loop)

        private fun leg(route: Route, segment: RouteSegment, settings: JoystickSettings): Leg {
            val from = segment.from
            val to = segment.to
            val overrides = route.segmentSettings(from.id, to.id)
            return Leg(
                startLatitude = from.latitude,
                startLongitude = from.longitude,
                endLatitude = to.latitude,
                endLongitude = to.longitude,
                distanceM = GeoMath.distanceMeters(from.latitude, from.longitude, to.latitude, to.longitude),
                bearingDegrees = GeoMath.initialBearing(from.latitude, from.longitude, to.latitude, to.longitude).toFloat(),
                speedMps = (overrides.speed ?: route.defaultSpeed).resolveMps(settings),
                variation = overrides.variationPercent.coerceIn(0, ROUTE_MAX_VARIATION_PERCENT) / 100.0,
                dwellSeconds = overrides.dwellSeconds.coerceIn(0, ROUTE_MAX_DWELL_SECONDS).toDouble(),
                endPointNumber = segment.toNumber,
            )
        }
    }
}

/** What the route panel and favorites show about a route. */
data class RouteSummary(val pointCount: Int, val distanceM: Double, val durationS: Double) {
    companion object {
        fun of(route: Route, settings: JoystickSettings): RouteSummary {
            if (route.points.size < ROUTE_MIN_POINTS) return RouteSummary(route.points.size, 0.0, 0.0)
            val plan = RoutePlan.from(route, settings)
            return RouteSummary(route.points.size, plan.totalDistanceM, plan.estimatedDurationS)
        }
    }
}
```

- [ ] **Step 4: Run the tests and verify they pass.** Same command. Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/noobexon/xposedfakelocation/manager/route/RoutePlan.kt app/src/test/java/com/noobexon/xposedfakelocation/manager/route/RoutePlanTest.kt
git commit -m "feat: resolve routes into playable legs"
```

---

### Task 5: Route player (movement, waits, loop, seek, variation)

**Files:**
- Create: `manager/route/RoutePlayer.kt`
- Test: `manager/route/RoutePlayerTest.kt`

**Interfaces:**
- Consumes:
  - From Task 4: `RoutePlan`, `Leg`.
  - From existing code: `GeoMath.destination`.
- Produces:
  - `enum class RoutePhase { RUNNING, PAUSED, FINISHED }`.
  - `data class PlayerState(legIndex = 0, distanceIntoLegM = 0.0, waitRemainingS = 0.0, variationFactor = 1.0, variationTarget = 1.0, secondsToNextTarget = 0.0, phase = RUNNING)`.
  - `data class RouteStep(state: PlayerState, latitude: Double, longitude: Double, bearingDegrees: Float, speedMps: Float)`.
  - `object RoutePlayer` with `step(plan, state, dtSeconds, multiplier, random): RouteStep`, `snapshot(plan, state, multiplier): RouteStep`, `seek(plan, state, fraction: Float): PlayerState`, `progress(plan, state): Float` and `distanceCovered(plan, state): Double`.

- [ ] **Step 1: Write the failing tests.** Create `manager/route/RoutePlayerTest.kt`:

```kotlin
package com.noobexon.xposedfakelocation.manager.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class RoutePlayerTest {
    private fun leg(distance: Double, speed: Float = 1f, dwell: Double = 0.0, variation: Double = 0.0, end: Int = 2) =
        Leg(0.0, 0.0, 0.0, 0.0, distance, 0f, speed, variation, dwell, end)

    private val random = Random(42)

    private fun run(plan: RoutePlan, state: PlayerState, seconds: Double, multiplier: Float = 1f) =
        RoutePlayer.step(plan, state, seconds, multiplier, random)

    @Test
    fun advancesBySpeedTimesTime() {
        val plan = RoutePlan(listOf(leg(100.0, speed = 2f)), loop = false)
        val step = run(plan, PlayerState(), 3.0)
        assertEquals(6.0, step.state.distanceIntoLegM, 1e-9)
        assertEquals(2f, step.speedMps, 0f)
    }

    @Test
    fun carriesLeftoverDistanceIntoNextLeg() {
        val plan = RoutePlan(listOf(leg(10.0), leg(10.0, end = 3)), loop = false)
        val step = run(plan, PlayerState(), 12.0)
        assertEquals(1, step.state.legIndex)
        assertEquals(2.0, step.state.distanceIntoLegM, 1e-9)
    }

    @Test
    fun waitsAtLegEndWithZeroSpeed() {
        val plan = RoutePlan(listOf(leg(10.0, dwell = 5.0), leg(10.0, end = 3)), loop = false)
        val waiting = run(plan, PlayerState(), 13.0)
        assertEquals(0, waiting.state.legIndex)
        assertEquals(2.0, waiting.state.waitRemainingS, 1e-9)
        assertEquals(0f, waiting.speedMps, 0f)
        val moving = run(plan, waiting.state, 3.0)
        assertEquals(1, moving.state.legIndex)
        assertEquals(1.0, moving.state.distanceIntoLegM, 1e-9)
    }

    @Test
    fun oneTimeRouteFinishesAtTheEnd() {
        val plan = RoutePlan(listOf(leg(10.0)), loop = false)
        val step = run(plan, PlayerState(), 60.0)
        assertEquals(RoutePhase.FINISHED, step.state.phase)
        assertEquals(0f, step.speedMps, 0f)
        assertEquals(1f, RoutePlayer.progress(plan, step.state), 0f)
    }

    @Test
    fun loopWrapsToFirstLeg() {
        val plan = RoutePlan(listOf(leg(10.0), leg(10.0, end = 1)), loop = true)
        val step = run(plan, PlayerState(), 25.0)
        assertEquals(RoutePhase.RUNNING, step.state.phase)
        assertEquals(0, step.state.legIndex)
        assertEquals(5.0, step.state.distanceIntoLegM, 1e-9)
    }

    @Test
    fun multiplierScalesMovementAndReportedSpeed() {
        val plan = RoutePlan(listOf(leg(100.0, speed = 2f)), loop = false)
        val step = run(plan, PlayerState(), 3.0, multiplier = 2f)
        assertEquals(12.0, step.state.distanceIntoLegM, 1e-9)
        assertEquals(4f, step.speedMps, 0f)
    }

    @Test
    fun pausedDoesNotMove() {
        val plan = RoutePlan(listOf(leg(100.0)), loop = false)
        val step = run(plan, PlayerState(phase = RoutePhase.PAUSED, distanceIntoLegM = 4.0), 10.0)
        assertEquals(4.0, step.state.distanceIntoLegM, 0.0)
        assertEquals(0f, step.speedMps, 0f)
    }

    @Test
    fun seekMapsFractionToLegAndCancelsWait() {
        val plan = RoutePlan(listOf(leg(10.0), leg(10.0, end = 3)), loop = false)
        val sought = RoutePlayer.seek(plan, PlayerState(waitRemainingS = 4.0), 0.75f)
        assertEquals(1, sought.legIndex)
        assertEquals(5.0, sought.distanceIntoLegM, 1e-9)
        assertEquals(0.0, sought.waitRemainingS, 0.0)
        assertEquals(0.75f, RoutePlayer.progress(plan, sought), 1e-6f)
    }

    @Test
    fun seekingAFinishedRouteBackPausesIt() {
        val plan = RoutePlan(listOf(leg(10.0)), loop = false)
        val sought = RoutePlayer.seek(plan, PlayerState(phase = RoutePhase.FINISHED, distanceIntoLegM = 10.0), 0.2f)
        assertEquals(RoutePhase.PAUSED, sought.phase)
        assertEquals(2.0, sought.distanceIntoLegM, 1e-9)
    }

    @Test
    fun variationStaysInBoundsAndChangesSmoothly() {
        val plan = RoutePlan(listOf(leg(1_000_000.0, speed = 10f, variation = 0.2)), loop = false)
        var state = PlayerState()
        repeat(2_000) {
            val previous = state.variationFactor
            state = RoutePlayer.step(plan, state, 0.1, 1f, random).state
            assertTrue(state.variationFactor in 0.8..1.2)
            assertTrue(abs(state.variationFactor - previous) <= 0.01 + 1e-9)
        }
    }
}
```

- [ ] **Step 2: Run the tests and verify they fail.** Run `.\gradlew.bat :app:testDebugUnitTest --tests "*RoutePlayerTest"`. Expected: unresolved `RoutePlayer`.

- [ ] **Step 3: Implement.** Create `manager/route/RoutePlayer.kt`:

```kotlin
package com.noobexon.xposedfakelocation.manager.route

import com.noobexon.xposedfakelocation.data.GeoMath
import kotlin.random.Random

enum class RoutePhase { RUNNING, PAUSED, FINISHED }

/** Where playback is along a [RoutePlan]. While [waitRemainingS] > 0 it holds at the end of [legIndex]. */
data class PlayerState(
    val legIndex: Int = 0,
    val distanceIntoLegM: Double = 0.0,
    val waitRemainingS: Double = 0.0,
    val variationFactor: Double = 1.0,
    val variationTarget: Double = 1.0,
    val secondsToNextTarget: Double = 0.0,
    val phase: RoutePhase = RoutePhase.RUNNING,
)

/** The state after a step plus what to publish to target apps. */
data class RouteStep(
    val state: PlayerState,
    val latitude: Double,
    val longitude: Double,
    val bearingDegrees: Float,
    val speedMps: Float,
)

/** Pure playback maths over a [RoutePlan]. */
object RoutePlayer {
    private const val MAX_VARIATION_RATE_PER_S = 0.1
    private const val MIN_TARGET_INTERVAL_S = 3.0
    private const val MAX_TARGET_INTERVAL_S = 6.0

    /** Advances [state] by [dtSeconds] of wall time, scaled (moves and waits alike) by [multiplier]. */
    fun step(plan: RoutePlan, state: PlayerState, dtSeconds: Double, multiplier: Float, random: Random): RouteStep {
        if (state.phase != RoutePhase.RUNNING || dtSeconds <= 0.0) return snapshot(plan, state, multiplier)

        var s = withVariation(state, plan.legs[state.legIndex].variation, dtSeconds, random)
        var budget = dtSeconds * multiplier
        // Zero-length legs without waits consume no time; the cap stops a degenerate loop from spinning.
        var guard = plan.legs.size * 2 + 2
        while (budget > 0.0 && s.phase == RoutePhase.RUNNING && guard-- > 0) {
            val leg = plan.legs[s.legIndex]
            if (s.waitRemainingS > 0.0) {
                val used = minOf(budget, s.waitRemainingS)
                budget -= used
                s = s.copy(waitRemainingS = s.waitRemainingS - used)
                if (s.waitRemainingS <= 0.0) s = advance(plan, s.copy(waitRemainingS = 0.0))
                continue
            }
            val speed = leg.speedMps * s.variationFactor
            val needed = (leg.distanceM - s.distanceIntoLegM) / speed
            if (needed > budget) {
                s = s.copy(distanceIntoLegM = s.distanceIntoLegM + speed * budget)
                budget = 0.0
            } else {
                budget -= needed
                s = s.copy(distanceIntoLegM = leg.distanceM)
                s = if (leg.dwellSeconds > 0.0) s.copy(waitRemainingS = leg.dwellSeconds) else advance(plan, s)
            }
        }
        return snapshot(plan, s, multiplier)
    }

    /** Position and reported motion for [state] without advancing time. */
    fun snapshot(plan: RoutePlan, state: PlayerState, multiplier: Float): RouteStep {
        val leg = plan.legs[state.legIndex]
        val (lat, lon) = if (state.distanceIntoLegM >= leg.distanceM) {
            leg.endLatitude to leg.endLongitude
        } else {
            GeoMath.destination(leg.startLatitude, leg.startLongitude, leg.bearingDegrees.toDouble(), state.distanceIntoLegM)
        }
        val moving = state.phase == RoutePhase.RUNNING && state.waitRemainingS <= 0.0
        val speed = if (moving) (leg.speedMps * state.variationFactor * multiplier).toFloat() else 0f
        return RouteStep(state, lat, lon, leg.bearingDegrees, speed)
    }

    /** Jumps to [fraction] of the total distance, cancelling any wait. A finished route sought back becomes paused. */
    fun seek(plan: RoutePlan, state: PlayerState, fraction: Float): PlayerState {
        val clamped = fraction.coerceIn(0f, 1f)
        val target = clamped * plan.totalDistanceM
        val index = plan.legs.indices.firstOrNull { target < plan.legStartM(it) + plan.legs[it].distanceM }
            ?: plan.legs.lastIndex
        val into = (target - plan.legStartM(index)).coerceIn(0.0, plan.legs[index].distanceM)
        val phase = if (state.phase == RoutePhase.FINISHED && clamped < 1f) RoutePhase.PAUSED else state.phase
        return state.copy(legIndex = index, distanceIntoLegM = into, waitRemainingS = 0.0, phase = phase)
    }

    fun distanceCovered(plan: RoutePlan, state: PlayerState): Double =
        (plan.legStartM(state.legIndex) + state.distanceIntoLegM).coerceAtMost(plan.totalDistanceM)

    /** Fraction of the current lap's distance covered, 0..1. */
    fun progress(plan: RoutePlan, state: PlayerState): Float {
        if (plan.totalDistanceM <= 0.0) return if (state.phase == RoutePhase.FINISHED) 1f else 0f
        return (distanceCovered(plan, state) / plan.totalDistanceM).toFloat().coerceIn(0f, 1f)
    }

    private fun advance(plan: RoutePlan, s: PlayerState): PlayerState = when {
        s.legIndex < plan.legs.lastIndex -> s.copy(legIndex = s.legIndex + 1, distanceIntoLegM = 0.0)
        plan.loop -> s.copy(legIndex = 0, distanceIntoLegM = 0.0)
        else -> s.copy(phase = RoutePhase.FINISHED)
    }

    private fun withVariation(s: PlayerState, variation: Double, dt: Double, random: Random): PlayerState {
        if (variation <= 0.0) return s.copy(variationFactor = 1.0, variationTarget = 1.0, secondsToNextTarget = 0.0)
        val range = (1.0 - variation)..(1.0 + variation)
        var target = s.variationTarget
        var timer = s.secondsToNextTarget - dt
        if (timer <= 0.0 || target !in range) {
            target = 1.0 + (random.nextDouble() * 2.0 - 1.0) * variation
            timer = MIN_TARGET_INTERVAL_S + random.nextDouble() * (MAX_TARGET_INTERVAL_S - MIN_TARGET_INTERVAL_S)
        }
        val maxDelta = MAX_VARIATION_RATE_PER_S * dt
        val factor = (s.variationFactor + (target - s.variationFactor).coerceIn(-maxDelta, maxDelta))
            .coerceIn(range.start, range.endInclusive)
        return s.copy(variationFactor = factor, variationTarget = target, secondsToNextTarget = timer)
    }
}
```

- [ ] **Step 4: Run the tests and verify they pass.** Same command. Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/noobexon/xposedfakelocation/manager/route/RoutePlayer.kt app/src/test/java/com/noobexon/xposedfakelocation/manager/route/RoutePlayerTest.kt
git commit -m "feat: add route playback engine"
```

---

### Task 6: Route session (tick loop and publishing)

**Files:**
- Create: `manager/route/RouteSession.kt`
- Test: `manager/route/RouteSessionTest.kt`

**Interfaces:**
- Consumes:
  - From Tasks 4 and 5: `RoutePlan`, `RoutePlayer`, `PlayerState`, `RouteStep`, `RoutePhase`.
  - From existing `manager/joystick/JoystickSession.kt` (all usable from the same module): `JoystickFrame`, `JoystickWritePolicy`, `RecentPositions`.
- Produces:
  - `data class RouteStatus(phase, progress: Float, speedMps: Float, pointNumber: Int, waitRemainingS: Int, distanceCoveredM: Double, totalDistanceM: Double, multiplier: Float)`.
  - `class RouteSession(scope, plan, publish: (JoystickFrame) -> Unit, monotonicClock: () -> Long, wallClock, ioDispatcher, random, onPublishError)` with `status: StateFlow<RouteStatus>`, `start()`, `play()`, `pause()`, `replay()`, `seek(fraction)`, `cycleMultiplier()`, `isOwnEcho(lat, lon)`, `stop(): JoystickFrame` and `internal tick(nowMs): JoystickFrame?`.

- [ ] **Step 1: Write the failing tests.** Create `manager/route/RouteSessionTest.kt`:

```kotlin
package com.noobexon.xposedfakelocation.manager.route

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RouteSessionTest {
    private val plan = RoutePlan(
        listOf(Leg(0.0, 0.0, 0.0009, 0.0, 100.0, 0f, 10f, 0.0, 0.0, 2)),
        loop = false,
    )

    private fun session() = RouteSession(
        scope = CoroutineScope(Job()),
        plan = plan,
        publish = {},
        monotonicClock = { 0L },
        wallClock = { 42L },
        random = Random(1),
    )

    @Test
    fun firstTickPublishesActiveFrameAtStart() {
        val frame = session().tick(0)!!
        assertTrue(frame.state.active)
        assertEquals(10f, frame.state.speed, 0f)
        assertEquals(42L, frame.state.updatedAt)
        assertEquals(0.0, frame.latitude, 0.0)
    }

    @Test
    fun movingWritesAreThrottled() {
        val s = session()
        assertNotNull(s.tick(0))
        assertNull(s.tick(100))
        val frame = s.tick(250)!!
        assertTrue(frame.latitude > 0.0)
        assertEquals(2.5, s.status.value.distanceCoveredM, 1e-6)
    }

    @Test
    fun pausePublishesStationaryFrameImmediately() {
        val s = session()
        s.tick(0)
        s.pause()
        val frame = s.tick(100)!!
        assertTrue(frame.state.active)
        assertEquals(0f, frame.state.speed, 0f)
        assertEquals(RoutePhase.PAUSED, s.status.value.phase)
    }

    @Test
    fun finishedRouteStaysActiveAtZeroSpeedWithHeartbeat() {
        val s = session()
        s.tick(0)
        val finished = s.tick(20_000)!!
        assertEquals(0f, finished.state.speed, 0f)
        assertTrue(finished.state.active)
        assertEquals(RoutePhase.FINISHED, s.status.value.phase)
        assertEquals(1f, s.status.value.progress, 0f)
        assertNull(s.tick(21_000))
        assertNotNull(s.tick(22_000))
    }

    @Test
    fun seekForcesTheNextWrite() {
        val s = session()
        s.tick(0)
        assertNull(s.tick(100))
        s.seek(0.5f)
        assertNotNull(s.tick(150))
        assertEquals(50.5, s.status.value.distanceCoveredM, 1e-6)
    }

    @Test
    fun replayAfterFinishStartsOver() {
        val s = session()
        s.tick(0)
        s.tick(20_000)
        s.play()
        assertEquals(RoutePhase.RUNNING, s.status.value.phase)
        assertEquals(0.0, s.status.value.distanceCoveredM, 0.0)
    }

    @Test
    fun multiplierCyclesThroughPresets() {
        val s = session()
        s.cycleMultiplier()
        assertEquals(2f, s.status.value.multiplier, 0f)
        s.cycleMultiplier()
        assertEquals(0.5f, s.status.value.multiplier, 0f)
        s.cycleMultiplier()
        assertEquals(1f, s.status.value.multiplier, 0f)
    }

    @Test
    fun startPointCountsAsOwnEcho() {
        assertTrue(session().isOwnEcho(0.0, 0.0))
        assertFalse(session().isOwnEcho(5.0, 5.0))
    }

    @Test
    fun stopReturnsInactiveFrame() {
        val s = session()
        s.tick(0)
        val last = s.stop()
        assertFalse(last.state.active)
        assertEquals(0f, last.state.speed, 0f)
    }
}
```

- [ ] **Step 2: Run the tests and verify they fail.** Run `.\gradlew.bat :app:testDebugUnitTest --tests "*RouteSessionTest"`. Expected: unresolved `RouteSession`.

- [ ] **Step 3: Implement.** Create `manager/route/RouteSession.kt`:

```kotlin
package com.noobexon.xposedfakelocation.manager.route

import android.util.Log
import com.noobexon.xposedfakelocation.data.model.JoystickState
import com.noobexon.xposedfakelocation.manager.joystick.JoystickFrame
import com.noobexon.xposedfakelocation.manager.joystick.JoystickWritePolicy
import com.noobexon.xposedfakelocation.manager.joystick.RecentPositions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.random.Random

/** Playback status for the overlay, notification and map screen. */
data class RouteStatus(
    val phase: RoutePhase,
    val progress: Float,
    val speedMps: Float,
    /** 1-based number of the point being travelled to (or waited at). */
    val pointNumber: Int,
    val waitRemainingS: Int,
    val distanceCoveredM: Double,
    val totalDistanceM: Double,
    val multiplier: Float,
)

/**
 * Plays a [RoutePlan] and hands frames to [publish], using the joystick's write policy.
 *
 * Not thread-safe: controls, [stop] and the tick loop must all run on the single thread backing
 * [scope] (the service's main-thread scope). Only [publish] runs on [ioDispatcher].
 */
class RouteSession(
    private val scope: CoroutineScope,
    private val plan: RoutePlan,
    private val publish: (JoystickFrame) -> Unit,
    private val monotonicClock: () -> Long,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val random: Random = Random.Default,
    private val onPublishError: (Throwable) -> Unit = { Log.w(TAG, "Failed to publish route frame", it) },
) {
    private var player = PlayerState()
    private var multiplier = 1f
    private var last: RouteStep = RoutePlayer.snapshot(plan, player, multiplier)
    private var lastTickMs: Long? = null
    private var lastWriteMs: Long? = null
    private var wasMoving = false
    private val recent = RecentPositions().apply { add(plan.startLatitude, plan.startLongitude) }
    private val frames = Channel<JoystickFrame>(Channel.CONFLATED)
    private var jobs: List<Job> = emptyList()
    private val lock = Any()
    private var stopped = false
    private val _status = MutableStateFlow(statusOf(last))

    val status: StateFlow<RouteStatus> = _status.asStateFlow()

    fun start() {
        jobs = listOf(
            scope.launch(ioDispatcher) { for (frame in frames) publishSafely(frame) },
            scope.launch {
                while (true) {
                    tick(monotonicClock())?.let { frames.trySend(it) }
                    delay(TICK_INTERVAL_MS)
                }
            },
        )
    }

    /** Resumes a paused route, or replays a finished one. */
    fun play() {
        when (player.phase) {
            RoutePhase.PAUSED -> player = player.copy(phase = RoutePhase.RUNNING)
            RoutePhase.FINISHED -> return replay()
            RoutePhase.RUNNING -> Unit
        }
        refresh()
    }

    fun pause() {
        if (player.phase == RoutePhase.RUNNING) player = player.copy(phase = RoutePhase.PAUSED)
        refresh()
    }

    fun replay() {
        player = PlayerState()
        lastWriteMs = null
        refresh()
    }

    fun seek(fraction: Float) {
        player = RoutePlayer.seek(plan, player, fraction)
        lastWriteMs = null
        refresh()
    }

    fun cycleMultiplier() {
        multiplier = MULTIPLIERS[(MULTIPLIERS.indexOf(multiplier) + 1) % MULTIPLIERS.size]
        refresh()
    }

    /** Whether a marker position is one this session published (so it isn't an external change). */
    fun isOwnEcho(latitude: Double, longitude: Double): Boolean = recent.contains(latitude, longitude)

    /**
     * Cancels the loop and returns the final inactive frame for the caller to publish; once this
     * returns no [publish] is running or can start, so that final write is guaranteed to be last.
     */
    fun stop(): JoystickFrame {
        synchronized(lock) { stopped = true }
        jobs.forEach { it.cancel() }
        frames.close()
        return frame(last, active = false)
    }

    internal fun tick(nowMs: Long): JoystickFrame? {
        val dtSeconds = lastTickMs?.let { (nowMs - it) / 1000.0 } ?: 0.0
        lastTickMs = nowMs
        last = RoutePlayer.step(plan, player, dtSeconds, multiplier, random)
        player = last.state
        _status.value = statusOf(last)

        val moving = last.speedMps > 0f
        val lastWrite = lastWriteMs
        val write = lastWrite == null || JoystickWritePolicy.shouldWrite(nowMs, lastWrite, moving, wasMoving)
        wasMoving = moving
        if (!write) return null

        lastWriteMs = nowMs
        recent.add(last.latitude, last.longitude)
        return frame(last, active = true)
    }

    private fun refresh() {
        last = RoutePlayer.snapshot(plan, player, multiplier)
        _status.value = statusOf(last)
    }

    private fun statusOf(step: RouteStep) = RouteStatus(
        phase = step.state.phase,
        progress = RoutePlayer.progress(plan, step.state),
        speedMps = step.speedMps,
        pointNumber = plan.legs[step.state.legIndex].endPointNumber,
        waitRemainingS = ceil(step.state.waitRemainingS).toInt(),
        distanceCoveredM = RoutePlayer.distanceCovered(plan, step.state),
        totalDistanceM = plan.totalDistanceM,
        multiplier = multiplier,
    )

    private fun frame(step: RouteStep, active: Boolean) = JoystickFrame(
        latitude = step.latitude,
        longitude = step.longitude,
        state = JoystickState(
            active = active,
            bearing = step.bearingDegrees,
            speed = if (active) step.speedMps else 0f,
            updatedAt = wallClock(),
        ),
    )

    /** Publishes [frame] unless stopped; a failing [publish] is reported but doesn't end the session. */
    private fun publishSafely(frame: JoystickFrame) {
        try {
            synchronized(lock) { if (!stopped) publish(frame) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onPublishError(e)
        }
    }

    private companion object {
        const val TAG = "RouteSession"
        const val TICK_INTERVAL_MS = 100L
        val MULTIPLIERS = listOf(0.5f, 1f, 2f)
    }
}
```

- [ ] **Step 4: Run the tests and verify they pass.** Same command. Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/noobexon/xposedfakelocation/manager/route/RouteSession.kt app/src/test/java/com/noobexon/xposedfakelocation/manager/route/RouteSessionTest.kt
git commit -m "feat: add route session with throttled publishing"
```

---

### Task 7: Persist routes, draft, map mode and overlay position

**Files:**
- Modify: `data/Constants.kt`, `data/repository/PrefrencesRepository.kt`

**Interfaces:**
- Consumes: from Task 2, `RouteJson`, `Route`, `RouteDraft`, `MapMode`.
- Produces, all non-suspend on `PreferencesRepository`:
  - Routes: `getRoutesFlow(): Flow<List<Route>>`, `getRoutes()`, `getRoute(id): Route?`, `saveRoute(route)`, `deleteRoute(id)` and `replaceRoutes(routes)`.
  - Draft: `getRouteDraft(): RouteDraft` (never null; empty draft when missing) and `saveRouteDraft(draft)`.
  - Map mode: `getMapMode(): MapMode` and `saveMapMode(mode)`.
  - Overlay position: `getRouteOverlayPosition(): Pair<Int, Int>?` and `saveRouteOverlayPosition(x, y)`.

There are no unit tests in this task: the repository needs a `Context`, and the parsing logic is already covered by `RouteJsonTest`.

- [ ] **Step 1: Add keys to `data/Constants.kt`.** Put them after `KEY_JOYSTICK_OVERLAY_Y`:

```kotlin
const val KEY_ROUTES = "routes"
const val KEY_ROUTE_DRAFT = "route_draft"
const val KEY_MAP_MODE = "map_mode"
const val KEY_ROUTE_OVERLAY_X = "route_overlay_x"
const val KEY_ROUTE_OVERLAY_Y = "route_overlay_y"
```

- [ ] **Step 2: Add the repository region.** In `PrefrencesRepository.kt`, add these imports:

```kotlin
import com.noobexon.xposedfakelocation.data.KEY_MAP_MODE
import com.noobexon.xposedfakelocation.data.KEY_ROUTES
import com.noobexon.xposedfakelocation.data.KEY_ROUTE_DRAFT
import com.noobexon.xposedfakelocation.data.KEY_ROUTE_OVERLAY_X
import com.noobexon.xposedfakelocation.data.KEY_ROUTE_OVERLAY_Y
import com.noobexon.xposedfakelocation.data.model.MapMode
import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.data.model.RouteDraft
import com.noobexon.xposedfakelocation.data.model.RouteJson
import java.util.UUID
```

Then insert this region directly before `// region Map Zoom (local)`:

```kotlin
    // region Routes (local)
    fun getRoutesFlow(): Flow<List<Route>> =
        localFlow(KEY_ROUTES) { RouteJson.parseRoutes(it.getString(KEY_ROUTES, null)) }

    fun getRoutes(): List<Route> = RouteJson.parseRoutes(localPrefs.getString(KEY_ROUTES, null))

    fun getRoute(id: String): Route? = getRoutes().firstOrNull { it.id == id }

    /** Inserts [route], or replaces the saved route with the same id. */
    fun saveRoute(route: Route) {
        val routes = getRoutes().toMutableList()
        val index = routes.indexOfFirst { it.id == route.id }
        if (index >= 0) routes[index] = route else routes.add(route)
        writeRoutes(routes)
    }

    fun deleteRoute(id: String) = writeRoutes(getRoutes().filterNot { it.id == id })

    /** Replaces every saved route (used by settings import). */
    fun replaceRoutes(routes: List<Route>) = writeRoutes(routes)

    private fun writeRoutes(routes: List<Route>) =
        editLocal { putString(KEY_ROUTES, RouteJson.gson.toJson(routes)) }

    /** The route being edited; an empty draft when none is stored or it can't be read. */
    fun getRouteDraft(): RouteDraft =
        RouteJson.parseDraft(localPrefs.getString(KEY_ROUTE_DRAFT, null))
            ?: RouteDraft(Route(id = UUID.randomUUID().toString()))

    fun saveRouteDraft(draft: RouteDraft) =
        editLocal { putString(KEY_ROUTE_DRAFT, RouteJson.gson.toJson(draft)) }

    fun getMapMode(): MapMode {
        val name = localPrefs.getString(KEY_MAP_MODE, null)
        return MapMode.entries.firstOrNull { it.name == name } ?: MapMode.LOCATION
    }

    fun saveMapMode(mode: MapMode) = editLocal { putString(KEY_MAP_MODE, mode.name) }

    fun getRouteOverlayPosition(): Pair<Int, Int>? =
        if (localPrefs.contains(KEY_ROUTE_OVERLAY_X) && localPrefs.contains(KEY_ROUTE_OVERLAY_Y)) {
            localPrefs.getInt(KEY_ROUTE_OVERLAY_X, 0) to localPrefs.getInt(KEY_ROUTE_OVERLAY_Y, 0)
        } else {
            null
        }

    fun saveRouteOverlayPosition(x: Int, y: Int) = editLocal {
        putInt(KEY_ROUTE_OVERLAY_X, x)
        putInt(KEY_ROUTE_OVERLAY_Y, y)
    }
    // endregion
```

- [ ] **Step 3: Compile check.** Run `.\gradlew.bat :app:compileDebugKotlin`. Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/java/com/noobexon/xposedfakelocation/data/Constants.kt app/src/main/java/com/noobexon/xposedfakelocation/data/repository/PrefrencesRepository.kt
git commit -m "feat: persist routes, route draft and map mode"
```

---

### Task 8: Routes in settings backup

**Files:**
- Modify: `data/model/SettingsBackup.kt`, `data/backup/SettingsBackupCodec.kt`, `data/backup/SettingsBackupManager.kt`, `manager/ui/settings/SettingsViewModel.kt`
- Test: `data/backup/SettingsBackupCodecTest.kt`

**Interfaces:**
- Consumes:
  - From Task 2: `RouteJson.gson`, `Route.isValid()`.
  - From Task 7: `PreferencesRepository.getRoutes()` and `replaceRoutes()`.
- Produces: `SettingsBackup.routes: List<Route>? = null`.

- [ ] **Step 1: Write the failing tests.** In `SettingsBackupCodecTest.kt`, add these imports:

```kotlin
import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.data.model.RoutePoint
import com.noobexon.xposedfakelocation.data.model.SpeedChoice
```

Add a `routes` argument as the last argument of `sampleBackup()`'s `SettingsBackup(...)`, after `joystick = ...`:

```kotlin
        routes = listOf(
            Route(
                id = "r1", name = "Loop",
                points = listOf(RoutePoint("a", 1.0, 2.0), RoutePoint("b", 1.1, 2.1)),
                defaultSpeed = SpeedChoice.Custom(4f), loop = true,
            ),
        ),
```

Then add these tests inside the class:

```kotlin
    @Test
    fun invalidRoutesAreDroppedOnImport() {
        val single = sampleBackup().routes!!.first().copy(id = "r2", points = listOf(RoutePoint("a", 1.0, 2.0)))
        val backup = sampleBackup().copy(routes = sampleBackup().routes!! + single)
        val restored = codec.fromJson(codec.toJson(backup)).getOrThrow()
        assertEquals(sampleBackup().routes, restored.routes)
    }

    @Test
    fun backupWithoutRoutesImportsWithNullRoutes() {
        val json = codec.toJson(sampleBackup().copy(routes = null))
        assertNull(codec.fromJson(json).getOrThrow().routes)
    }
```

- [ ] **Step 2: Run the tests and verify they fail.** Run `.\gradlew.bat :app:testDebugUnitTest --tests "*SettingsBackupCodecTest"`. Expected: compile error, no parameter `routes`.

- [ ] **Step 3: Implement.**

In `SettingsBackup.kt`, add `routes` after the `joystick` property of `SettingsBackup`:

```kotlin
    /** Absent in files exported before the route feature; import then leaves saved routes untouched. */
    val routes: List<Route>? = null,
```

In `SettingsBackupCodec.kt`, add `import com.noobexon.xposedfakelocation.data.model.RouteJson` and `import com.noobexon.xposedfakelocation.data.model.isValid`. Change the constructor default to `private val gson: Gson = RouteJson.gson`, remove the now-unused `GsonBuilder` import, and replace the final `parsed.copy(...)` line with:

```kotlin
        @Suppress("SENSELESS_COMPARISON")
        val routes = parsed.routes?.filter { it != null && it.isValid() }
        parsed.copy(favorites = favorites, joystick = parsed.joystick?.takeIf { it.isValid() }, routes = routes)
```

In `SettingsBackupManager.apply`, add this after the joystick line:

```kotlin
        backup.routes?.let { repository.replaceRoutes(it) }
```

Also update the KDoc sentence to: "Favorites, appearance, joystick settings and saved routes (local prefs) are always applied; a missing joystick or routes section leaves those untouched."

In `SettingsViewModel.currentBackup()`, add this after the `joystick = ...` argument:

```kotlin
            routes = preferencesRepository.getRoutes(),
```

- [ ] **Step 4: Run the tests and verify they pass.** Run `.\gradlew.bat :app:testDebugUnitTest --tests "*SettingsBackupCodecTest"`. Expected: PASS, including the existing `roundTripProducesEqualBackup`.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/noobexon/xposedfakelocation/data/model/SettingsBackup.kt app/src/main/java/com/noobexon/xposedfakelocation/data/backup/SettingsBackupCodec.kt app/src/main/java/com/noobexon/xposedfakelocation/data/backup/SettingsBackupManager.kt app/src/main/java/com/noobexon/xposedfakelocation/manager/ui/settings/SettingsViewModel.kt app/src/test/java/com/noobexon/xposedfakelocation/data/backup/SettingsBackupCodecTest.kt
git commit -m "feat: include saved routes in settings backup"
```

---

### Task 9: Extract shared overlay plumbing

**Files:**
- Move: `manager/joystick/JoystickOverlayWindow.kt` to `manager/overlay/OverlayWindowHost.kt`
- Create: `manager/overlay/OverlayDrag.kt`
- Modify: `manager/joystick/ui/JoystickOverlay.kt`, `manager/joystick/JoystickOverlayService.kt`

**Interfaces:**
- Produces:
  - `class OverlayWindowHost(context, lifecycleOwner, savedStateRegistryOwner, initialPosition: Pair<Int, Int>?, onPositionCommitted: (Int, Int) -> Unit, defaultYFraction: Float = 0.55f, content: @Composable () -> Unit)`. Its methods are the same as before: `show()`, `moveBy`, `commitPosition`, `clampToScreen` and `remove`.
  - `@Composable fun rememberOverlayDragModifier(onDrag: (Float, Float) -> Unit, onDragEnd: () -> Unit, onTap: (() -> Unit)? = null): Modifier`.

Joystick behavior must stay the same.

- [ ] **Step 1: Move and rename the window class**

```powershell
New-Item -ItemType Directory -Force app/src/main/java/com/noobexon/xposedfakelocation/manager/overlay | Out-Null
git mv app/src/main/java/com/noobexon/xposedfakelocation/manager/joystick/JoystickOverlayWindow.kt app/src/main/java/com/noobexon/xposedfakelocation/manager/overlay/OverlayWindowHost.kt
```

In `OverlayWindowHost.kt`, make these changes:
- Change the package to `com.noobexon.xposedfakelocation.manager.overlay`.
- Rename the class to `OverlayWindowHost`.
- Add the constructor parameter `private val defaultYFraction: Float = 0.55f` before `content`.
- In `defaultPosition()`, use `(height * defaultYFraction).roundToInt()`.
- Delete `DEFAULT_Y_FRACTION` from the companion object.

- [ ] **Step 2: Extract the drag modifier.** Create `manager/overlay/OverlayDrag.kt`. Cut `DragTracker` and `rememberOverlayDragModifier` (plus its KDoc) out of `JoystickOverlay.kt` and paste them here unchanged, except that `rememberOverlayDragModifier` loses `private`:

```kotlin
package com.noobexon.xposedfakelocation.manager.overlay

import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlin.math.hypot

private class DragTracker {
    var startX = 0f
    var startY = 0f
    var lastX = 0f
    var lastY = 0f
    var dragging = false
}

/**
 * Moves an overlay window using raw screen coordinates. Local pointer coordinates can't be used
 * because the window itself moves under the finger.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun rememberOverlayDragModifier(
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    onTap: (() -> Unit)? = null,
): Modifier {
    // body exactly as previously in JoystickOverlay.kt (from `val touchSlop = ...` to the end)
}
```

The body is the existing code verbatim: `val touchSlop = LocalViewConfiguration.current.touchSlop` through the closing `}` of `remember(touchSlop) { ... }`. Paste it rather than retyping.

- [ ] **Step 3: Fix the joystick references.**
  - **`JoystickOverlay.kt`:** add `import com.noobexon.xposedfakelocation.manager.overlay.rememberOverlayDragModifier`. Remove the imports that are now unused: `android.view.MotionEvent`, `ExperimentalComposeUiApi`, `pointerInteropFilter`, `LocalViewConfiguration`, `kotlin.math.hypot`.
  - **`JoystickOverlayService.kt`:** add `import com.noobexon.xposedfakelocation.manager.overlay.OverlayWindowHost`. Change `private var window: JoystickOverlayWindow?` to `OverlayWindowHost?`, and `window = JoystickOverlayWindow(` to `window = OverlayWindowHost(`.

- [ ] **Step 4: Compile check.** Run `.\gradlew.bat :app:compileDebugKotlin`. Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```powershell
git add -A app/src/main/java/com/noobexon/xposedfakelocation/manager/overlay app/src/main/java/com/noobexon/xposedfakelocation/manager/joystick
git commit -m "refactor: share overlay window and drag handling"
```

---

### Task 10: Route strings (all locales)

**Files:**
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-de/strings.xml`, `app/src/main/res/values-zh/strings.xml`

**Interfaces:**
- Produces: the string resources used in Tasks 11–18, listed below.

- [ ] **Step 1: English.** Append before `</resources>` in `values/strings.xml`:

```xml
    <!-- Custom route -->
    <string name="map_mode_location">Location</string>
    <string name="map_mode_route">Route</string>
    <string name="route_unnamed">Unsaved route</string>
    <string name="route_empty_hint">Tap the map to add points</string>
    <string name="route_needs_two_points">Add at least 2 points</string>
    <string name="route_joystick_running">Close the joystick first</string>
    <string name="route_service_lost">Module disconnected; route stopped</string>
    <string name="route_summary">%1$d points · %2$s · %3$s</string>
    <string name="route_distance_km">%1$s km</string>
    <string name="route_distance_progress">%1$s / %2$s km</string>
    <string name="route_duration_min">~%1$d min</string>
    <string name="route_duration_h_min">~%1$d h %2$d min</string>
    <string name="route_default_speed">Speed: %1$s</string>
    <string name="route_default_speed_title">Route speed</string>
    <string name="route_loop">Loop</string>
    <string name="route_reverse">Reverse</string>
    <string name="route_point_label">Point %1$d</string>
    <string name="route_save_to_favorites">Save to favorites</string>
    <string name="route_load_from_favorites">Load from favorites</string>
    <string name="route_clear">Clear route</string>
    <string name="route_clear_message">Remove all points from the current route?</string>
    <string name="route_delete_point">Delete</string>
    <string name="route_move_earlier">Move earlier</string>
    <string name="route_move_later">Move later</string>
    <string name="route_set_number">Set number…</string>
    <string name="route_set_number_title">Move point to position</string>
    <string name="route_set_number_label">Position (1–%1$d)</string>
    <string name="route_segment_title">Segment %1$d → %2$d</string>
    <string name="route_speed_default">Default</string>
    <string name="route_speed_custom">Custom</string>
    <string name="route_speed_custom_label">Speed (km/h)</string>
    <string name="route_wait_label">Wait at end point (s)</string>
    <string name="route_variation_label">Speed variation: ±%1$d%%</string>
    <string name="route_save_title">Save route</string>
    <string name="route_save_update">Update</string>
    <string name="route_save_as_new">Save as new</string>
    <string name="route_replace_title">Replace current route?</string>
    <string name="route_replace_message">The current route has unsaved changes.</string>
    <string name="route_replace_confirm">Replace</string>
    <string name="route_notification_channel">Route playback</string>
    <string name="route_notification_title">Route playing</string>
    <string name="route_notification_starting">Starting route…</string>
    <string name="route_action_pause">Pause</string>
    <string name="route_action_resume">Resume</string>
    <string name="route_action_stop">Stop</string>
    <string name="route_status_point">Point %1$d/%2$d</string>
    <string name="route_status_waiting">Waiting %1$s</string>
    <string name="route_status_paused">Paused</string>
    <string name="route_status_finished">Finished</string>
    <string name="route_multiplier_label">%1$sx</string>
    <string name="route_overlay_permission_message">Shows play, pause and a progress bar over other apps while the route plays. Without it, use the notification controls.</string>
    <string name="route_continue_without_overlay">Continue without</string>
    <string name="cd_route_play">Play route</string>
    <string name="cd_route_pause">Pause route</string>
    <string name="cd_route_replay">Replay route</string>
    <string name="cd_route_stop">Stop route</string>
    <string name="cd_route_drag">Move route controls</string>
    <string name="cd_route_collapse">Minimize route controls</string>
    <string name="cd_route_expand">Expand route controls</string>
    <string name="cd_route_multiplier">Playback speed %1$s</string>
    <string name="cd_route_drag_handle">Reorder point %1$d</string>
    <string name="cd_route_delete_point">Delete point %1$d</string>
    <string name="cd_route_show_points">Show points</string>
    <string name="cd_route_hide_points">Hide points</string>
    <string name="cd_route_options">Route options</string>
    <string name="favorites_tab_locations">Locations</string>
    <string name="favorites_tab_routes">Routes</string>
    <string name="favorites_routes_empty">No saved routes</string>
    <string name="favorites_routes_empty_description">Build a route in Route mode on the map and save it here.</string>
    <string name="favorites_route_points">%1$d points</string>
    <string name="favorites_route_rename_title">Rename route</string>
```

- [ ] **Step 2: German.** Append before `</resources>` in `values-de/strings.xml`:

```xml
    <!-- Custom route -->
    <string name="map_mode_location">Standort</string>
    <string name="map_mode_route">Route</string>
    <string name="route_unnamed">Ungespeicherte Route</string>
    <string name="route_empty_hint">Tippe auf die Karte, um Punkte hinzuzufügen</string>
    <string name="route_needs_two_points">Mindestens 2 Punkte hinzufügen</string>
    <string name="route_joystick_running">Schließe zuerst den Joystick</string>
    <string name="route_service_lost">Modul getrennt; Route gestoppt</string>
    <string name="route_summary">%1$d Punkte · %2$s · %3$s</string>
    <string name="route_distance_km">%1$s km</string>
    <string name="route_distance_progress">%1$s / %2$s km</string>
    <string name="route_duration_min">~%1$d Min.</string>
    <string name="route_duration_h_min">~%1$d Std. %2$d Min.</string>
    <string name="route_default_speed">Tempo: %1$s</string>
    <string name="route_default_speed_title">Routengeschwindigkeit</string>
    <string name="route_loop">Schleife</string>
    <string name="route_reverse">Umkehren</string>
    <string name="route_point_label">Punkt %1$d</string>
    <string name="route_save_to_favorites">Zu Favoriten hinzufügen</string>
    <string name="route_load_from_favorites">Aus Favoriten laden</string>
    <string name="route_clear">Route löschen</string>
    <string name="route_clear_message">Alle Punkte der aktuellen Route entfernen?</string>
    <string name="route_delete_point">Löschen</string>
    <string name="route_move_earlier">Nach vorne</string>
    <string name="route_move_later">Nach hinten</string>
    <string name="route_set_number">Nummer festlegen…</string>
    <string name="route_set_number_title">Punkt an Position verschieben</string>
    <string name="route_set_number_label">Position (1–%1$d)</string>
    <string name="route_segment_title">Abschnitt %1$d → %2$d</string>
    <string name="route_speed_default">Standard</string>
    <string name="route_speed_custom">Benutzerdefiniert</string>
    <string name="route_speed_custom_label">Geschwindigkeit (km/h)</string>
    <string name="route_wait_label">Wartezeit am Endpunkt (s)</string>
    <string name="route_variation_label">Tempovariation: ±%1$d %%</string>
    <string name="route_save_title">Route speichern</string>
    <string name="route_save_update">Aktualisieren</string>
    <string name="route_save_as_new">Als neu speichern</string>
    <string name="route_replace_title">Aktuelle Route ersetzen?</string>
    <string name="route_replace_message">Die aktuelle Route hat ungespeicherte Änderungen.</string>
    <string name="route_replace_confirm">Ersetzen</string>
    <string name="route_notification_channel">Routenwiedergabe</string>
    <string name="route_notification_title">Route läuft</string>
    <string name="route_notification_starting">Route wird gestartet…</string>
    <string name="route_action_pause">Pause</string>
    <string name="route_action_resume">Fortsetzen</string>
    <string name="route_action_stop">Stopp</string>
    <string name="route_status_point">Punkt %1$d/%2$d</string>
    <string name="route_status_waiting">Warten %1$s</string>
    <string name="route_status_paused">Pausiert</string>
    <string name="route_status_finished">Beendet</string>
    <string name="route_multiplier_label">%1$sx</string>
    <string name="route_overlay_permission_message">Zeigt beim Abspielen der Route Wiedergabe, Pause und einen Fortschrittsbalken über anderen Apps an. Ohne diese Berechtigung nutzt du die Steuerung in der Benachrichtigung.</string>
    <string name="route_continue_without_overlay">Ohne fortfahren</string>
    <string name="cd_route_play">Route abspielen</string>
    <string name="cd_route_pause">Route pausieren</string>
    <string name="cd_route_replay">Route erneut abspielen</string>
    <string name="cd_route_stop">Route stoppen</string>
    <string name="cd_route_drag">Routensteuerung verschieben</string>
    <string name="cd_route_collapse">Routensteuerung minimieren</string>
    <string name="cd_route_expand">Routensteuerung erweitern</string>
    <string name="cd_route_multiplier">Wiedergabegeschwindigkeit %1$s</string>
    <string name="cd_route_drag_handle">Punkt %1$d verschieben</string>
    <string name="cd_route_delete_point">Punkt %1$d löschen</string>
    <string name="cd_route_show_points">Punkte anzeigen</string>
    <string name="cd_route_hide_points">Punkte ausblenden</string>
    <string name="cd_route_options">Routenoptionen</string>
    <string name="favorites_tab_locations">Orte</string>
    <string name="favorites_tab_routes">Routen</string>
    <string name="favorites_routes_empty">Keine gespeicherten Routen</string>
    <string name="favorites_routes_empty_description">Erstelle im Routenmodus auf der Karte eine Route und speichere sie hier.</string>
    <string name="favorites_route_points">%1$d Punkte</string>
    <string name="favorites_route_rename_title">Route umbenennen</string>
```

- [ ] **Step 3: Chinese.** Append before `</resources>` in `values-zh/strings.xml`:

```xml
    <!-- Custom route -->
    <string name="map_mode_location">位置</string>
    <string name="map_mode_route">路线</string>
    <string name="route_unnamed">未保存的路线</string>
    <string name="route_empty_hint">点击地图添加点</string>
    <string name="route_needs_two_points">请至少添加 2 个点</string>
    <string name="route_joystick_running">请先关闭摇杆</string>
    <string name="route_service_lost">模块已断开，路线已停止</string>
    <string name="route_summary">%1$d 个点 · %2$s · %3$s</string>
    <string name="route_distance_km">%1$s 公里</string>
    <string name="route_distance_progress">%1$s / %2$s 公里</string>
    <string name="route_duration_min">约 %1$d 分钟</string>
    <string name="route_duration_h_min">约 %1$d 小时 %2$d 分钟</string>
    <string name="route_default_speed">速度：%1$s</string>
    <string name="route_default_speed_title">路线速度</string>
    <string name="route_loop">循环</string>
    <string name="route_reverse">反向</string>
    <string name="route_point_label">点 %1$d</string>
    <string name="route_save_to_favorites">保存到收藏</string>
    <string name="route_load_from_favorites">从收藏加载</string>
    <string name="route_clear">清除路线</string>
    <string name="route_clear_message">删除当前路线中的所有点？</string>
    <string name="route_delete_point">删除</string>
    <string name="route_move_earlier">前移</string>
    <string name="route_move_later">后移</string>
    <string name="route_set_number">设置序号…</string>
    <string name="route_set_number_title">将点移动到位置</string>
    <string name="route_set_number_label">位置（1–%1$d）</string>
    <string name="route_segment_title">路段 %1$d → %2$d</string>
    <string name="route_speed_default">默认</string>
    <string name="route_speed_custom">自定义</string>
    <string name="route_speed_custom_label">速度（公里/小时）</string>
    <string name="route_wait_label">终点等待时间（秒）</string>
    <string name="route_variation_label">速度波动：±%1$d%%</string>
    <string name="route_save_title">保存路线</string>
    <string name="route_save_update">更新</string>
    <string name="route_save_as_new">另存为新路线</string>
    <string name="route_replace_title">替换当前路线？</string>
    <string name="route_replace_message">当前路线有未保存的更改。</string>
    <string name="route_replace_confirm">替换</string>
    <string name="route_notification_channel">路线播放</string>
    <string name="route_notification_title">路线播放中</string>
    <string name="route_notification_starting">正在启动路线…</string>
    <string name="route_action_pause">暂停</string>
    <string name="route_action_resume">继续</string>
    <string name="route_action_stop">停止</string>
    <string name="route_status_point">点 %1$d/%2$d</string>
    <string name="route_status_waiting">等待 %1$s</string>
    <string name="route_status_paused">已暂停</string>
    <string name="route_status_finished">已完成</string>
    <string name="route_multiplier_label">%1$sx</string>
    <string name="route_overlay_permission_message">路线播放时在其他应用上方显示播放、暂停和进度条。不授予此权限时，可使用通知中的控制按钮。</string>
    <string name="route_continue_without_overlay">不使用悬浮窗继续</string>
    <string name="cd_route_play">播放路线</string>
    <string name="cd_route_pause">暂停路线</string>
    <string name="cd_route_replay">重新播放路线</string>
    <string name="cd_route_stop">停止路线</string>
    <string name="cd_route_drag">移动路线控制</string>
    <string name="cd_route_collapse">最小化路线控制</string>
    <string name="cd_route_expand">展开路线控制</string>
    <string name="cd_route_multiplier">播放速度 %1$s</string>
    <string name="cd_route_drag_handle">重新排序点 %1$d</string>
    <string name="cd_route_delete_point">删除点 %1$d</string>
    <string name="cd_route_show_points">显示点</string>
    <string name="cd_route_hide_points">隐藏点</string>
    <string name="cd_route_options">路线选项</string>
    <string name="favorites_tab_locations">位置</string>
    <string name="favorites_tab_routes">路线</string>
    <string name="favorites_routes_empty">没有已保存的路线</string>
    <string name="favorites_routes_empty_description">在地图的路线模式中创建路线并保存到这里。</string>
    <string name="favorites_route_points">%1$d 个点</string>
    <string name="favorites_route_rename_title">重命名路线</string>
```

- [ ] **Step 4: Resource check.** Run `.\gradlew.bat :app:processDebugResources`. Expected: BUILD SUCCESSFUL. If this fails with an `R.jar` lock, stop and report it as the Global Constraints say.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/res/values/strings.xml app/src/main/res/values-de/strings.xml app/src/main/res/values-zh/strings.xml
git commit -m "feat: add custom route strings"
```

---

### Task 11: Process-wide running state and joystick exclusivity

**Files:**
- Create: `manager/route/RoutePlaybackState.kt`, `manager/joystick/JoystickRunningState.kt`
- Modify: `manager/joystick/JoystickOverlayService.kt`

**Interfaces:**
- Consumes: from Task 6, `RouteStatus`.
- Produces:
  - `data class RoutePlaybackStatus(routeId: String, routeName: String, pointCount: Int, status: RouteStatus)`.
  - `object RoutePlaybackState { val current: StateFlow<RoutePlaybackStatus?>; internal fun publish(value: RoutePlaybackStatus?) }`.
  - `object JoystickRunningState { val running: StateFlow<Boolean>; internal fun set(running: Boolean) }`.

- [ ] **Step 1: Create `manager/route/RoutePlaybackState.kt`**

```kotlin
package com.noobexon.xposedfakelocation.manager.route

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A playing route as the app UI sees it. */
data class RoutePlaybackStatus(
    val routeId: String,
    val routeName: String,
    val pointCount: Int,
    val status: RouteStatus,
)

/** Process-wide playback status published by [RoutePlaybackService]; `null` while no route plays. */
object RoutePlaybackState {
    private val _current = MutableStateFlow<RoutePlaybackStatus?>(null)
    val current: StateFlow<RoutePlaybackStatus?> = _current.asStateFlow()

    internal fun publish(value: RoutePlaybackStatus?) {
        _current.value = value
    }
}
```

- [ ] **Step 2: Create `manager/joystick/JoystickRunningState.kt`**

```kotlin
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
```

- [ ] **Step 3: Wire it into `JoystickOverlayService`.**
  1. Add `import com.noobexon.xposedfakelocation.manager.route.RoutePlaybackState`.
  2. In `beginSession()`, extend the precondition check to:

```kotlin
        if (App.service == null || start == null || !repository.getIsPlaying() ||
            !Settings.canDrawOverlays(this) || RoutePlaybackState.current.value != null
        ) {
```

  3. After `newSession.start()`, add `JoystickRunningState.set(true)`.
  4. In `releaseSession`, add `JoystickRunningState.set(false)` right after `session = null`.

- [ ] **Step 4: Compile check.** Run `.\gradlew.bat :app:compileDebugKotlin`. Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/noobexon/xposedfakelocation/manager/route/RoutePlaybackState.kt app/src/main/java/com/noobexon/xposedfakelocation/manager/joystick/JoystickRunningState.kt app/src/main/java/com/noobexon/xposedfakelocation/manager/joystick/JoystickOverlayService.kt
git commit -m "feat: make joystick and route playback mutually exclusive"
```

---

### Task 12: Formatting helpers and the playback overlay

**Files:**
- Create: `manager/route/RouteFormat.kt`, `manager/route/ui/RouteOverlay.kt`
- Test: `manager/route/RouteFormatTest.kt`

**Interfaces:**
- Consumes:
  - From Task 6: `RouteStatus`, `RoutePhase`.
  - From Task 9: `rememberOverlayDragModifier`.
  - From existing code: `displayKmh`.
- Produces:
  - `object RouteFormat` with `clock(seconds: Int): String` ("0:24"), `km(meters, locale = default): String` ("1.1"), `minutes(seconds: Double): Int` (rounds up, at least 1) and `multiplier(value: Float): String` ("0.5", "1", "2").
  - `@Composable fun RouteOverlay(routeName, pointCount, collapsed, status: () -> RouteStatus, onPlayPause, onStop, onSeek: (Float) -> Unit, onCycleMultiplier, onCollapsedChange: (Boolean) -> Unit, onDrag: (Float, Float) -> Unit, onDragEnd)`.

- [ ] **Step 1: Write the failing tests.** Create `manager/route/RouteFormatTest.kt`:

```kotlin
package com.noobexon.xposedfakelocation.manager.route

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class RouteFormatTest {
    @Test
    fun clockPadsSeconds() {
        assertEquals("0:24", RouteFormat.clock(24))
        assertEquals("2:05", RouteFormat.clock(125))
    }

    @Test
    fun kmHasOneDecimal() {
        assertEquals("1.1", RouteFormat.km(1_100.0, Locale.US))
        assertEquals("0.0", RouteFormat.km(0.0, Locale.US))
        assertEquals("2,4", RouteFormat.km(2_400.0, Locale.GERMANY))
    }

    @Test
    fun minutesRoundUpWithAMinimumOfOne() {
        assertEquals(1, RouteFormat.minutes(0.0))
        assertEquals(1, RouteFormat.minutes(59.0))
        assertEquals(2, RouteFormat.minutes(61.0))
    }

    @Test
    fun multiplierDropsTrailingZero() {
        assertEquals("0.5", RouteFormat.multiplier(0.5f))
        assertEquals("1", RouteFormat.multiplier(1f))
        assertEquals("2", RouteFormat.multiplier(2f))
    }
}
```

- [ ] **Step 2: Run the tests and verify they fail.** Run `.\gradlew.bat :app:testDebugUnitTest --tests "*RouteFormatTest"`. Expected: unresolved `RouteFormat`.

- [ ] **Step 3: Implement `manager/route/RouteFormat.kt`**

```kotlin
package com.noobexon.xposedfakelocation.manager.route

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

/** Number formatting for route UI; units and wording come from string resources. */
object RouteFormat {
    fun clock(seconds: Int): String = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)

    fun km(meters: Double, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%.1f", meters / 1000.0)

    fun minutes(seconds: Double): Int = max(1, ceil(seconds / 60.0).toInt())

    fun multiplier(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString() else value.toString()
}
```

- [ ] **Step 4: Run the tests and verify they pass.** Same command. Expected: PASS.

- [ ] **Step 5: Create `manager/route/ui/RouteOverlay.kt`**

```kotlin
package com.noobexon.xposedfakelocation.manager.route.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.manager.joystick.displayKmh
import com.noobexon.xposedfakelocation.manager.overlay.rememberOverlayDragModifier
import com.noobexon.xposedfakelocation.manager.route.RouteFormat
import com.noobexon.xposedfakelocation.manager.route.RoutePhase
import com.noobexon.xposedfakelocation.manager.route.RouteStatus

/**
 * Music-player style controller for a playing route: an expanded card or a progress bubble.
 *
 * @param status Latest playback status; a lambda so only the parts that read it recompose per tick.
 */
@Composable
fun RouteOverlay(
    routeName: String,
    pointCount: Int,
    collapsed: Boolean,
    status: () -> RouteStatus,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Float) -> Unit,
    onCycleMultiplier: () -> Unit,
    onCollapsedChange: (Boolean) -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    if (collapsed) {
        RouteBubble(
            progress = { status().progress },
            onExpand = { onCollapsedChange(false) },
            onDrag = onDrag,
            onDragEnd = onDragEnd,
        )
        return
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.width(288.dp).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.Default.DragIndicator,
                    contentDescription = stringResource(R.string.cd_route_drag),
                    modifier = Modifier.size(32.dp).then(rememberOverlayDragModifier(onDrag, onDragEnd)),
                )
                Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(
                        text = routeName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    StatusLine(status = status, pointCount = pointCount)
                }
                IconButton(onClick = { onCollapsedChange(true) }) {
                    Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.cd_route_collapse))
                }
            }
            ProgressSeekBar(status = status, onSeek = onSeek)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                PlayPauseButton(phase = { status().phase }, onClick = onPlayPause)
                IconButton(onClick = onStop) {
                    Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.cd_route_stop))
                }
                MultiplierButton(multiplier = { status().multiplier }, onClick = onCycleMultiplier)
                Spacer(Modifier.weight(1f))
                SpeedReadout(speedMps = { status().speedMps })
            }
        }
    }
}

@Composable
private fun StatusLine(status: () -> RouteStatus, pointCount: Int) {
    val s = status()
    val text = when {
        s.phase == RoutePhase.FINISHED -> stringResource(R.string.route_status_finished)
        s.waitRemainingS > 0 -> stringResource(R.string.route_status_waiting, RouteFormat.clock(s.waitRemainingS))
        s.phase == RoutePhase.PAUSED -> stringResource(R.string.route_status_paused)
        else -> stringResource(R.string.route_status_point, s.pointNumber, pointCount)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

/** Shows the drag position while seeking and applies [onSeek] only on release. */
@Composable
private fun ProgressSeekBar(status: () -> RouteStatus, onSeek: (Float) -> Unit) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val s = status()
    Slider(
        value = dragFraction ?: s.progress,
        onValueChange = { dragFraction = it },
        onValueChangeFinished = {
            dragFraction?.let(onSeek)
            dragFraction = null
        },
        modifier = Modifier.fillMaxWidth(),
    )
    val shownMeters = dragFraction?.let { it * s.totalDistanceM } ?: s.distanceCoveredM
    Text(
        text = stringResource(R.string.route_distance_progress, RouteFormat.km(shownMeters), RouteFormat.km(s.totalDistanceM)),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PlayPauseButton(phase: () -> RoutePhase, onClick: () -> Unit) {
    val (icon, description) = when (phase()) {
        RoutePhase.RUNNING -> Icons.Default.Pause to R.string.cd_route_pause
        RoutePhase.PAUSED -> Icons.Default.PlayArrow to R.string.cd_route_play
        RoutePhase.FINISHED -> Icons.Default.Replay to R.string.cd_route_replay
    }
    FilledIconButton(onClick = onClick) {
        Icon(icon, contentDescription = stringResource(description))
    }
}

@Composable
private fun MultiplierButton(multiplier: () -> Float, onClick: () -> Unit) {
    val label = RouteFormat.multiplier(multiplier())
    val description = stringResource(R.string.cd_route_multiplier, label)
    TextButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = description }) {
        Text(stringResource(R.string.route_multiplier_label, label), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SpeedReadout(speedMps: () -> Float) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = displayKmh(speedMps()).toString(),
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.unit_kilometers_per_hour),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 3.dp),
        )
    }
}

@Composable
private fun RouteBubble(progress: () -> Float, onExpand: () -> Unit, onDrag: (Float, Float) -> Unit, onDragEnd: () -> Unit) {
    val expandDescription = stringResource(R.string.cd_route_expand)
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
            CircularProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxSize().padding(4.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
            )
            Icon(Icons.Default.Route, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}
```

- [ ] **Step 6: Compile check.** Run `.\gradlew.bat :app:compileDebugKotlin`. Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/java/com/noobexon/xposedfakelocation/manager/route/RouteFormat.kt app/src/main/java/com/noobexon/xposedfakelocation/manager/route/ui/RouteOverlay.kt app/src/test/java/com/noobexon/xposedfakelocation/manager/route/RouteFormatTest.kt
git commit -m "feat: add route playback overlay"
```

---

### Task 13: Route playback service

**Files:**
- Create: `manager/route/RoutePlaybackService.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes:
  - From earlier tasks: `RoutePlan` and `RouteSession` (4, 6), `RouteOverlay` (12), `OverlayWindowHost` (9), `RoutePlaybackState` and `JoystickRunningState` (11).
  - From Task 7: the repository's route functions.
- Produces: `RoutePlaybackService.start(context, routeId: String? = null)` (`null` plays the draft) and `RoutePlaybackService.stop(context)`.

- [ ] **Step 1: Create `manager/route/RoutePlaybackService.kt`**

```kotlin
package com.noobexon.xposedfakelocation.manager.route

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
import android.widget.Toast
import androidx.annotation.StringRes
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
import com.noobexon.xposedfakelocation.data.ROUTE_MIN_POINTS
import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.data.repository.PreferencesRepository
import com.noobexon.xposedfakelocation.manager.App
import com.noobexon.xposedfakelocation.manager.MainActivity
import com.noobexon.xposedfakelocation.manager.joystick.JoystickRunningState
import com.noobexon.xposedfakelocation.manager.localization.LocaleController
import com.noobexon.xposedfakelocation.manager.overlay.OverlayWindowHost
import com.noobexon.xposedfakelocation.manager.route.ui.RouteOverlay
import com.noobexon.xposedfakelocation.manager.ui.theme.ThemeOption
import com.noobexon.xposedfakelocation.manager.ui.theme.XposedFakeLocationTheme
import com.noobexon.xposedfakelocation.manager.ui.theme.isDark
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Foreground service playing one route: owns the [RouteSession], its notification and the
 * optional overlay. Ends on Stop, when spoofing is turned off, or when the marker is cleared or
 * changed by anything other than this session.
 */
class RoutePlaybackService : LifecycleService(), SavedStateRegistryOwner {

    companion object {
        private const val TAG = "RoutePlaybackService"
        private const val ACTION_START = "com.noobexon.xposedfakelocation.route.START"
        private const val ACTION_PAUSE = "com.noobexon.xposedfakelocation.route.PAUSE"
        private const val ACTION_RESUME = "com.noobexon.xposedfakelocation.route.RESUME"
        private const val ACTION_STOP = "com.noobexon.xposedfakelocation.route.STOP"
        private const val EXTRA_ROUTE_ID = "route_id"
        private const val CHANNEL_ID = "route_playback"
        private const val NOTIFICATION_ID = 4202
        private const val OVERLAY_DEFAULT_Y_FRACTION = 0.15f

        /** Plays the saved route [routeId], or the map's draft when `null`. */
        fun start(context: Context, routeId: String? = null) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RoutePlaybackService::class.java).setAction(ACTION_START).putExtra(EXTRA_ROUTE_ID, routeId),
            )
        }

        fun stop(context: Context) {
            if (RoutePlaybackState.current.value == null) return
            context.startService(Intent(context, RoutePlaybackService::class.java).setAction(ACTION_STOP))
        }
    }

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private lateinit var repository: PreferencesRepository
    private var session: RouteSession? = null
    private var window: OverlayWindowHost? = null
    private val collapsed = MutableStateFlow(false)
    private var lastNotified: Pair<RoutePhase, Int>? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleController.attachBaseContext(newBase))
    }

    override fun onCreate() {
        savedStateRegistryController.performRestore(null)
        super.onCreate()
        repository = PreferencesRepository(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startInForeground()
        val active = session
        when (intent?.action) {
            ACTION_STOP -> endSession(userStop = true)
            ACTION_PAUSE -> active?.pause() ?: stopSelfCompletely()
            ACTION_RESUME -> active?.play() ?: stopSelfCompletely()
            else -> if (active == null) beginSession(intent?.getStringExtra(EXTRA_ROUTE_ID)) else collapsed.value = false
        }
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        window?.clampToScreen()
    }

    override fun onDestroy() {
        releaseSession()
        super.onDestroy()
    }

    private fun beginSession(routeId: String?) {
        val route = routeId?.let(repository::getRoute) ?: repository.getRouteDraft().route
        val problem = when {
            App.service == null -> R.string.system_hooks_module_inactive
            route.points.size < ROUTE_MIN_POINTS -> R.string.route_needs_two_points
            JoystickRunningState.running.value -> R.string.route_joystick_running
            else -> null
        }
        if (problem != null) {
            toast(problem)
            stopSelfCompletely()
            return
        }

        val plan = RoutePlan.from(route, repository.getJoystickSettings())
        val newSession = RouteSession(
            scope = lifecycleScope,
            plan = plan,
            publish = { frame -> repository.saveJoystickFrame(frame.latitude, frame.longitude, frame.state) },
            monotonicClock = SystemClock::elapsedRealtime,
        )
        session = newSession

        lifecycleScope.launch {
            repository.saveLastClickedLocation(plan.startLatitude, plan.startLongitude)
            repository.saveIsPlaying(true)
            if (session !== newSession) return@launch
            newSession.start()
            observeStatus(newSession, route)
            if (Settings.canDrawOverlays(this@RoutePlaybackService)) showOverlay(newSession, route)
            observeStopConditions(newSession)
        }
    }

    private fun displayName(route: Route): String = route.name.ifBlank { getString(R.string.route_unnamed) }

    private fun observeStatus(active: RouteSession, route: Route) {
        lifecycleScope.launch {
            active.status.collect { status ->
                RoutePlaybackState.publish(RoutePlaybackStatus(route.id, displayName(route), route.points.size, status))
                val key = status.phase to (status.progress * 100).toInt()
                if (key != lastNotified) {
                    lastNotified = key
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
                }
            }
        }
    }

    private fun showOverlay(active: RouteSession, route: Route) {
        val themeOptionFlow = repository.getThemeOptionFlow().map { ThemeOption.fromTag(it) }
        val name = displayName(route)
        window = OverlayWindowHost(
            context = this,
            lifecycleOwner = this,
            savedStateRegistryOwner = this,
            initialPosition = repository.getRouteOverlayPosition(),
            onPositionCommitted = repository::saveRouteOverlayPosition,
            defaultYFraction = OVERLAY_DEFAULT_Y_FRACTION,
        ) {
            val themeOption by themeOptionFlow.collectAsState(initial = ThemeOption.SYSTEM)
            XposedFakeLocationTheme(darkTheme = themeOption.isDark()) {
                val isCollapsed by collapsed.collectAsState()
                val status by active.status.collectAsState()
                RouteOverlay(
                    routeName = name,
                    pointCount = route.points.size,
                    collapsed = isCollapsed,
                    status = { status },
                    onPlayPause = { if (active.status.value.phase == RoutePhase.RUNNING) active.pause() else active.play() },
                    onStop = { endSession(userStop = true) },
                    onSeek = active::seek,
                    onCycleMultiplier = active::cycleMultiplier,
                    onCollapsedChange = { collapsed.value = it },
                    onDrag = { dx, dy -> window?.moveBy(dx, dy) },
                    onDragEnd = { window?.commitPosition() },
                )
            }
        }
        // The overlay permission can be revoked between the check and addView; playback goes on without it.
        runCatching { window?.show() }.onFailure { error ->
            Log.w(TAG, "Could not show the route overlay; continuing with notification controls", error)
            window?.remove()
            window = null
        }
    }

    private fun observeStopConditions(active: RouteSession) {
        // Emits false as soon as the Xposed service disconnects, too.
        lifecycleScope.launch {
            repository.getIsPlayingFlow().collect { isPlaying ->
                if (!isPlaying) {
                    if (App.service == null && session != null) toast(R.string.route_service_lost)
                    endSession(userStop = false)
                }
            }
        }
        lifecycleScope.launch {
            repository.getLastClickedLocationFlow().collect { location ->
                if (location == null || !active.isOwnEcho(location.latitude, location.longitude)) {
                    endSession(userStop = false)
                }
            }
        }
    }

    /** @param userStop the user pressed Stop, so spoofing is turned off as well. */
    private fun endSession(userStop: Boolean) {
        val wasActive = session != null
        releaseSession()
        if (userStop && wasActive) {
            lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) { repository.saveIsPlaying(false) }
        }
        stopSelfCompletely()
    }

    private fun releaseSession() {
        session?.let { repository.saveJoystickState(it.stop().state) }
        session = null
        window?.remove()
        window = null
        RoutePlaybackState.publish(null)
    }

    private fun stopSelfCompletely() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun toast(@StringRes message: Int) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this, requestCode,
        Intent(this, RoutePlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun buildNotification(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.route_notification_channel), NotificationManager.IMPORTANCE_LOW)
        )
        val playback = RoutePlaybackState.current.value
        val status = playback?.status
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle(playback?.routeName ?: getString(R.string.route_notification_title))
            .setContentText(status?.let { statusText(it, playback.pointCount) } ?: getString(R.string.route_notification_starting))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, ((status?.progress ?: 0f) * 100).toInt(), false)
        if (status != null && status.phase != RoutePhase.FINISHED) {
            val paused = status.phase == RoutePhase.PAUSED
            builder.addAction(
                0,
                getString(if (paused) R.string.route_action_resume else R.string.route_action_pause),
                servicePendingIntent(if (paused) ACTION_RESUME else ACTION_PAUSE, requestCode = 1),
            )
        }
        builder.addAction(0, getString(R.string.route_action_stop), servicePendingIntent(ACTION_STOP, requestCode = 2))
        return builder.build()
    }

    private fun statusText(status: RouteStatus, pointCount: Int): String = when {
        status.phase == RoutePhase.FINISHED -> getString(R.string.route_status_finished)
        status.waitRemainingS > 0 -> getString(R.string.route_status_waiting, RouteFormat.clock(status.waitRemainingS))
        status.phase == RoutePhase.PAUSED -> getString(R.string.route_status_paused)
        else -> getString(R.string.route_status_point, status.pointNumber, pointCount)
    }
}
```

- [ ] **Step 2: Declare the service.** In `AndroidManifest.xml`, after the joystick `<service>` element:

```xml
        <service
            android:name=".manager.route.RoutePlaybackService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="Plays a user-built route by moving the spoofed location along it" />
        </service>
```

- [ ] **Step 3: Compile check.** Run `.\gradlew.bat :app:compileDebugKotlin`. Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/java/com/noobexon/xposedfakelocation/manager/route/RoutePlaybackService.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add route playback foreground service"
```

---

### Task 14: Route editor ViewModel

**Files:**
- Create: `manager/ui/map/route/RouteEditorViewModel.kt`

**Interfaces:**
- Consumes: from earlier tasks, `RouteEditor` (3), `RouteSummary` (4), the repository functions (7), `RoutePlaybackState` and `JoystickRunningState` (11).
- Produces: `class RouteEditorViewModel(application)` and `data class RouteEditorUiState`.
  - **Flows:** `mapMode: StateFlow<MapMode>`, `draft: StateFlow<RouteDraft>`, `savedRoutes: StateFlow<List<Route>>`, `summary: StateFlow<RouteSummary>`, `playback: StateFlow<RoutePlaybackStatus?>`, `joystickRunning: StateFlow<Boolean>`, `ui: StateFlow<RouteEditorUiState>`, `fitRouteEvent: Flow<Unit>`.
  - **Edit methods:** `setMapMode`, `addPoint(GeoPoint)`, `movePoint(id, GeoPoint)`, `deletePoint`, `moveEarlier`, `moveLater`, `setPointNumber(id, number)`, `setOrder(ids)`, `updateSegment(aId, bId, settings)`, `setDefaultSpeed`, `setLoop` and `setReversed`.
  - **Sheet and dialog visibility:** `showPointActions(id?)`, `showSetNumberDialog(id?)`, `showSegment(pair?)`, `showDefaultSpeedDialog(Boolean)`, `showSaveDialog(Boolean)` and `showClearDialog(Boolean)`.
  - **Save and clear:** `saveRoute(name, asNew)`, `clearRoute()`.
  - **Loading from favorites:** `requestLoadRoute(route)`, `confirmLoad()`, `cancelLoad()`, `requestOpenRoutesTab()` and `consumeOpenRoutesTabRequest(): Boolean`.

- [ ] **Step 1: Create the ViewModel**

```kotlin
package com.noobexon.xposedfakelocation.manager.ui.map.route

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noobexon.xposedfakelocation.data.model.MapMode
import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.data.model.RouteDraft
import com.noobexon.xposedfakelocation.data.model.SegmentSettings
import com.noobexon.xposedfakelocation.data.model.SpeedChoice
import com.noobexon.xposedfakelocation.data.repository.PreferencesRepository
import com.noobexon.xposedfakelocation.manager.joystick.JoystickRunningState
import com.noobexon.xposedfakelocation.manager.route.RouteEditor
import com.noobexon.xposedfakelocation.manager.route.RoutePlaybackState
import com.noobexon.xposedfakelocation.manager.route.RoutePlaybackStatus
import com.noobexon.xposedfakelocation.manager.route.RouteSummary
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.osmdroid.util.GeoPoint
import java.util.UUID

/** Which route sheet or dialog is open on the map. */
@Immutable
data class RouteEditorUiState(
    val pointActionsId: String? = null,
    val setNumberPointId: String? = null,
    val segment: Pair<String, String>? = null,
    val showDefaultSpeedDialog: Boolean = false,
    val showSaveDialog: Boolean = false,
    val showClearDialog: Boolean = false,
    /** A favorite route waiting for "Replace current route?" confirmation. */
    val pendingLoad: Route? = null,
)

/**
 * Owns the map's mode and route draft. Only this ViewModel writes the draft, so it keeps the
 * current value in memory and persists every edit.
 */
class RouteEditorViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PreferencesRepository(application)
    private fun newId() = UUID.randomUUID().toString()

    private val _mapMode = MutableStateFlow(repository.getMapMode())
    val mapMode: StateFlow<MapMode> = _mapMode.asStateFlow()

    private val _draft = MutableStateFlow(repository.getRouteDraft())
    val draft: StateFlow<RouteDraft> = _draft.asStateFlow()

    val savedRoutes: StateFlow<List<Route>> =
        repository.getRoutesFlow().stateIn(viewModelScope, SharingStarted.Eagerly, repository.getRoutes())

    private val joystickSettings =
        repository.getJoystickSettingsFlow().stateIn(viewModelScope, SharingStarted.Eagerly, repository.getJoystickSettings())

    val summary: StateFlow<RouteSummary> = combine(_draft, joystickSettings) { d, s -> RouteSummary.of(d.route, s) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RouteSummary.of(_draft.value.route, joystickSettings.value))

    val playback: StateFlow<RoutePlaybackStatus?> = RoutePlaybackState.current
    val joystickRunning: StateFlow<Boolean> = JoystickRunningState.running

    private val _ui = MutableStateFlow(RouteEditorUiState())
    val ui: StateFlow<RouteEditorUiState> = _ui.asStateFlow()

    private val _fitRouteEvent = Channel<Unit>(Channel.BUFFERED)
    /** Asks the map to frame the whole draft, e.g. after loading a favorite. */
    val fitRouteEvent: Flow<Unit> = _fitRouteEvent.receiveAsFlow()

    private var openRoutesTabRequested = false

    fun setMapMode(mode: MapMode) {
        _mapMode.value = mode
        repository.saveMapMode(mode)
    }

    private fun edit(transform: (Route) -> Route) {
        val current = _draft.value
        val updated = current.copy(route = transform(current.route), dirty = true)
        _draft.value = updated
        repository.saveRouteDraft(updated)
    }

    fun addPoint(point: GeoPoint) = edit { RouteEditor.append(it, point.latitude, point.longitude, newId()) }
    fun movePoint(id: String, point: GeoPoint) = edit { RouteEditor.move(it, id, point.latitude, point.longitude) }
    fun deletePoint(id: String) = edit { RouteEditor.delete(it, id) }
    fun moveEarlier(id: String) = edit { RouteEditor.moveEarlier(it, id) }
    fun moveLater(id: String) = edit { RouteEditor.moveLater(it, id) }
    fun setOrder(ids: List<String>) = edit { RouteEditor.setOrder(it, ids) }
    fun updateSegment(aId: String, bId: String, settings: SegmentSettings) = edit { RouteEditor.setSegment(it, aId, bId, settings) }
    fun setDefaultSpeed(choice: SpeedChoice) = edit { it.copy(defaultSpeed = choice) }
    fun setLoop(loop: Boolean) = edit { it.copy(loop = loop) }
    fun setReversed(reversed: Boolean) = edit { it.copy(reversed = reversed) }

    fun setPointNumber(id: String, number: Int) {
        edit { RouteEditor.moveTo(it, id, number) }
        showSetNumberDialog(null)
    }

    fun showPointActions(id: String?) = _ui.update { it.copy(pointActionsId = id) }
    fun showSetNumberDialog(id: String?) = _ui.update { it.copy(setNumberPointId = id, pointActionsId = null) }
    fun showSegment(segment: Pair<String, String>?) = _ui.update { it.copy(segment = segment) }
    fun showDefaultSpeedDialog(show: Boolean) = _ui.update { it.copy(showDefaultSpeedDialog = show) }
    fun showSaveDialog(show: Boolean) = _ui.update { it.copy(showSaveDialog = show) }
    fun showClearDialog(show: Boolean) = _ui.update { it.copy(showClearDialog = show) }

    fun clearRoute() {
        replaceDraft(RouteDraft(Route(id = newId())))
        showClearDialog(false)
    }

    /** Saves the draft to favorites; [asNew] (or a draft not loaded from a favorite) gets a fresh id. */
    fun saveRoute(name: String, asNew: Boolean) {
        val current = _draft.value
        val id = current.sourceRouteId.takeUnless { asNew } ?: newId()
        val saved = RouteEditor.pruneSegments(current.route).copy(id = id, name = name.trim())
        repository.saveRoute(saved)
        replaceDraft(RouteDraft(route = saved, sourceRouteId = id, dirty = false))
        showSaveDialog(false)
    }

    /** Loads a favorite, asking first when that would discard unsaved edits. */
    fun requestLoadRoute(route: Route) {
        val current = _draft.value
        val wouldLoseEdits = current.dirty && current.route.points.isNotEmpty() && current.sourceRouteId != route.id
        if (wouldLoseEdits) _ui.update { it.copy(pendingLoad = route) } else loadRoute(route)
    }

    fun confirmLoad() {
        _ui.value.pendingLoad?.let(::loadRoute)
    }

    fun cancelLoad() = _ui.update { it.copy(pendingLoad = null) }

    fun requestOpenRoutesTab() {
        openRoutesTabRequested = true
    }

    /** Whether the Favorites screen should open on its Routes tab; consumed once. */
    fun consumeOpenRoutesTabRequest(): Boolean {
        val requested = openRoutesTabRequested
        openRoutesTabRequested = false
        return requested
    }

    private fun loadRoute(route: Route) {
        replaceDraft(RouteDraft(route = route, sourceRouteId = route.id, dirty = false))
        // Mode switches are locked while anything plays.
        if (!repository.getIsPlaying() && playback.value == null) setMapMode(MapMode.ROUTE)
        _ui.update { it.copy(pendingLoad = null) }
        _fitRouteEvent.trySend(Unit)
    }

    private fun replaceDraft(draft: RouteDraft) {
        _draft.value = draft
        repository.saveRouteDraft(draft)
    }
}
```

- [ ] **Step 2: Compile check.** Run `.\gradlew.bat :app:compileDebugKotlin`. Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```powershell
git add app/src/main/java/com/noobexon/xposedfakelocation/manager/ui/map/route/RouteEditorViewModel.kt
git commit -m "feat: add route editor view model"
```

---

### Task 15: Draw and edit the route on the map

**Files:**
- Create: `manager/ui/map/route/RouteMarkerIcons.kt`, `manager/ui/map/route/RouteMapOverlays.kt`
- Modify: `manager/ui/map/MapViewContainer.kt`

**Interfaces:**
- Consumes: from Task 2, `Route`, `travelSegments`; from Task 1, `GeoMath`.
- Produces:
  - `@Composable internal fun RouteOverlaysEffect(mapView, route: Route?, editable: Boolean, onPointTap: (String) -> Unit, onPointMoved: (String, GeoPoint) -> Unit, onSegmentTap: (String, String) -> Unit)`.
  - `@Composable internal fun HandleFitRouteEvent(mapView, route: Route?, fitRouteEvent: Flow<Unit>)`.
  - New `MapViewContainer` parameters, all with defaults: `routeMode`, `routePlaying`, `route`, `routeEditable`, `fitRouteEvent`, `onRouteTap`, `onRoutePointTap`, `onRoutePointMoved`, `onRouteSegmentTap`.

- [ ] **Step 1: Create `RouteMarkerIcons.kt`**

```kotlin
package com.noobexon.xposedfakelocation.manager.ui.map.route

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlin.math.roundToInt

/** Bitmap icons for route markers; drawn by hand so they need no drawable resources. */
internal object RouteMarkerIcons {
    fun numbered(context: Context, number: Int, fill: Int, textColor: Int): Drawable {
        val density = context.resources.displayMetrics.density
        val size = (28 * density).roundToInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = fill
        canvas.drawCircle(center, center, center, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2 * density
        paint.color = Color.WHITE
        canvas.drawCircle(center, center, center - density, paint)
        paint.style = Paint.Style.FILL
        paint.color = textColor
        paint.textSize = 13 * density
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(number.toString(), center, center - (paint.descent() + paint.ascent()) / 2, paint)
        return BitmapDrawable(context.resources, bitmap)
    }

    /** A triangle pointing along [bearingDegrees] (0 = up/north, clockwise), for a north-up map. */
    fun arrow(context: Context, bearingDegrees: Float, color: Int): Drawable {
        val density = context.resources.displayMetrics.density
        val size = (18 * density).roundToInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.rotate(bearingDegrees, size / 2f, size / 2f)
        val inset = 3 * density
        val path = Path().apply {
            moveTo(size / 2f, inset)
            lineTo(size - inset, size - inset)
            lineTo(size / 2f, size * 0.7f)
            lineTo(inset, size - inset)
            close()
        }
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        return BitmapDrawable(context.resources, bitmap)
    }
}
```

- [ ] **Step 2: Create `RouteMapOverlays.kt`**

```kotlin
package com.noobexon.xposedfakelocation.manager.ui.map.route

import android.graphics.DashPathEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.noobexon.xposedfakelocation.data.GeoMath
import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.data.model.travelSegments
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import kotlin.coroutines.resume

/**
 * Draws [route] as numbered markers and direction-arrowed segments, rebuilding the overlays
 * whenever the route or [editable] changes. Pass `null` to remove them.
 */
@Composable
internal fun RouteOverlaysEffect(
    mapView: MapView,
    route: Route?,
    editable: Boolean,
    onPointTap: (String) -> Unit,
    onPointMoved: (String, GeoPoint) -> Unit,
    onSegmentTap: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val lineColor = MaterialTheme.colorScheme.primary.toArgb()
    val pointColor = MaterialTheme.colorScheme.primary.toArgb()
    val startColor = MaterialTheme.colorScheme.tertiary.toArgb()
    val endColor = MaterialTheme.colorScheme.error.toArgb()
    val textColor = MaterialTheme.colorScheme.onPrimary.toArgb()
    val currentOnPointTap by rememberUpdatedState(onPointTap)
    val currentOnPointMoved by rememberUpdatedState(onPointMoved)
    val currentOnSegmentTap by rememberUpdatedState(onSegmentTap)
    val overlays = remember(mapView) { mutableListOf<Overlay>() }

    DisposableEffect(mapView) {
        onDispose {
            mapView.overlays.removeAll(overlays)
            overlays.clear()
        }
    }

    LaunchedEffect(mapView, route, editable, lineColor, startColor, endColor) {
        mapView.overlays.removeAll(overlays)
        overlays.clear()
        if (route != null && route.points.isNotEmpty()) {
            val density = context.resources.displayMetrics.density
            route.travelSegments().forEach { segment ->
                val a = segment.from
                val b = segment.to
                overlays += Polyline(mapView).apply {
                    setPoints(listOf(GeoPoint(a.latitude, a.longitude), GeoPoint(b.latitude, b.longitude)))
                    outlinePaint.color = lineColor
                    outlinePaint.strokeWidth = 4 * density
                    if (segment.closing) outlinePaint.pathEffect = DashPathEffect(floatArrayOf(12 * density, 8 * density), 0f)
                    setOnClickListener { _, _, _ ->
                        if (editable) currentOnSegmentTap(a.id, b.id)
                        true
                    }
                }
                val distance = GeoMath.distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
                if (distance > 0.0) {
                    val bearing = GeoMath.initialBearing(a.latitude, a.longitude, b.latitude, b.longitude)
                    val (midLat, midLon) = GeoMath.destination(a.latitude, a.longitude, bearing, distance / 2)
                    overlays += Marker(mapView).apply {
                        position = GeoPoint(midLat, midLon)
                        icon = RouteMarkerIcons.arrow(context, bearing.toFloat(), lineColor)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        setInfoWindow(null)
                        setOnMarkerClickListener { _, _ ->
                            if (editable) currentOnSegmentTap(a.id, b.id)
                            true
                        }
                    }
                }
            }
            val last = route.points.size
            val startNumber = if (route.reversed) last else 1
            val endNumber = if (route.loop) -1 else if (route.reversed) 1 else last
            route.points.forEachIndexed { index, point ->
                val number = index + 1
                val fill = when (number) {
                    startNumber -> startColor
                    endNumber -> endColor
                    else -> pointColor
                }
                overlays += Marker(mapView).apply {
                    position = GeoPoint(point.latitude, point.longitude)
                    icon = RouteMarkerIcons.numbered(context, number, fill, textColor)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setInfoWindow(null)
                    isDraggable = editable
                    setOnMarkerClickListener { _, _ ->
                        if (editable) currentOnPointTap(point.id)
                        true
                    }
                    setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                        override fun onMarkerDrag(marker: Marker) = Unit
                        override fun onMarkerDragStart(marker: Marker) = Unit
                        override fun onMarkerDragEnd(marker: Marker) = currentOnPointMoved(point.id, marker.position)
                    })
                }
            }
            mapView.overlays.addAll(overlays)
        }
        mapView.invalidate()
    }
}

/** Frames the whole route when [fitRouteEvent] fires (after the map has laid out and restored its camera). */
@Composable
internal fun HandleFitRouteEvent(mapView: MapView, route: Route?, fitRouteEvent: Flow<Unit>) {
    val currentRoute by rememberUpdatedState(route)
    LaunchedEffect(mapView, fitRouteEvent) {
        fitRouteEvent.collect {
            mapView.awaitFirstLayout()
            delay(FIT_DELAY_MS)
            val points = currentRoute?.points.orEmpty().map { GeoPoint(it.latitude, it.longitude) }
            when {
                points.size == 1 -> mapView.controller.animateTo(points.first())
                points.size > 1 -> mapView.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(points).increaseByScale(1.4f), true)
            }
        }
    }
}

// Lets CenterMapOnUserLocation restore the camera first, so this fit isn't overwritten.
private const val FIT_DELAY_MS = 400L

private suspend fun MapView.awaitFirstLayout() {
    if (isLaidOut) return
    suspendCancellableCoroutine { continuation ->
        addOnFirstLayoutListener { _, _, _, _, _ -> if (continuation.isActive) continuation.resume(Unit) }
    }
}
```

- [ ] **Step 3: Modify `MapViewContainer.kt`.**

Add these imports:

```kotlin
import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.manager.ui.map.route.HandleFitRouteEvent
import com.noobexon.xposedfakelocation.manager.ui.map.route.RouteOverlaysEffect
import kotlinx.coroutines.flow.emptyFlow
```

Append these parameters after `onInitialLocationResolved`, and document them in the KDoc:

```kotlin
    routeMode: Boolean = false,
    routePlaying: Boolean = false,
    route: Route? = null,
    routeEditable: Boolean = false,
    fitRouteEvent: Flow<Unit> = emptyFlow(),
    onRouteTap: (GeoPoint) -> Unit = {},
    onRoutePointTap: (String) -> Unit = {},
    onRoutePointMoved: (String, GeoPoint) -> Unit = {},
    onRouteSegmentTap: (String, String) -> Unit = {},
```

KDoc lines:

```kotlin
 * @param routeMode When `true`, taps edit the route and the location marker is hidden unless a
 *   route is playing (then it shows the moving position).
 * @param route The route to draw in Route mode.
 * @param routeEditable Whether route markers can be tapped and dragged (false while playing).
 * @param fitRouteEvent One-shot request to frame the whole [route].
```

Replace the `HandleMarkerUpdates(...)` and `SetupMapClickListener(...)` lines with:

```kotlin
    val markerLocation = if (routeMode && !routePlaying) null else lastClickedLocation
    HandleMarkerUpdates(mapView, userMarker, markerLocation)
    SetupMapClickListener(mapView, isPlaying) { point ->
        point?.let { if (routeMode) onRouteTap(it) else onClickedLocationChange(it) }
    }
    RouteOverlaysEffect(
        mapView = mapView,
        route = route.takeIf { routeMode },
        editable = routeEditable,
        onPointTap = onRoutePointTap,
        onPointMoved = onRoutePointMoved,
        onSegmentTap = onRouteSegmentTap,
    )
    HandleFitRouteEvent(mapView, route, fitRouteEvent)
```

- [ ] **Step 4: Compile check.** Run `.\gradlew.bat :app:compileDebugKotlin`. Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/noobexon/xposedfakelocation/manager/ui/map/route/RouteMarkerIcons.kt app/src/main/java/com/noobexon/xposedfakelocation/manager/ui/map/route/RouteMapOverlays.kt app/src/main/java/com/noobexon/xposedfakelocation/manager/ui/map/MapViewContainer.kt
git commit -m "feat: draw and edit routes on the map"
```

---

### Task 16: Route editing controls (panel, sheets, dialogs, play button)

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Create:
  - `manager/ui/map/route/RouteText.kt`, `MapModeSwitch.kt`, `SpeedChoicePicker.kt`, `RouteSheets.kt`, `RouteDialogs.kt`, `RoutePanel.kt`, `RouteModeUi.kt`
  - `manager/route/RoutePlayButton.kt`

**Interfaces:**
- Consumes: from earlier tasks, `RouteEditorViewModel` (14), `RoutePlaybackService` (13), `RouteFormat` (12) and `RouteSummary` (4).
- Produces:
  - `@Composable fun MapModeSwitch(mode, enabled, onModeChange, modifier)`.
  - `@Composable fun BoxScope.RouteModeUi(viewModel, isPlaying, onStopSpoofing, onLoadFromFavorites)`.
  - `@Composable fun RouteLoadConfirmation(viewModel)`.

- [ ] **Step 1: Add the reorderable dependency.** In `gradle/libs.versions.toml`:
  - Under `[versions]`, add `reorderable = "2.4.3"`.
  - Under `[libraries]`, add `reorderable = { group = "sh.calvin.reorderable", name = "reorderable", version.ref = "reorderable" }`.

  In `app/build.gradle.kts`, add `implementation(libs.reorderable)` after `implementation(libs.coil.compose)`.

- [ ] **Step 2: Create `RouteText.kt`**

```kotlin
package com.noobexon.xposedfakelocation.manager.ui.map.route

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.model.SpeedChoice
import com.noobexon.xposedfakelocation.manager.joystick.displayKmh
import com.noobexon.xposedfakelocation.manager.route.RouteFormat
import com.noobexon.xposedfakelocation.manager.ui.settings.labelRes

@Composable
fun distanceText(meters: Double): String = stringResource(R.string.route_distance_km, RouteFormat.km(meters))

@Composable
fun durationText(seconds: Double): String {
    val minutes = RouteFormat.minutes(seconds)
    return if (minutes < 60) {
        stringResource(R.string.route_duration_min, minutes)
    } else {
        stringResource(R.string.route_duration_h_min, minutes / 60, minutes % 60)
    }
}

@Composable
fun speedChoiceText(choice: SpeedChoice): String = when (choice) {
    is SpeedChoice.Preset -> stringResource(choice.preset.labelRes)
    is SpeedChoice.Custom -> "${displayKmh(choice.speedMps)} ${stringResource(R.string.unit_kilometers_per_hour)}"
}
```

- [ ] **Step 3: Create `MapModeSwitch.kt`**

```kotlin
package com.noobexon.xposedfakelocation.manager.ui.map.route

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.model.MapMode

/** Location / Route switch floating over the map; locked while anything plays. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapModeSwitch(mode: MapMode, enabled: Boolean, onModeChange: (MapMode) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shadowElevation = 4.dp,
        modifier = modifier,
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(4.dp)) {
            MapMode.entries.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = mode == entry,
                    onClick = { onModeChange(entry) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index, MapMode.entries.size),
                    icon = {
                        Icon(
                            imageVector = if (entry == MapMode.LOCATION) Icons.Default.Place else Icons.Default.Route,
                            contentDescription = null,
                        )
                    },
                ) {
                    Text(stringResource(if (entry == MapMode.LOCATION) R.string.map_mode_location else R.string.map_mode_route))
                }
            }
        }
    }
}
```

- [ ] **Step 4: Create `SpeedChoicePicker.kt`**

```kotlin
package com.noobexon.xposedfakelocation.manager.ui.map.route

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.JOYSTICK_MAX_SPEED_MPS
import com.noobexon.xposedfakelocation.data.JOYSTICK_MIN_SPEED_MPS
import com.noobexon.xposedfakelocation.data.MPS_TO_KMH
import com.noobexon.xposedfakelocation.data.model.JoystickPreset
import com.noobexon.xposedfakelocation.data.model.SpeedChoice
import com.noobexon.xposedfakelocation.manager.joystick.displayKmh
import com.noobexon.xposedfakelocation.manager.ui.settings.labelRes

private const val DEFAULT_CUSTOM_KMH = 10f

/**
 * Walk / Run / Bike / Drive / Custom chips, plus "Default" when [allowDefault]. `null` means the
 * route default. Custom speeds are entered in km/h and applied once they're in range.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeedChoicePicker(choice: SpeedChoice?, allowDefault: Boolean, onChange: (SpeedChoice?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (allowDefault) {
                FilterChip(
                    selected = choice == null,
                    onClick = { onChange(null) },
                    label = { Text(stringResource(R.string.route_speed_default)) },
                )
            }
            JoystickPreset.entries.forEach { preset ->
                FilterChip(
                    selected = choice == SpeedChoice.Preset(preset),
                    onClick = { onChange(SpeedChoice.Preset(preset)) },
                    label = { Text(stringResource(preset.labelRes)) },
                )
            }
            FilterChip(
                selected = choice is SpeedChoice.Custom,
                onClick = { if (choice !is SpeedChoice.Custom) onChange(SpeedChoice.Custom(DEFAULT_CUSTOM_KMH / MPS_TO_KMH)) },
                label = { Text(stringResource(R.string.route_speed_custom)) },
            )
        }
        if (choice is SpeedChoice.Custom) {
            var text by remember(choice) { mutableStateOf(displayKmh(choice.speedMps).toString()) }
            val range = JOYSTICK_MIN_SPEED_MPS * MPS_TO_KMH..JOYSTICK_MAX_SPEED_MPS * MPS_TO_KMH
            val parsed = text.toFloatOrNull()
            val valid = parsed != null && parsed in range
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    val kmh = it.toFloatOrNull()
                    if (kmh != null && kmh in range) onChange(SpeedChoice.Custom(kmh / MPS_TO_KMH))
                },
                label = { Text(stringResource(R.string.route_speed_custom_label)) },
                isError = !valid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
```

`remember(choice)` resets the field whenever the stored value changes. Every valid keystroke changes the stored value, so the text gets re-rounded to whole km/h on each keystroke. That's acceptable for whole-number km/h input.

- [ ] **Step 5: Create `RouteSheets.kt`**

```kotlin
package com.noobexon.xposedfakelocation.manager.ui.map.route

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.ROUTE_MAX_DWELL_SECONDS
import com.noobexon.xposedfakelocation.data.ROUTE_MAX_VARIATION_PERCENT
import com.noobexon.xposedfakelocation.data.model.SegmentSettings
import kotlin.math.roundToInt

private const val VARIATION_STEP_PERCENT = 5

/** Per-segment speed, wait and variation; every change applies immediately. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentSheet(
    fromNumber: Int,
    toNumber: Int,
    settings: SegmentSettings,
    onChange: (SegmentSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp),
        ) {
            Text(stringResource(R.string.route_segment_title, fromNumber, toNumber), style = MaterialTheme.typography.titleMedium)
            SpeedChoicePicker(choice = settings.speed, allowDefault = true, onChange = { onChange(settings.copy(speed = it)) })
            var waitText by remember(settings.dwellSeconds) { mutableStateOf(settings.dwellSeconds.toString()) }
            val wait = waitText.toIntOrNull()
            OutlinedTextField(
                value = waitText,
                onValueChange = {
                    waitText = it
                    it.toIntOrNull()?.takeIf { s -> s in 0..ROUTE_MAX_DWELL_SECONDS }?.let { s -> onChange(settings.copy(dwellSeconds = s)) }
                },
                label = { Text(stringResource(R.string.route_wait_label)) },
                isError = wait == null || wait !in 0..ROUTE_MAX_DWELL_SECONDS,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.route_variation_label, settings.variationPercent))
            Slider(
                value = settings.variationPercent.toFloat(),
                onValueChange = {
                    val stepped = (it / VARIATION_STEP_PERCENT).roundToInt() * VARIATION_STEP_PERCENT
                    if (stepped != settings.variationPercent) onChange(settings.copy(variationPercent = stepped))
                },
                valueRange = 0f..ROUTE_MAX_VARIATION_PERCENT.toFloat(),
                steps = ROUTE_MAX_VARIATION_PERCENT / VARIATION_STEP_PERCENT - 1,
            )
        }
    }
}

/** Actions for one tapped point. Each action also closes the sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointActionsSheet(
    number: Int,
    count: Int,
    onDelete: () -> Unit,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onSetNumber: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.route_point_label, number),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            ActionRow(Icons.Default.ArrowUpward, R.string.route_move_earlier, enabled = number > 1, onClick = onMoveEarlier)
            ActionRow(Icons.Default.ArrowDownward, R.string.route_move_later, enabled = number < count, onClick = onMoveLater)
            ActionRow(Icons.Default.FormatListNumbered, R.string.route_set_number, enabled = count > 1, onClick = onSetNumber)
            ActionRow(Icons.Outlined.Delete, R.string.route_delete_point, enabled = true, onClick = onDelete)
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: Int, enabled: Boolean, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.38f
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)) },
        headlineContent = { Text(stringResource(label), color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)) },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}
```

- [ ] **Step 6: Create `RouteDialogs.kt`**

```kotlin
package com.noobexon.xposedfakelocation.manager.ui.map.route

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.model.SpeedChoice

/** Asks for a name; offers Update / Save as new when the draft came from a saved route. */
@Composable
fun SaveRouteDialog(initialName: String, canUpdate: Boolean, onSave: (name: String, asNew: Boolean) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    val nameError = name.isBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.route_save_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.field_name)) },
                isError = nameError,
                supportingText = if (nameError) {
                    { Text(stringResource(R.string.validation_name_required)) }
                } else null,
                singleLine = true,
            )
        },
        confirmButton = {
            Row {
                if (canUpdate) {
                    TextButton(enabled = !nameError, onClick = { onSave(name, false) }) {
                        Text(stringResource(R.string.route_save_update))
                    }
                }
                TextButton(enabled = !nameError, onClick = { onSave(name, true) }) {
                    Text(stringResource(if (canUpdate) R.string.route_save_as_new else R.string.action_save))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun SetNumberDialog(count: Int, current: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current.toString()) }
    val number = text.toIntOrNull()?.takeIf { it in 1..count }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.route_set_number_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.route_set_number_label, count)) },
                isError = number == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(enabled = number != null, onClick = { number?.let(onConfirm) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun DefaultSpeedDialog(choice: SpeedChoice, onChange: (SpeedChoice) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.route_default_speed_title)) },
        text = { SpeedChoicePicker(choice = choice, allowDefault = false, onChange = { it?.let(onChange) }) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } },
    )
}

@Composable
fun ConfirmDialog(
    @StringRes title: Int,
    @StringRes message: Int,
    @StringRes confirmLabel: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(confirmLabel)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
```

- [ ] **Step 7: Create `manager/route/RoutePlayButton.kt`.** It follows `JoystickButton`'s permission flow, but the overlay is optional:

```kotlin
package com.noobexon.xposedfakelocation.manager.route

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.manager.App

/**
 * Route mode's play/stop button. Stops a playing route, or spoofing that was started some other
 * way; otherwise starts the draft, asking for the (optional) overlay and notification permissions.
 *
 * @param blockedReason why the route can't start, or `null` when it can.
 */
@Composable
fun RoutePlayButton(
    @StringRes blockedReason: Int?,
    routePlaying: Boolean,
    otherSpoofingActive: Boolean,
    onStopSpoofing: () -> Unit,
) {
    val context = LocalContext.current
    var showOverlayRationale by remember { mutableStateOf(false) }
    val moduleInactive = stringResource(R.string.system_hooks_module_inactive)
    val blockedMessage = blockedReason?.let { stringResource(it) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { RoutePlaybackService.start(context) }

    fun startWithNotificationPermission() {
        val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            RoutePlaybackService.start(context)
        }
    }

    val overlaySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { startWithNotificationPermission() }

    val stopping = routePlaying || otherSpoofingActive
    val active = stopping || blockedReason == null
    SmallFloatingActionButton(
        onClick = {
            when {
                routePlaying -> RoutePlaybackService.stop(context)
                otherSpoofingActive -> onStopSpoofing()
                blockedMessage != null -> Toast.makeText(context, blockedMessage, Toast.LENGTH_SHORT).show()
                App.service == null -> Toast.makeText(context, moduleInactive, Toast.LENGTH_SHORT).show()
                !Settings.canDrawOverlays(context) -> showOverlayRationale = true
                else -> startWithNotificationPermission()
            }
        },
        containerColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        contentColor = if (active) contentColorFor(MaterialTheme.colorScheme.primary) else MaterialTheme.colorScheme.onSurfaceVariant,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = if (active) 4.dp else 0.dp),
    ) {
        Icon(
            imageVector = if (stopping) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = stringResource(if (stopping) R.string.cd_route_stop else R.string.cd_route_play),
        )
    }

    if (showOverlayRationale) {
        AlertDialog(
            onDismissRequest = { showOverlayRationale = false },
            title = { Text(stringResource(R.string.joystick_overlay_permission_title)) },
            text = { Text(stringResource(R.string.route_overlay_permission_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showOverlayRationale = false
                    overlaySettingsLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    )
                }) { Text(stringResource(R.string.permissions_open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showOverlayRationale = false
                    startWithNotificationPermission()
                }) { Text(stringResource(R.string.route_continue_without_overlay)) }
            },
        )
    }
}
```

- [ ] **Step 8: Create `RoutePanel.kt`**

```kotlin
package com.noobexon.xposedfakelocation.manager.ui.map.route

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.ROUTE_MIN_POINTS
import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.manager.route.RoutePlaybackStatus
import com.noobexon.xposedfakelocation.manager.route.RouteSummary
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Bottom card for Route mode: summary, default speed, Loop/Reverse, play button and an expandable
 * reorderable point list. Read-only (with progress) while [editable] is false.
 */
@Composable
fun RoutePanel(
    route: Route,
    summary: RouteSummary,
    editable: Boolean,
    playback: RoutePlaybackStatus?,
    playButton: @Composable () -> Unit,
    onDefaultSpeedClick: () -> Unit,
    onLoopChange: (Boolean) -> Unit,
    onReversedChange: (Boolean) -> Unit,
    onOrderChange: (List<String>) -> Unit,
    onDeletePoint: (String) -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = summaryText(summary),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_route_options))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.route_save_to_favorites)) },
                            enabled = route.points.size >= ROUTE_MIN_POINTS,
                            onClick = { menuOpen = false; onSave() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.route_load_from_favorites)) },
                            enabled = editable,
                            onClick = { menuOpen = false; onLoad() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.route_clear)) },
                            enabled = editable && route.points.isNotEmpty(),
                            onClick = { menuOpen = false; onClear() },
                        )
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        contentDescription = stringResource(if (expanded) R.string.cd_route_hide_points else R.string.cd_route_show_points),
                    )
                }
                playButton()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = onDefaultSpeedClick,
                    enabled = editable,
                    label = { Text(stringResource(R.string.route_default_speed, speedChoiceText(route.defaultSpeed))) },
                )
                FilterChip(
                    selected = route.loop,
                    onClick = { onLoopChange(!route.loop) },
                    enabled = editable,
                    label = { Text(stringResource(R.string.route_loop)) },
                )
                FilterChip(
                    selected = route.reversed,
                    onClick = { onReversedChange(!route.reversed) },
                    enabled = editable,
                    label = { Text(stringResource(R.string.route_reverse)) },
                )
            }
            playback?.let { status ->
                LinearProgressIndicator(
                    progress = { status.status.progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
            AnimatedVisibility(visible = expanded) {
                PointList(route = route, editable = editable, onOrderChange = onOrderChange, onDeletePoint = onDeletePoint)
            }
        }
    }
}

@Composable
private fun summaryText(summary: RouteSummary): String = when {
    summary.pointCount == 0 -> stringResource(R.string.route_empty_hint)
    summary.pointCount < ROUTE_MIN_POINTS -> stringResource(R.string.route_needs_two_points)
    else -> stringResource(R.string.route_summary, summary.pointCount, distanceText(summary.distanceM), durationText(summary.durationS))
}

/** Reorders locally while dragging and commits the new order once, when the drag stops. */
@Composable
private fun PointList(route: Route, editable: Boolean, onOrderChange: (List<String>) -> Unit, onDeletePoint: (String) -> Unit) {
    var order by remember(route.points) { mutableStateOf(route.points) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        order = order.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
        items(order, key = { it.id }) { point ->
            ReorderableItem(reorderState, key = point.id) {
                val number = order.indexOf(point) + 1
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        enabled = editable,
                        onClick = {},
                        modifier = Modifier.draggableHandle(
                            enabled = editable,
                            onDragStopped = { onOrderChange(order.map { it.id }) },
                        ),
                    ) {
                        Icon(Icons.Default.DragHandle, contentDescription = stringResource(R.string.cd_route_drag_handle, number))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.route_point_label, number), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = stringResource(R.string.coordinates_lat_lon, "%.5f".format(point.latitude), "%.5f".format(point.longitude)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(enabled = editable, onClick = { onDeletePoint(point.id) }) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.cd_route_delete_point, number),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
```

If `draggableHandle`'s parameter names differ in the resolved library version, match the library's signature. Keep `enabled` and the on-stop commit, and leave everything else as is.

- [ ] **Step 9: Create `RouteModeUi.kt`**

```kotlin
package com.noobexon.xposedfakelocation.manager.ui.map.route

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.ROUTE_MIN_POINTS
import com.noobexon.xposedfakelocation.data.model.segmentKey
import com.noobexon.xposedfakelocation.data.model.segmentSettings
import com.noobexon.xposedfakelocation.data.model.travelSegments
import com.noobexon.xposedfakelocation.manager.route.RoutePlayButton

/** Route mode's bottom panel plus every route sheet and dialog, wired to [viewModel]. */
@Composable
fun BoxScope.RouteModeUi(
    viewModel: RouteEditorViewModel,
    isPlaying: Boolean,
    onStopSpoofing: () -> Unit,
    onLoadFromFavorites: () -> Unit,
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val joystickRunning by viewModel.joystickRunning.collectAsStateWithLifecycle()
    val savedRoutes by viewModel.savedRoutes.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val route = draft.route
    val editable = !isPlaying && playback == null
    val blockedReason = when {
        route.points.size < ROUTE_MIN_POINTS -> R.string.route_needs_two_points
        joystickRunning -> R.string.route_joystick_running
        else -> null
    }

    RoutePanel(
        route = route,
        summary = summary,
        editable = editable,
        playback = playback,
        playButton = {
            RoutePlayButton(
                blockedReason = blockedReason,
                routePlaying = playback != null,
                otherSpoofingActive = isPlaying && playback == null,
                onStopSpoofing = onStopSpoofing,
            )
        },
        onDefaultSpeedClick = { viewModel.showDefaultSpeedDialog(true) },
        onLoopChange = viewModel::setLoop,
        onReversedChange = viewModel::setReversed,
        onOrderChange = viewModel::setOrder,
        onDeletePoint = viewModel::deletePoint,
        onSave = { viewModel.showSaveDialog(true) },
        onLoad = onLoadFromFavorites,
        onClear = { viewModel.showClearDialog(true) },
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
    )

    ui.pointActionsId?.let { id ->
        val index = route.points.indexOfFirst { it.id == id }
        if (index >= 0) {
            val close = { viewModel.showPointActions(null) }
            PointActionsSheet(
                number = index + 1,
                count = route.points.size,
                onDelete = { viewModel.deletePoint(id); close() },
                onMoveEarlier = { viewModel.moveEarlier(id); close() },
                onMoveLater = { viewModel.moveLater(id); close() },
                onSetNumber = { viewModel.showSetNumberDialog(id) },
                onDismiss = close,
            )
        }
    }

    ui.setNumberPointId?.let { id ->
        val index = route.points.indexOfFirst { it.id == id }
        if (index >= 0) {
            SetNumberDialog(
                count = route.points.size,
                current = index + 1,
                onConfirm = { viewModel.setPointNumber(id, it) },
                onDismiss = { viewModel.showSetNumberDialog(null) },
            )
        }
    }

    ui.segment?.let { (a, b) ->
        val segment = route.travelSegments().firstOrNull { segmentKey(it.from.id, it.to.id) == segmentKey(a, b) }
        if (segment != null) {
            SegmentSheet(
                fromNumber = segment.fromNumber,
                toNumber = segment.toNumber,
                settings = route.segmentSettings(a, b),
                onChange = { viewModel.updateSegment(a, b, it) },
                onDismiss = { viewModel.showSegment(null) },
            )
        }
    }

    if (ui.showDefaultSpeedDialog) {
        DefaultSpeedDialog(
            choice = route.defaultSpeed,
            onChange = viewModel::setDefaultSpeed,
            onDismiss = { viewModel.showDefaultSpeedDialog(false) },
        )
    }

    if (ui.showSaveDialog) {
        SaveRouteDialog(
            initialName = route.name,
            canUpdate = draft.sourceRouteId?.let { id -> savedRoutes.any { it.id == id } } == true,
            onSave = viewModel::saveRoute,
            onDismiss = { viewModel.showSaveDialog(false) },
        )
    }

    if (ui.showClearDialog) {
        ConfirmDialog(
            title = R.string.route_clear,
            message = R.string.route_clear_message,
            confirmLabel = R.string.route_clear,
            onConfirm = viewModel::clearRoute,
            onDismiss = { viewModel.showClearDialog(false) },
        )
    }
}

/** "Replace current route?" for a favorite picked while the draft has unsaved edits. */
@Composable
fun RouteLoadConfirmation(viewModel: RouteEditorViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    if (ui.pendingLoad != null) {
        ConfirmDialog(
            title = R.string.route_replace_title,
            message = R.string.route_replace_message,
            confirmLabel = R.string.route_replace_confirm,
            onConfirm = viewModel::confirmLoad,
            onDismiss = viewModel::cancelLoad,
        )
    }
}
```

- [ ] **Step 10: Compile check.** Run `.\gradlew.bat :app:compileDebugKotlin`. Expected: BUILD SUCCESSFUL.

- [ ] **Step 11: Commit**

```powershell
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/noobexon/xposedfakelocation/manager/ui/map/route app/src/main/java/com/noobexon/xposedfakelocation/manager/route/RoutePlayButton.kt
git commit -m "feat: add route editing panel, sheets and dialogs"
```

---

### Task 17: Wire Route mode into the map screen and navigation

**Files:**
- Modify: `manager/ui/map/MapScreen.kt`, `manager/ui/navigation/NavGraph.kt`

**Interfaces:**
- Consumes: from Task 16, `MapModeSwitch`, `RouteModeUi`, `RouteLoadConfirmation`; from Task 14, `RouteEditorViewModel`.
- Produces: `MapScreen(navController, mapViewModel, routeEditorViewModel)`.

- [ ] **Step 1: Update `MapScreen.kt`.**

Add these imports:

```kotlin
import androidx.compose.ui.Alignment
import com.noobexon.xposedfakelocation.data.model.MapMode
import com.noobexon.xposedfakelocation.manager.ui.map.route.MapModeSwitch
import com.noobexon.xposedfakelocation.manager.ui.map.route.RouteEditorViewModel
import com.noobexon.xposedfakelocation.manager.ui.map.route.RouteLoadConfirmation
import com.noobexon.xposedfakelocation.manager.ui.map.route.RouteModeUi
```

Change the signature to:

```kotlin
fun MapScreen(
    navController: NavController,
    mapViewModel: MapViewModel,
    routeEditorViewModel: RouteEditorViewModel,
) {
```

Also add this KDoc line: `@param routeEditorViewModel Route mode state; shared with Favorites at the [NavHost] level.`

After `val fakeLocationUnset = ...`, add:

```kotlin
    val mapMode by routeEditorViewModel.mapMode.collectAsStateWithLifecycle()
    val draft by routeEditorViewModel.draft.collectAsStateWithLifecycle()
    val playback by routeEditorViewModel.playback.collectAsStateWithLifecycle()
    val joystickRunning by routeEditorViewModel.joystickRunning.collectAsStateWithLifecycle()
    val routeMode = mapMode == MapMode.ROUTE
```

Change the joystick button to:

```kotlin
                        JoystickButton(enabled = isPlaying && uiState.lastClickedLocation != null && playback == null)
```

Wrap the whole body of `floatingActionButton = { ... }` in `if (!routeMode) { ... }`. In Route mode, the play button lives in the route panel so it can't cover the panel.

Replace the `Box(...) { MapViewContainer(...) }` content with:

```kotlin
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                MapViewContainer(
                    isLoading = uiState.isLoading,
                    lastClickedLocation = uiState.lastClickedLocation,
                    userLocation = uiState.userLocation,
                    isPlaying = uiState.isPlaying,
                    mapZoom = uiState.mapZoom,
                    hasResolvedInitialLocation = uiState.hasResolvedInitialLocation,
                    goToPointEvent = mapViewModel.goToPointEvent,
                    centerMapEvent = mapViewModel.centerMapEvent,
                    onClickedLocationChange = mapViewModel::updateClickedLocation,
                    onUserLocationChange = mapViewModel::updateUserLocation,
                    onMapZoomChange = mapViewModel::updateMapZoom,
                    onLoadingFinished = mapViewModel::setLoadingFinished,
                    onInitialLocationResolved = mapViewModel::markInitialLocationResolved,
                    routeMode = routeMode,
                    routePlaying = playback != null,
                    route = draft.route,
                    routeEditable = !isPlaying && playback == null,
                    fitRouteEvent = routeEditorViewModel.fitRouteEvent,
                    onRouteTap = routeEditorViewModel::addPoint,
                    onRoutePointTap = routeEditorViewModel::showPointActions,
                    onRoutePointMoved = routeEditorViewModel::movePoint,
                    onRouteSegmentTap = { a, b -> routeEditorViewModel.showSegment(a to b) },
                )
                MapModeSwitch(
                    mode = mapMode,
                    enabled = !isPlaying && playback == null && !joystickRunning,
                    onModeChange = routeEditorViewModel::setMapMode,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                )
                if (routeMode) {
                    RouteModeUi(
                        viewModel = routeEditorViewModel,
                        isPlaying = isPlaying,
                        onStopSpoofing = mapViewModel::togglePlaying,
                        onLoadFromFavorites = {
                            routeEditorViewModel.requestOpenRoutesTab()
                            navController.navigate(Screen.Favorites.route) { launchSingleTop = true }
                        },
                    )
                }
            }
```

After the `if (showAddToFavoritesDialog) { ... }` block, add:

```kotlin
        RouteLoadConfirmation(routeEditorViewModel)
```

- [ ] **Step 2: Update `NavGraph.kt`.**

Add these imports:

```kotlin
import androidx.compose.runtime.remember
import com.noobexon.xposedfakelocation.data.model.MapMode
import com.noobexon.xposedfakelocation.manager.route.RoutePlaybackState
import com.noobexon.xposedfakelocation.manager.ui.favorites.FavoritesTab
import com.noobexon.xposedfakelocation.manager.ui.map.route.RouteEditorViewModel
```

After `val mapViewModel: MapViewModel = viewModel()`, add `val routeEditorViewModel: RouteEditorViewModel = viewModel()`.

Replace the Favorites and Map destinations with:

```kotlin
        composable(route = Screen.Favorites.route) {
            val initialTab = remember {
                if (routeEditorViewModel.consumeOpenRoutesTabRequest()) FavoritesTab.ROUTES else FavoritesTab.LOCATIONS
            }
            FavoritesScreen(
                navController = navController,
                onFavoriteSelected = { favorite ->
                    mapViewModel.updateClickedLocation(GeoPoint(favorite.latitude, favorite.longitude))
                    if (RoutePlaybackState.current.value == null) routeEditorViewModel.setMapMode(MapMode.LOCATION)
                },
                onRouteSelected = routeEditorViewModel::requestLoadRoute,
                initialTab = initialTab,
            )
        }
        composable(route = Screen.Map.route) {
            MapScreen(navController = navController, mapViewModel, routeEditorViewModel)
        }
```

`FavoritesTab` and the new `FavoritesScreen` parameters come from Task 18, so do Task 18 before compiling this step. If you do run this task first, compile once Task 18 is done.

- [ ] **Step 3: Commit, together with Task 18.** This task doesn't compile on its own, so commit it in Task 18's final step.

---

### Task 18: Routes tab in Favorites

**Files:**
- Modify: `manager/ui/favorites/FavoritesScreen.kt`, `manager/ui/favorites/FavoritesViewModel.kt`
- Create: `manager/ui/favorites/RouteFavorites.kt`

**Interfaces:**
- Consumes: from Task 7, the repository route functions; from Task 4, `RouteSummary`; from Task 16, `distanceText`.
- Produces:
  - `enum class FavoritesTab { LOCATIONS, ROUTES }`.
  - `FavoritesScreen(navController, onFavoriteSelected, onRouteSelected: (Route) -> Unit, initialTab: FavoritesTab = LOCATIONS, favoritesViewModel)`.
  - On `FavoritesViewModel`: `routes: StateFlow<List<Route>>`, `deleteRoute(route)` and `renameRoute(route, name)`.

- [ ] **Step 1: Extend `FavoritesViewModel`.** Add `import com.noobexon.xposedfakelocation.data.model.Route`, then add:

```kotlin
    /** Saved routes, kept in sync with [PreferencesRepository]. */
    val routes: StateFlow<List<Route>> =
        preferencesRepository.getRoutesFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteRoute(route: Route) = preferencesRepository.deleteRoute(route.id)

    fun renameRoute(route: Route, name: String) = preferencesRepository.saveRoute(route.copy(name = name.trim()))
```

- [ ] **Step 2: Create `RouteFavorites.kt`**

```kotlin
package com.noobexon.xposedfakelocation.manager.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.model.JoystickSettings
import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.manager.route.RouteSummary
import com.noobexon.xposedfakelocation.manager.ui.map.route.distanceText

/** Saved routes list with rename/delete; tapping a card loads the route into the map. */
@Composable
internal fun RoutesList(
    routes: List<Route>,
    onRouteClick: (Route) -> Unit,
    onRename: (Route, String) -> Unit,
    onDelete: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    var deletePending by remember { mutableStateOf<Route?>(null) }
    var renamePending by remember { mutableStateOf<Route?>(null) }

    deletePending?.let { route ->
        DeleteConfirmationDialog(
            favoriteName = route.name,
            onConfirm = {
                onDelete(route)
                deletePending = null
            },
            onDismiss = { deletePending = null },
        )
    }
    renamePending?.let { route ->
        RenameRouteDialog(
            initialName = route.name,
            onSave = { name ->
                onRename(route, name)
                renamePending = null
            },
            onDismiss = { renamePending = null },
        )
    }

    if (routes.isEmpty()) {
        RoutesEmptyState(modifier = modifier.fillMaxSize().padding(horizontal = 32.dp))
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(routes, key = { it.id }) { route ->
            RouteFavoriteItem(
                route = route,
                onClick = { onRouteClick(route) },
                onRenameClick = { renamePending = route },
                onDeleteClick = { deletePending = route },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteFavoriteItem(route: Route, onClick: () -> Unit, onRenameClick: () -> Unit, onDeleteClick: () -> Unit) {
    // Distance doesn't depend on speeds, so default joystick settings are enough here.
    val distanceM = remember(route) { RouteSummary.of(route, JoystickSettings()).distanceM }
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Route,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = route.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = buildList {
                    add(stringResource(R.string.favorites_route_points, route.points.size))
                    add(distanceText(distanceM))
                    if (route.loop) add(stringResource(R.string.route_loop))
                    if (route.reversed) add(stringResource(R.string.route_reverse))
                }
                Text(
                    text = details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onRenameClick) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.cd_edit_named_item, route.name),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.cd_delete_named_item, route.name),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun RenameRouteDialog(initialName: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    val nameError = name.isBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.favorites_route_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.field_name)) },
                isError = nameError,
                supportingText = if (nameError) {
                    { Text(stringResource(R.string.validation_name_required)) }
                } else null,
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = !nameError, onClick = { onSave(name) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun RoutesEmptyState(modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(
            Icons.Default.Route,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.favorites_routes_empty),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.favorites_routes_empty_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
```

- [ ] **Step 3: Update `FavoritesScreen.kt`.**
  1. Add these imports:

```kotlin
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.saveable.rememberSaveable
import com.noobexon.xposedfakelocation.data.model.Route
```

  2. Change `private fun DeleteConfirmationDialog` to `internal fun DeleteConfirmationDialog`.
  3. Add `/** Which list the Favorites screen shows. */ enum class FavoritesTab { LOCATIONS, ROUTES }` above `FavoritesScreen`.
  4. Replace `FavoritesScreen`, and add these KDoc lines to it: `@param onRouteSelected Called with a tapped saved route; the caller loads it into the map.` and `@param initialTab Tab shown first (Routes when opened from the route panel).`

```kotlin
@Composable
fun FavoritesScreen(
    navController: NavController,
    onFavoriteSelected: (FavoriteLocation) -> Unit,
    onRouteSelected: (Route) -> Unit,
    initialTab: FavoritesTab = FavoritesTab.LOCATIONS,
    favoritesViewModel: FavoritesViewModel = viewModel()
) {
    val favorites by favoritesViewModel.favorites.collectAsStateWithLifecycle()
    val routes by favoritesViewModel.routes.collectAsStateWithLifecycle()

    FavoritesContent(
        favorites = favorites,
        routes = routes,
        initialTab = initialTab,
        onFavoriteClick = { favorite ->
            onFavoriteSelected(favorite)
            navController.navigateUp()
        },
        onRouteClick = { route ->
            onRouteSelected(route)
            navController.navigateUp()
        },
        onDelete = { favorite -> favoritesViewModel.removeFavorite(favorite) },
        onEdit = { old, new -> favoritesViewModel.updateFavorite(old, new) },
        onDeleteRoute = favoritesViewModel::deleteRoute,
        onRenameRoute = favoritesViewModel::renameRoute,
        onNavigateUp = { navController.navigateUp() },
    )
}
```

  5. Change the `FavoritesContent` parameters to:

```kotlin
private fun FavoritesContent(
    favorites: List<FavoriteLocation>,
    routes: List<Route>,
    initialTab: FavoritesTab,
    onFavoriteClick: (FavoriteLocation) -> Unit,
    onRouteClick: (Route) -> Unit,
    onDelete: (FavoriteLocation) -> Unit,
    onEdit: (old: FavoriteLocation, new: FavoriteLocation) -> Unit,
    onDeleteRoute: (Route) -> Unit,
    onRenameRoute: (Route, String) -> Unit,
    onNavigateUp: () -> Unit,
) {
```

Add `var tab by rememberSaveable { mutableStateOf(initialTab) }` next to the existing `deletePending` / `editPending` state.

  6. Replace the `Scaffold(...) { innerPadding -> ... }` block with:

```kotlin
    Scaffold(
        topBar = { FavoritesTopAppBar(onNavigateUp) }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            TabRow(selectedTabIndex = tab.ordinal) {
                Tab(
                    selected = tab == FavoritesTab.LOCATIONS,
                    onClick = { tab = FavoritesTab.LOCATIONS },
                    text = { Text(stringResource(R.string.favorites_tab_locations)) },
                )
                Tab(
                    selected = tab == FavoritesTab.ROUTES,
                    onClick = { tab = FavoritesTab.ROUTES },
                    text = { Text(stringResource(R.string.favorites_tab_routes)) },
                )
            }
            when (tab) {
                FavoritesTab.LOCATIONS -> if (favorites.isEmpty()) {
                    FavoritesEmptyState(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = favorites,
                            key = { "${it.name}_${it.latitude}_${it.longitude}" }
                        ) { favorite ->
                            FavoriteItem(
                                favorite = favorite,
                                onClick = { onFavoriteClick(favorite) },
                                onEditClick = { editPending = favorite },
                                onDeleteClick = { deletePending = favorite },
                            )
                        }
                    }
                }
                FavoritesTab.ROUTES -> RoutesList(
                    routes = routes,
                    onRouteClick = onRouteClick,
                    onRename = onRenameRoute,
                    onDelete = onDeleteRoute,
                )
            }
        }
    }
```

  7. Update the KDoc of `FavoritesContent` to mention the tabs and the route callbacks.

- [ ] **Step 4: Full build and tests.** Run `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`. Expected: BUILD SUCCESSFUL, all tests pass. If `R.jar` is locked, stop and report it.

- [ ] **Step 5: Commit Tasks 17 and 18 together**

```powershell
git add app/src/main/java/com/noobexon/xposedfakelocation/manager/ui/map/MapScreen.kt app/src/main/java/com/noobexon/xposedfakelocation/manager/ui/navigation/NavGraph.kt app/src/main/java/com/noobexon/xposedfakelocation/manager/ui/favorites/FavoritesScreen.kt app/src/main/java/com/noobexon/xposedfakelocation/manager/ui/favorites/FavoritesViewModel.kt app/src/main/java/com/noobexon/xposedfakelocation/manager/ui/favorites/RouteFavorites.kt
git commit -m "feat: add Route mode to the map and routes to favorites"
```

---

### Task 19: Align the spec, then verify on a device

**Files:**
- Modify: `docs/superpowers/specs/2026-10-25-custom-route-design.md`

- [ ] **Step 1: Record the implementation decisions in the spec.**
  - In `## Route engine` → `RouteSession`, replace the "Exposed StateFlows" bullet with: "**Exposed state:** a single `status: StateFlow<RouteStatus>` (phase, progress, speed, current point number, wait remaining, distance covered and total, multiplier). The multiplier cycles 0.5x → 1x → 2x via `cycleMultiplier()`."
  - In `## Map screen (Route mode)`, replace the "Route panel (`BottomSheetScaffold`)" heading text with "**Route panel** (a collapsible card at the bottom of the map)". Then replace the "Play button" bullet's first sentence with: "In Route mode the floating Play button is hidden so it can't cover the panel; Play/Stop sits in the panel header and is enabled when the route has at least 2 points and the joystick is not running."
  - In the "Editing gestures" list, change "Tap a marker: menu with …" to "Tap a marker: bottom sheet with …".
  - In the segment sheet line, add "(5 % steps)" after "variation slider, 0–50 %".
  - In `RoutePlayer` → Running, add: "The playback multiplier also shortens waits."

- [ ] **Step 2: Commit**

```powershell
git add docs/superpowers/specs/2026-10-25-custom-route-design.md
git commit -m "docs: align custom route spec with implementation"
```

- [ ] **Step 3: Device checklist for the user.** No code here; hand this list to the user.
  1. **Editing.** Switch to Route mode. Tap to add 4 points, drag one, tap one and Move earlier, Set number, and Delete. Reorder in the panel list by dragging the handle.
  2. **Segment settings.** Tap a segment, set Bike, a 10 s wait and 20 % variation.
  3. **Playback.** Turn on Loop and Reverse, then press Play.
     - The overlay appears, and the target app shows movement.
     - The speed varies around the Bike speed, and point 2 waits for 10 s.
     - Pause, resume, seek by dragging the bar, cycle 0.5x/1x/2x, and minimize to the bubble.
  4. **Notification.** Pause/Resume and Stop work, and Stop turns spoofing off.
  5. **One-time end.** Turn Loop off and let the route finish. It stays at the last point and shows "Finished"; Replay restarts it.
  6. **Favorites.** Save the route, change it, and use Load from favorites. You should get "Replace current route?". Rename and delete it in Favorites → Routes.
  7. **Exclusivity.** While a route plays, the joystick button and the mode switch are disabled. While the joystick runs, route Play shows "Close the joystick first".
  8. **Backup.** Export settings, delete the route, import, and the route comes back.
  9. **Arrows and fit.** Arrow direction matches travel direction. Loading a favorite frames the whole route.
