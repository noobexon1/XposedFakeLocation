# Custom Route — Design Spec

**Date:** 2026-10-25
**Status:** Approved design, pending implementation plan

## Goal

Add a third mode of operation next to single-location spoofing and the joystick: the user builds a
route by placing ordered points on the map, and the app moves the spoofed location along it at
realistic speeds. Movement must look real to target apps: the position changes continuously, and
the reported bearing and speed match the movement.

## Decisions

| Topic | Decision |
|---|---|
| Where routes are edited | On the existing map screen. A Location / Route switch floats under the app bar; Route mode edits the route draft. |
| Path shape | Straight lines between consecutive points (offline, no routing service). Users add points for curves. |
| Speed | A route-wide default speed, plus optional per-segment overrides. Speeds are a preset (Walk / Run / Bike / Drive, resolved from the Settings joystick preset speeds at play time) or a custom km/h value. |
| Segment options | Tap a segment on the map to set: speed override, wait time at its end point, and speed variation (± %). |
| Point editing | Tap the map to append; long-press-drag a marker to move it; tap a marker for Delete / Move earlier / Move later / Set number…; reorder and delete in the panel's point list. |
| Loop | Closed circuit: a closing segment from the last point back to point 1, with its own settings. |
| Reverse | A direction toggle saved with the route. It does not renumber points. |
| One-time route end | Stay spoofed at the last point with speed 0; the overlay shows "Finished" with Replay. |
| Start / stop | One tap on the map's Play button (Route mode, at least 2 points) turns spoofing on, jumps to the start point and begins playback. Stop ends the route and turns spoofing off. |
| Playback controls | Notification (Pause/Resume, Stop) plus a new draggable overlay styled like a music player: play/pause, stop, a seekable progress bar, a speed multiplier and a km/h readout. |
| Favorites | The Favorites screen gets two tabs: Locations / Routes. Routes are included in the settings backup. |
| Exclusivity | Strict. The joystick and route playback can't run together, and the mode switch is locked while anything plays. |
| Extras in v1 | Total distance and estimated duration; the draft survives app restarts; a 0.5x / 1x / 2x playback multiplier. |
| Architecture | A new `RoutePlaybackService` in the manager runs a pure-Kotlin route engine and publishes through the joystick's existing pipeline. Hooks are unchanged. Hook-side route playback and merging with the joystick service were rejected (complexity and risk to the shipped joystick). |

## Data model (`data/model/Route.kt`)

```kotlin
data class Route(
    val id: String,                             // UUID
    val name: String,
    val points: List<RoutePoint>,               // list order = travel order (1..N)
    val segments: Map<String, SegmentSettings>, // key = segmentKey(fromId, toId); missing = defaults
    val defaultSpeed: SpeedChoice,
    val loop: Boolean,                          // adds a closing segment N -> 1
    val reversed: Boolean,                      // play N -> 1 without renumbering
)

data class RoutePoint(val id: String, val latitude: Double, val longitude: Double)

data class SegmentSettings(
    val speed: SpeedChoice? = null, // null = route default
    val dwellSeconds: Int = 0,      // wait at the end you arrive at, in the current direction
    val variationPercent: Int = 0,  // speed jitter of +/- N %
)

sealed interface SpeedChoice {
    data class Preset(val preset: JoystickPreset) : SpeedChoice
    data class Custom(val speedMps: Float) : SpeedChoice
}
```

- **Segment identity.** A segment's settings belong to the unordered pair of point IDs it connects,
  so `segmentKey(a, b) == segmentKey(b, a)`. That way the same settings apply in reverse.
  - When points are reordered or deleted, a segment whose two points are still neighbors keeps its
    settings. New neighbor pairs start with the defaults.
  - Moving a point keeps its ID, so it keeps its settings.
  - Settings for pairs that are no longer neighbors are pruned on save.
- **Limits.** Speed 1–200 km/h (the existing `JOYSTICK_MIN/MAX_SPEED_MPS`), wait 0–3600 s,
  variation 0–50 %, at least 2 points to play or save.
- **Default route speed:** `SpeedChoice.Preset(WALK)`.
- **Serialization.** `SpeedChoice` is written as `{"type":"preset","preset":"WALK"}` or
  `{"type":"custom","speedMps":4.2}`, using a small Gson adapter in the codec.

## Storage

Gson JSON in the local SharedPreferences, following the favorites pattern. None of this is written to
remote prefs; the hooks never need routes.

| Key | Contents |
|---|---|
| `KEY_ROUTES` | `List<Route>`: saved routes (Favorites → Routes tab). |
| `KEY_ROUTE_DRAFT` | `RouteDraft(route: Route, sourceRouteId: String?, dirty: Boolean)`: the route currently being edited, saved on every edit. `sourceRouteId` is the saved route it was loaded from (if any); `dirty` means it has edits not saved to that route. |
| `KEY_MAP_MODE` | `LOCATION` or `ROUTE`: the last selected map mode. |
| `KEY_ROUTE_OVERLAY_X/Y` | Saved position of the route overlay. |

`PreferencesRepository` gets: `getRoutesFlow`, `saveRoute` (insert or replace by `id`),
`deleteRoute`, `replaceRoutes`, `getRouteDraftFlow`, `saveRouteDraft`, and the map-mode getters and
setters.

**Backup.** `SettingsBackup` gets an optional `routes: List<Route>?` section, the same pattern as the
optional `joystick` section. `SETTINGS_BACKUP_VERSION` stays 1, so older backups import unchanged. The
codec drops routes with fewer than 2 points, non-finite coordinates, a blank name, or out-of-range
values. Importing replaces the saved routes; the draft is not part of the backup.

## Route engine (`manager/route/`, pure Kotlin)

### `RoutePlan`

`RoutePlan.from(route, joystickSettings)` builds an ordered list of `Leg`s:

- Each `Leg` holds the start and end coordinates, distance (m), initial bearing (°), resolved base
  speed (m/s), variation percentage and wait time at its end (s).
- **Speed resolution:** the segment's override, else the route default. A preset maps to the
  current `JoystickSettings` preset speed; the result is clamped to the speed limits.
- **Reversed:** the point order is flipped before legs are built.
- **Loop:** adds the closing leg (last point → first point).
- **Totals:** `totalDistanceM`, and `estimatedDurationS` (sum of distance / base speed, plus waits;
  ignores variation and the multiplier).
- Distance and initial bearing use great-circle formulas added to `GeoMath` next to `destination`.
  Positions along a leg come from `GeoMath.destination(start, bearing, offset)`.

### `RoutePlayer`

`RoutePlayer.step(state, plan, dtS, multiplier, random): RouteStep`. `RouteStep` holds the new state
plus the latitude, longitude, bearing and speed to publish.

- **State:** leg index, distance into the leg, wait time remaining, current variation factor and
  its target, and a phase: `RUNNING`, `PAUSED` or `FINISHED`.
- **Running:**
  - Advances by `baseSpeed × variationFactor × multiplier × dt`. Leftover distance carries over into
    the next leg in the same step, so long steps never overshoot.
  - When a leg ends, the leg's wait starts. While waiting, the position holds at the leg's end with
    speed 0 and bearing kept from the leg.
  - After the last leg: a one-time route becomes `FINISHED` at the last point with speed 0. A loop
    wraps to leg 0.
  - The playback multiplier also shortens waits.
- **Variation:** every 3–6 s a new target factor is drawn uniformly in `[1 − v, 1 + v]`. The factor
  eases toward the target at most 10 % per second. The random source is injected so tests are
  deterministic.
- **Seek:** `seek(state, plan, fraction)` maps a fraction of `totalDistanceM` to a leg and offset,
  cancels any wait, and keeps the phase. Seeking a finished route to below 1.0 sets it to `PAUSED`.
- **Progress:** distance covered / `totalDistanceM`. It is distance-based, so the bar stays exact
  under variation and the multiplier. For a looping route it is progress within the current lap.

### `RouteSession` (mirrors `JoystickSession`)

- **Tick:** every 100 ms on the service's main-thread scope, with injected clocks for tests.
- **Publishing:** writes follow `JoystickWritePolicy`. Every 250 ms while moving, immediately on a
  moving-to-stationary transition, and a 2 s heartbeat while stationary.
  - Frames go through a conflated channel to `repository.saveJoystickFrame(lat, lon, JoystickState(...))`
    on the IO dispatcher, as the joystick does.
  - Echoes of its own writes are recognized with `RecentPositions`.
- **Stationary states:** while paused, waiting or finished, the session publishes speed 0 as an
  active state. Target apps then see a stationary position, not the user's custom speed settings.
- **Controls:** `play()`, `pause()`, `seek(fraction)`, `setMultiplier(0.5 | 1 | 2)`, `replay()`.
- **Stop:** `stop()` cancels the tick and returns a final inactive frame, so the hooks resume normal
  behavior at once.
- **Exposed state:** a single `status: StateFlow<RouteStatus>` (phase, progress, speed, current point
  number, wait remaining, distance covered and total, multiplier). The multiplier cycles
  0.5x → 1x → 2x via `cycleMultiplier()`.
- **Publish failures** are caught and logged; the session keeps running.

## Service (`manager/route/RoutePlaybackService.kt`)

- **Type:** a foreground `LifecycleService` of type `specialUse`, declared in the manifest like the
  joystick service.
- **Notification:** its own channel, "Route playback". Content: the route name, percent progress and
  point x/N. Actions: Pause/Resume and Stop.
- **Intents:** `ACTION_START` carries the route ID or uses the draft. The service reads the route from
  the repository, not from the intent extras. The other intents are `ACTION_PAUSE`, `ACTION_RESUME`
  and `ACTION_STOP`.
- **Start preconditions:**
  - The LSPosed service is bound.
  - The route has at least 2 points.
  - The joystick is not running.
  If any of these fails, the service shows a toast and stops itself. Overlay permission is optional:
  without it the route plays with notification controls only.
- **Start sequence:**
  1. Write the start point as the marker.
  2. `saveIsPlaying(true)`.
  3. Start the session.
  4. Show the overlay.
- **Stop conditions:**
  - Spoofing is turned off elsewhere (map button, broadcast).
  - The marker is cleared.
  - The marker changes to a position that is not the session's own echo (favorite, broadcast).
    External input wins, and the route stops.
  - The Stop action or the overlay's Stop button.
- **On stop:** write the final inactive frame, remove the overlay, stop the foreground service, and
  clear `RoutePlaybackState`.
  - A stop from the user's Stop control also calls `saveIsPlaying(false)`.
  - A stop caused by spoofing already being off or the marker being cleared leaves those as they are.
- **`RoutePlaybackState`:** a process-wide holder (an `object` with a `StateFlow<RoutePlaybackStatus?>`)
  that publishes running/paused/finished, progress and route ID to the app UI. The joystick gets
  an equivalent `JoystickRunningState` flag, so each feature can disable the other's start control.
  `JoystickOverlayService` also adds "no route playing" to its start preconditions. This is the only
  change to joystick behavior.

### Shared overlay plumbing

The window code in `JoystickOverlayWindow` is extracted into a shared `OverlayWindowHost`. That covers
adding the `ComposeView` as `TYPE_APPLICATION_OVERLAY`, setting the lifecycle owners, dragging,
clamping to the screen, and saving the position under keys supplied by the caller. The joystick
overlay is moved onto it with no behavior change.

## Overlay (`manager/route/ui/RouteOverlay.kt`)

A compact draggable card, styled like a music-player mini controller:

- **Top row:** drag handle, route name, status ("Point 3/6" or "Waiting 0:24"), minimize.
- **Middle:** a `Slider` for progress, with "1.1 / 2.4 km" beneath it. While dragging, the slider
  shows where it will land without moving the location; `seek` is applied on release.
- **Bottom row:**
  - Play/Pause, and Replay when finished
  - Stop
  - a multiplier chip that cycles 0.5x → 1x → 2x
  - a live km/h readout using `displayKmh`
- **Minimized:** a small bubble with a route icon and a circular progress ring; tap to expand.
- **Finished:** the status reads "Finished", and Play becomes Replay.
- **Recomposition:** high-frequency values (progress, speed) are passed as lambdas, as in
  `JoystickOverlay`.

## Map screen (Route mode)

- **Mode switch:** a `SingleChoiceSegmentedButtonRow` (Location / Route) floating at the top of the
  map. It is disabled while spoofing is active or a route is playing. The selected mode is saved.
- **Location mode:** unchanged. The route draft is hidden in this mode.
- **Route overlays on the map (osmdroid):**
  - **Point markers:** numbered circular markers (1..N), with the start and end in distinct colors.
    The start is point 1, or N if reversed.
  - **Segments:** a `Polyline` per segment, with direction arrows. The closing segment is dashed
    when the route loops.
  - **Live position:** the moving position marker is shown while playing.
- **Editing gestures** (only in Route mode and while not playing):
  - Tap empty map: append a point.
  - Long-press-drag a marker: move the point (osmdroid draggable marker).
  - Tap a marker: bottom sheet with Delete, Move earlier, Move later, Set number… (a dialog that moves the
    point to a given position).
  - Tap a segment: segment sheet (`ModalBottomSheet`):
    - speed chips: Default / Walk / Run / Bike / Drive / Custom (km/h field)
    - wait at end point, in seconds
    - variation slider, 0–50 % (5 % steps)
- **Route panel** (a collapsible card at the bottom of the map):
  - **Collapsed:** "6 points · 2.4 km · ~28 min", the default speed chip (opens a picker with the same
    choices), and Loop and Reverse toggles.
  - **Expanded:**
    - the point list: a drag handle to reorder, the point number and coordinates, and a delete
      button
    - menu items: Save to favorites, Load from favorites, Clear route
  - **While playing:** read-only, with a progress bar.
- **Play button:** In Route mode the floating Play button is hidden so it can't cover the panel;
  Play/Stop sits in the panel header and is enabled when the route has at least 2 points and the
  joystick is not running. One tap starts `RoutePlaybackService` (overlay permission and notification permission are
  requested the same way `JoystickButton` does). While a route plays, the button is Stop.
  If spoofing was turned on some other way (for example by a broadcast) while the map is in Route
  mode, the button is Stop and turns spoofing off. It does not start the route until spoofing is off.
- **Joystick button:** disabled while a route is playing.
- **Draft editing logic:** append, delete, move, reorder, set number, toggles and segment edits are
  pure functions in `RouteDraftEditor`. `MapViewModel` (or a dedicated `RouteEditorViewModel` scoped
  like `MapViewModel`) calls them and saves the result.

## Favorites screen

- Tabs: **Locations** (existing list, unchanged) and **Routes**.
- **Route card:** name, point count, total distance, Loop/Reverse badges, plus Rename and Delete
  (with confirmation, reusing the existing dialogs).
- **Tapping a route:**
  1. If the current draft is `dirty` and has points, confirm "Replace current route?".
  2. Load the route into the draft.
  3. Switch the map to Route mode and fit the map to the route.
  Playback doesn't start.
- **Saving from the map:** "Save to favorites" asks for a name.
  - If the draft was loaded from a saved route, the dialog offers **Update** (same `id`) or **Save as
    new** (new `id`).
  - This uses `RouteDraft.sourceRouteId`. Saving either way clears `RouteDraft.dirty`.

## Strings

All new strings go in `values/`, `values-de/` and `values-zh/strings.xml`. They cover the mode
switch, panel, segment sheet, marker menu, overlay, notification, favorites tabs, dialogs and content
descriptions.

## Error handling

- **Fewer than 2 points:** Play and Save are disabled, with a hint ("Add at least 2 points").
- **Zero-length segments** (duplicate points): allowed. The leg completes instantly, and its wait
  still applies.
- **Invalid stored data:** an invalid draft or saved route from storage or a backup is dropped
  (logged), and the draft falls back to empty.
- **LSPosed service lost during playback:** the service stops cleanly and shows a toast.
- **Process death:** the draft is saved on every edit and survives. The playback position is not
  restored.

## Testing (JUnit 4, same style as the joystick tests)

- **`RoutePlanTest`:** leg building, distances and bearings, reverse, the loop's closing leg, speed
  resolution (override, default, preset from settings, clamping), and estimated duration.
- **`RoutePlayerTest`:**
  - distance advanced per step, and carrying leftover distance across legs
  - waits hold position at speed 0
  - one-time finish, and loop wrap
  - seek to a fraction, including cancelling a wait
  - the multiplier
  - with a seeded random source, the variation factor stays within ±v and changes smoothly
- **`RouteSessionTest`:** publishing cadence while moving and the stationary heartbeat. Pause,
  waiting and finished publish speed 0 as active. Stop returns an inactive frame.
- **`RouteDraftEditorTest`:** append, delete, move, reorder and set number. Segment settings survive
  reorders for surviving neighbor pairs and are pruned otherwise.
- **`GeoMathTest`:** the new distance and initial-bearing helpers.
- **`SettingsBackupCodecTest`:**
  - routes round-trip, including both `SpeedChoice` types
  - invalid routes are dropped
  - a backup without `routes` still imports

## Out of scope (v1)

Snapping to roads, undo/redo, GPX import/export, inserting a point in the middle of a segment,
restoring the playback position after process death, and per-point (rather than per-segment) settings.
