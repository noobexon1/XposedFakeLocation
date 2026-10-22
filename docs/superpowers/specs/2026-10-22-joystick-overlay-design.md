# Joystick Overlay — Design Spec

**Date:** 2026-10-22
**Status:** Approved design, pending implementation plan

## Goal

Let the user steer the spoofed location manually with a floating joystick drawn over any
app (e.g. while a target app is in the foreground). Movement must look realistic to the
target app: position changes continuously, and reported bearing/speed match the movement.

## Decisions

| Topic | Decision |
|---|---|
| Motion model | Two modes, chosen in Settings ("Constant speed" switch): **Dynamic** (speed ∝ stick deflection, up to a max) and **Constant** (fixed preset speed; stick sets direction only). |
| Placement | System overlay (`TYPE_APPLICATION_OVERLAY`) hosted by a foreground service in the manager app. Not shown in the map screen itself. |
| Overlay contents | Joystick pad, collapse-to-bubble, close. Draggable. Mode and preset are chosen in Settings. |
| Lifecycle | Launched from the map screen; requires spoofing ON and a marker set. Starts from the current marker. Moving updates the persisted marker. Closing keeps the last position; spoofing state is untouched. |
| Realism | While active: bearing = movement direction, speed = actual movement speed (0 when idle). Settings that interfere with motion are paused (see below). |
| Architecture | Manager-side simulation; position written to remote prefs (throttled). Hook-side extrapolation was rejected for complexity/robustness. |

### Settings paused while the joystick is active

- Randomize within radius (`useRandomize`, `randomizeRadius`)
- Custom speed (`useSpeed`, `speed`)
- Custom speed accuracy (`useSpeedAccuracy`, `speedAccuracy`)

Kept as configured (they don't interfere with motion): accuracy, altitude, vertical accuracy,
mean sea level, mean sea level accuracy.

## Components

### 1. `JoystickOverlayService` (`manager/joystick/`)

- Foreground service, type `specialUse`. Persistent notification with a **Stop joystick** action.
- Adds a `ComposeView` to `WindowManager` as `TYPE_APPLICATION_OVERLAY`. The service implements
  `LifecycleOwner` + `SavedStateRegistryOwner` and sets them on the view tree so Compose works
  outside an Activity.
- Owns the movement loop and the preference writer.
- Observes `getIsPlayingFlow()` and `getLastClickedLocationFlow()`; stops itself when spoofing is
  turned off or the marker is cleared.
- Re-starting while already running is idempotent: it re-shows / expands the existing overlay.

### 2. Overlay UI (Compose, `manager/joystick/ui/`)

- `JoystickPad`: circular base + thumb; emits a normalized vector `(x, y)` in `[-1, 1]`
  (clamped to the unit circle); thumb snaps back to centre on release.
- Collapse button (shrinks to a small bubble; tap bubble to expand) and close button.
- A drag handle moves the window by updating `WindowManager.LayoutParams.x/y`. The position is
  clamped to screen bounds and persisted in local prefs so it is restored next time.

### 3. `MovementEngine` (pure Kotlin, no Android deps)

```kotlin
data class MovementInput(val x: Float, val y: Float)          // y up = north
enum class JoystickMode { DYNAMIC, CONSTANT }
data class MovementConfig(val mode: JoystickMode, val maxSpeedMps: Float, val constantSpeedMps: Float)
data class MovementState(val latitude: Double, val longitude: Double, val bearingDeg: Float, val speedMps: Float)

fun step(state: MovementState, input: MovementInput, config: MovementConfig, dtSeconds: Double): MovementState
```

- Dead zone: magnitude < 0.15 → speed 0, position unchanged, bearing keeps its last value.
- Dynamic: `speed = maxSpeedMps * ((magnitude - deadZone) / (1 - deadZone))`.
- Constant: `speed = constantSpeedMps` whenever outside the dead zone.
- Bearing: `atan2(x, y)` in degrees, normalized to `[0, 360)` (up = 0°, right = 90°).
- New position via `GeoMath.destination(lat, lon, bearing, speed * dt)`.

### 4. `GeoMath` (shared, `data/GeoMath.kt`)

- `destination(lat, lon, bearingDeg, distanceMeters): Pair<Double, Double>` using the
  great-circle destination formula with `RADIUS_EARTH`. Latitude clamped to `[-90, 90]`,
  longitude wrapped to `[-180, 180)`.
- `LocationUtil.getRandomLocation` is refactored to use it (behaviour unchanged).

### 5. Joystick state (remote prefs)

- New model `JoystickState(active: Boolean, bearing: Float, speed: Float, updatedAt: Long)`
  stored as JSON under new key `KEY_JOYSTICK_STATE`.
- `PreferencesRepository`: `saveJoystickState(state)` and `saveJoystickFrame(lat, lon, state)`
  (marker + state in one edit). There is no `clearJoystickState()`: stopping writes an inactive
  state, and clearing the marker writes only the state. Marker writes are serialized by a
  process-wide lock, and a frame never re-creates a marker that was cleared.
- `PreferencesUtil`: `getJoystickState(): JoystickState?`.
- Position continues to use the existing `KEY_LAST_CLICKED_LOCATION`.

### 6. Hook-side override

- New pure function in `xposed/hooks/JoystickHookPolicy.kt` (mirrors `WifiIdentityHookPolicy`):
  `fun activeState(state: JoystickState?, nowMillis: Long): JoystickState?` — returns the state
  only if `active` and `abs(nowMillis - updatedAt) <= JOYSTICK_STALE_MS` (5000 ms). Staleness is
  symmetric, so a state timestamped in the future (clock skew) is also rejected.
- `LocationUtil.updateLocation()`:
  - If an active joystick state exists: skip randomize (use exact marker coordinates), ignore
    custom speed / speed accuracy, set `speed = state.speed`, `bearing = state.bearing`,
    `speedAccuracy = JOYSTICK_SPEED_ACCURACY` (small constant, e.g. 0.5 m/s).
  - Otherwise: current behaviour.
- `LocationUtil` gains `bearing` and an explicit override flag (`motionOverride: Boolean`).
  `createFakeLocation` applies speed/bearing/speedAccuracy when `motionOverride` is true **even
  when speed is 0**. This fixes the existing "0 means not overridden" semantics for speed,
  which would otherwise leak the real speed when the stick is idle.

### 7. Joystick settings (local prefs, manager-only)

| Key | Default |
|---|---|
| `KEY_JOYSTICK_MODE` | `DYNAMIC` |
| `KEY_JOYSTICK_MAX_SPEED` | 3.0 m/s |
| `KEY_JOYSTICK_PRESET_WALK` | 1.4 m/s |
| `KEY_JOYSTICK_PRESET_RUN` | 3.0 m/s |
| `KEY_JOYSTICK_PRESET_BIKE` | 5.5 m/s |
| `KEY_JOYSTICK_PRESET_DRIVE` | 14.0 m/s |
| `KEY_JOYSTICK_SELECTED_PRESET` | `WALK` |
| `KEY_JOYSTICK_OVERLAY_X/Y` | unset (default placement bottom-start) |

- New "Joystick" section in the Settings screen for the mode ("Constant speed" switch), the selected preset (shown only in Constant mode), max speed and the four presets
  (with per-row reset, following the existing pattern). Speeds shown in km/h in the UI, stored in m/s.
- The mode and selected preset chosen in the overlay are persisted, so the overlay reopens in the
  same configuration.

### 8. Entry point (Map screen)

- A joystick action button on the map screen, enabled only when `isPlaying && lastClickedLocation != null`.
- On tap:
  1. If `!Settings.canDrawOverlays(context)`: show an explanation dialog → open
     `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` for this package; re-check on return.
  2. On API 33+, request `POST_NOTIFICATIONS` if not granted (denial is non-blocking).
  3. If `App.service == null`: show a toast and do not start.
  4. `startForegroundService(JoystickOverlayService)`.

### 9. Manifest

```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<service
    android:name=".manager.joystick.JoystickOverlayService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Floating joystick overlay for manual control of the spoofed location" />
</service>
```

`startForeground` is called with `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE` on API 34+.

## Data Flow & Timing

1. **Start:** service reads marker + joystick settings, adds overlay, writes
   `JoystickState(active = true, speed = 0, bearing = last or 0, updatedAt = now)`.
2. **Tick loop** (coroutine, ~50 ms / 20 Hz): reads latest stick vector, calls
   `MovementEngine.step` with real elapsed `dt` (monotonic clock), updates in-memory state.
3. **Writer** (throttled): writes `KEY_LAST_CLICKED_LOCATION` + `KEY_JOYSTICK_STATE` at most every
   250 ms when state changed; immediately on stick release (speed 0); heartbeat every 2 s while
   idle so the 5 s staleness guard stays satisfied.
4. **Hooks:** unchanged pull model — every intercept calls `updateLocation()`, which now also reads
   the joystick state.
5. **Map screen:** marker follows automatically via the existing `getLastClickedLocationFlow()`.
6. **Stop** (close button, notification action, spoofing off, marker cleared, `onDestroy`):
   `JoystickSession.stop()` guarantees no publish runs after it returns, so the final write is
   last. Then write `JoystickState(active = false, …)`, remove overlay, stop foreground. The final
   position is written together with that state only if the marker still exists; when the marker
   was cleared, only the inactive state is written. Last position stays as the marker. If the
   process is killed without cleanup, the staleness guard makes hooks fall back to normal settings
   within 5 s.

**Threading:** remote-pref writes go through XposedService IPC, so they run on `Dispatchers.IO`
through a conflated channel (single writer, only the latest value is written). UI thread never blocks.

## Edge Cases

- **Marker changed externally while active** (map tap, Go to point, favorite, External Control
  broadcast): if the observed marker differs from the last value the service wrote, adopt it as
  the new position (teleport) and continue.
- **Poles / antimeridian:** handled by `GeoMath` clamp/wrap.
- **Rotation / screen size change:** overlay position re-clamped to the new bounds.
- **Settings changed while active:** service observes joystick setting flows; new values apply on
  the next tick.
- **XposedService dies mid-session:** writes are dropped by the repository; the service stops
  itself when `App.serviceState` becomes null.

## Backup / Localization

- `SettingsBackup` gains a nullable `joystick: JoystickBackup?` section (mode, max speed, presets,
  selected preset). Nullable keeps `SETTINGS_BACKUP_VERSION = 1` compatible: older files without
  the section import fine and leave joystick settings untouched. Overlay position is not exported.
- All new strings added in `values/`, `values-de/`, `values-zh/`.
- The overlay service follows the app's language (`LocaleController.attachBaseContext`) and theme
  option (`ThemeOption.isDark()`, shared with `MainActivity`).

## Testing

**Unit (JUnit):**
- `MovementEngineTest`: dead zone, Dynamic linear scaling, Constant preset speed, bearing
  (up = 0°, right = 90°, down-left = 225°), zero `dt` → no movement.
- `GeoMathTest`: 100 m north ≈ +0.000899° latitude; east movement scales with `cos(lat)`;
  pole clamp and antimeridian wrap.
- `JoystickHookPolicyTest`: active+fresh → returned; inactive → null; stale or future-dated (> 5 s either way) → null; null → null.
- `SettingsBackupCodecTest`: round-trip with joystick section; import of a file without it.

**Manual on-device:**
- Spoof ON, open joystick over a target app, walk a loop; map marker follows.
- GPS test app shows bearing matching stick direction and speed matching mode/preset.
- Release stick → reported speed 0, position stable (no randomize jitter even if randomize enabled).
- Toggle spoofing off from the map → overlay closes.
- Force-stop manager app while moving → hooks revert to normal settings within ~5 s.

## Out of Scope

- Mini-map, coordinates readout, or start/stop spoofing inside the overlay.
- Route/waypoint playback.
- Joystick on the in-app map screen.
- Hook-side position extrapolation.
- Changes to the External Control broadcast API.
