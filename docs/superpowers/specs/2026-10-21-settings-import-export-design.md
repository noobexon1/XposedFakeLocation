# Settings Import / Export — Design

**Date:** 2026-10-21
**Status:** Approved (pending implementation plan)

## Problem

Users lose their configured data when moving between app versions (e.g. reinstall / sideload a
new build). There is no way to carry a setup forward. We want an **import/export settings**
feature so a user can save their configuration to a portable file and restore it later or on
another device.

## Goals

- Export the user's meaningful configuration to a single portable file.
- Import that file later to restore the configuration, replacing what is currently on the device.
- Survive uninstall/reinstall and version upgrades.
- Require no extra runtime storage permissions on modern Android.

## Non-goals

- Automatic/cloud backup (Android Auto Backup) — explicitly out of scope.
- Partial/selective import UI (pick-and-choose sections). Import is all-or-nothing (replace).
- Sharing configs between users as a first-class flow (a saved file can still be shared manually).

## Decisions (from brainstorming)

1. **Scope of exported data:** spoofing settings + favorites + app preferences (theme/language).
2. **Import semantics:** **replace** — the file fully overwrites current settings (favorites and
   target apps included).
3. **Mechanism:** Storage Access Framework (SAF) file picker + a versioned JSON document.
4. **Module-not-bound handling:** **lenient / partial** (see below).

## Data model & JSON schema

A new Gson-serialized model `SettingsBackup` with a top-level `version` to allow future migration.

```json
{
  "version": 1,
  "exportedAt": 1760000000000,
  "appVersion": "1.2.3",
  "spoofing": {
    "useRandomize": false, "randomizeRadius": 0.0,
    "useAccuracy": false, "accuracy": 0.0,
    "useAltitude": false, "altitude": 0.0,
    "useVerticalAccuracy": false, "verticalAccuracy": 0.0,
    "useMeanSeaLevel": false, "meanSeaLevel": 0.0,
    "useMeanSeaLevelAccuracy": false, "meanSeaLevelAccuracy": 0.0,
    "useSpeed": false, "speed": 0.0,
    "useSpeedAccuracy": false, "speedAccuracy": 0.0,
    "hideFakeLocationToast": false,
    "targetApps": ["com.example.app"],
    "wifiIdentityEnabled": false,
    "wifiSsid": "AndroidAP",
    "wifiBssid": "02:00:00:00:00:00",
    "wifiRssi": -60
  },
  "favorites": [
    { "name": "Home", "latitude": 0.0, "longitude": 0.0, "description": "" }
  ],
  "appearance": { "themeOption": "", "languageTag": "" }
}
```

- `exportedAt` and `appVersion` are informational only (diagnostics / display).
- **Excluded** (intentionally not exported or imported): `is_playing`, `last_clicked_location`,
  `map_zoom` (transient UI state), `enable_system_hooks` and `enable_broadcast_control`
  (device-specific; the former triggers a live Xposed scope change + reboot).
- **Version handling:** importing a file whose `version != 1` is rejected with a clear error.
  A migration hook is reserved for future versions but not implemented now.

## Components

### `SettingsBackup` (data/model)

Immutable data classes mirroring the JSON above (`SettingsBackup`, `SpoofingBackup`,
`AppearanceBackup`; favorites reuse the existing `FavoriteLocation` model).

### `SettingsBackupManager` (new, `data/backup`)

Owns serialization and the settings↔JSON mapping so `PreferencesRepository` stays focused.

- `exportToJson(): String` — snapshot current values via the repository's existing getters and
  serialize. Spoofing values require the remote prefs (module service) to be bound; see below.
- `importFromJson(json: String): Result<SettingsBackup>` — parse + validate `version`; all parsing
  wrapped in `runCatching`. Returns a typed failure for malformed/unsupported input.
- `apply(backup: SettingsBackup, canWriteRemote: Boolean)` — write values through existing `save*`
  setters using **replace** semantics. Favorites and target apps are replaced wholesale. When
  `canWriteRemote` is false, only the local parts (favorites + appearance) are applied.

Depends on: `PreferencesRepository`, `Gson`.

### `PreferencesRepository` (existing, small addition)

Add `suspend fun replaceFavorites(list: List<FavoriteLocation>)` (the repo currently exposes only
add/remove/update). Reuse existing getters/setters for everything else.

### `SettingsViewModel` (existing)

Add export/import entry points that delegate to `SettingsBackupManager`. Outcomes are surfaced as
one-shot events via a `Channel` (mirroring the existing `SystemHooksEvent` pattern):

- `ExportSuccess`, `ImportSuccess`
- `ImportPartial` (local applied, remote skipped because module not bound)
- `InvalidFile` (malformed / unsupported version)
- `Error(message)`
- `ModuleNotActive` (export blocked)

## UI

Two new items in the Settings top-bar overflow menu, beside "Reset all settings":
**Export settings** and **Import settings**.

- **Export:** `ActivityResultContracts.CreateDocument("application/json")`, default filename
  `xposedfakelocation-settings-YYYYMMDD.json`. Write via `contentResolver.openOutputStream(uri)`.
- **Import:** `ActivityResultContracts.OpenDocument(arrayOf("application/json"))`. Read via
  `contentResolver.openInputStream(uri)`, parse + validate, then show a **confirmation dialog**
  ("Replace all current settings with this file?") before applying (consistent with the existing
  reset-all dialog). On confirm, apply with replace semantics.

All results produce a snackbar via the one-shot event stream.

## Module-active (remote prefs) handling — lenient/partial

Spoofing settings live in LSPosed remote prefs, readable/writable only while the module's
`XposedService` is bound (e.g. not bound right after a fresh install, before the module is enabled
in LSPosed).

- **Export:** if the service is **not** bound, block export and show a "module not active" snackbar
  (otherwise the spoofing snapshot would just be defaults).
- **Import:** always apply the local parts (favorites + appearance) immediately. Apply spoofing
  parts only when the service is bound. If not bound, emit `ImportPartial` →
  snackbar: "Imported favorites & appearance. Enable the module in LSPosed, then import again to
  restore spoofing settings." When bound, everything applies in one pass (`ImportSuccess`).

## Error handling & validation

- Non-JSON / wrong shape / unsupported `version` / IO failure → specific snackbar (`InvalidFile`
  or `Error`); nothing is applied on a parse/validation failure.
- Individual corrupt favorite entries are skipped rather than failing the whole import.
- On apply, Wi-Fi SSID/BSSID/RSSI pass through the existing normalize/coerce helpers
  (`normalizeWifiSsid`, BSSID regex/uppercase, RSSI `coerceIn`).

## Testing

Unit tests target `SettingsBackupManager` (pure Kotlin; SAF launchers are not unit-tested):

- Export → import round-trip produces an equal `SettingsBackup`.
- Unsupported `version` is rejected.
- Malformed / non-JSON input is handled without throwing.
- Corrupt favorite entries are skipped while valid ones import.

## Strings / i18n

New strings in `values`, `values-de`, `values-zh`:

- Menu labels: export / import settings.
- Import confirmation dialog title + message.
- Snackbars: export success, import success, import partial (module inactive), invalid file,
  generic error, module-not-active (export blocked).

## Open questions

None. Ready for implementation planning.
