# Settings Import / Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users export their configuration to a portable JSON file and import it back (replacing current settings) so configs survive uninstall/reinstall and version upgrades.

**Architecture:** A pure-Kotlin `SettingsBackupCodec` owns JSON (de)serialization + validation and is unit-tested without Android. An Android `SettingsBackupManager` maps between the codec model and the existing `PreferencesRepository`. `SettingsViewModel` adds `exportTo(uri)` / `importFrom(uri)` that do stream IO off the main thread and emit one-shot `SettingsBackupEvent`s. `SettingsScreen` adds two overflow-menu items wired to Storage Access Framework launchers plus an import confirmation dialog and snackbars.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), AndroidX ViewModel, Kotlin Coroutines (`Channel`/`StateFlow`), Gson, Android Storage Access Framework (`ActivityResultContracts`), JUnit4.

## Global Constraints

- Module package: `com.noobexon.xposedfakelocation`; backup code lives under `data/backup`, model under `data/model`.
- Backup JSON `version` floor/current = `1`; importing any other `version` is rejected (no migration implemented yet).
- Spoofing settings live in LSPosed **remote** prefs, only writable while `App.service != null`. Export is blocked when unbound; import applies local parts (favorites + appearance) always and spoofing parts only when bound.
- **Replace** semantics on import: favorites and target apps are overwritten wholesale.
- Excluded from backup entirely: `is_playing`, `last_clicked_location`, `map_zoom`, `enable_system_hooks`, `enable_broadcast_control`.
- All numeric/Wi-Fi values must pass through existing normalisation when written (`PreferencesRepository.saveWifiSsid/saveWifiBssid/saveWifiRssi/saveTargetApps` already normalise; call those).
- New user-facing strings must be added to `values`, `values-de`, and `values-zh`.
- Unit tests run on the JVM (no Robolectric). Only `SettingsBackupCodec` is unit-tested; Android-dependent classes are not.
- PowerShell shell: chain commands with `;`, never `&&`. Gradle wrapper is `.\gradlew.bat`.
- Follow the existing one-shot event pattern: a `Channel<...>(Channel.BUFFERED)` exposed as `receiveAsFlow()`, collected in the screen inside `repeatOnLifecycle(STARTED)` (mirror `SettingsViewModel.systemHooksEvents`).

---

### Task 1: Backup model + pure codec (serialize / validate)

**Files:**
- Create: `app/src/main/java/com/noobexon/xposedfakelocation/data/model/SettingsBackup.kt`
- Create: `app/src/main/java/com/noobexon/xposedfakelocation/data/backup/SettingsBackupCodec.kt`
- Test: `app/src/test/java/com/noobexon/xposedfakelocation/data/backup/SettingsBackupCodecTest.kt`

**Interfaces:**
- Consumes: existing `com.noobexon.xposedfakelocation.data.model.FavoriteLocation` (`data class FavoriteLocation(name: String, latitude: Double, longitude: Double, description: String = "")`).
- Produces:
  - `const val SETTINGS_BACKUP_VERSION = 1`
  - `data class SettingsBackup(version, exportedAt, appVersion, spoofing, favorites, appearance)`
  - `data class SpoofingBackup(...)`, `data class AppearanceBackup(themeOption, languageTag)`
  - `class SettingsBackupCodec { fun toJson(backup: SettingsBackup): String; fun fromJson(json: String): Result<SettingsBackup> }`
  - `fromJson` returns `Result.failure(SettingsBackupException)` for malformed JSON, missing root, or unsupported version; sanitises favorites (drops entries with blank name or non-finite coords).
  - `class SettingsBackupException(message: String) : Exception(message)`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/noobexon/xposedfakelocation/data/backup/SettingsBackupCodecTest.kt`:

```kotlin
package com.noobexon.xposedfakelocation.data.backup

import com.noobexon.xposedfakelocation.data.model.AppearanceBackup
import com.noobexon.xposedfakelocation.data.model.FavoriteLocation
import com.noobexon.xposedfakelocation.data.model.SETTINGS_BACKUP_VERSION
import com.noobexon.xposedfakelocation.data.model.SettingsBackup
import com.noobexon.xposedfakelocation.data.model.SpoofingBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupCodecTest {

    private val codec = SettingsBackupCodec()

    private fun sampleBackup() = SettingsBackup(
        version = SETTINGS_BACKUP_VERSION,
        exportedAt = 1_760_000_000_000L,
        appVersion = "1.2.3",
        spoofing = SpoofingBackup(
            useRandomize = true, randomizeRadius = 12.5,
            useAccuracy = true, accuracy = 5.0,
            useAltitude = false, altitude = 0.0,
            useVerticalAccuracy = false, verticalAccuracy = 0.0f,
            useMeanSeaLevel = false, meanSeaLevel = 0.0,
            useMeanSeaLevelAccuracy = false, meanSeaLevelAccuracy = 0.0f,
            useSpeed = true, speed = 3.5f,
            useSpeedAccuracy = false, speedAccuracy = 0.0f,
            hideFakeLocationToast = true,
            targetApps = listOf("com.example.a", "com.example.b"),
            wifiIdentityEnabled = true,
            wifiSsid = "MyNet", wifiBssid = "02:00:00:00:00:00", wifiRssi = -55,
        ),
        favorites = listOf(
            FavoriteLocation("Home", 40.0, -73.0, "desc"),
            FavoriteLocation("Work", 41.0, -74.0),
        ),
        appearance = AppearanceBackup(themeOption = "DARK", languageTag = "de"),
    )

    @Test
    fun roundTripProducesEqualBackup() {
        val original = sampleBackup()
        val restored = codec.fromJson(codec.toJson(original)).getOrThrow()
        assertEquals(original, restored)
    }

    @Test
    fun unsupportedVersionIsRejected() {
        val json = codec.toJson(sampleBackup()).replace(
            "\"version\":1", "\"version\":999"
        )
        assertTrue(codec.fromJson(json).isFailure)
    }

    @Test
    fun malformedJsonIsRejectedWithoutThrowing() {
        assertTrue(codec.fromJson("not valid json at all").isFailure)
        assertTrue(codec.fromJson("").isFailure)
        assertTrue(codec.fromJson("{}").isFailure)
    }

    @Test
    fun corruptFavoritesAreSkipped() {
        val json = """
            {"version":1,"exportedAt":0,"appVersion":"x",
             "spoofing":{"useRandomize":false,"randomizeRadius":0.0,"useAccuracy":false,"accuracy":0.0,
               "useAltitude":false,"altitude":0.0,"useVerticalAccuracy":false,"verticalAccuracy":0.0,
               "useMeanSeaLevel":false,"meanSeaLevel":0.0,"useMeanSeaLevelAccuracy":false,"meanSeaLevelAccuracy":0.0,
               "useSpeed":false,"speed":0.0,"useSpeedAccuracy":false,"speedAccuracy":0.0,
               "hideFakeLocationToast":false,"targetApps":[],"wifiIdentityEnabled":false,
               "wifiSsid":"AndroidAP","wifiBssid":"02:00:00:00:00:00","wifiRssi":-60},
             "favorites":[{"name":"Good","latitude":1.0,"longitude":2.0,"description":""},
                          {"name":"","latitude":3.0,"longitude":4.0,"description":""}],
             "appearance":{"themeOption":"","languageTag":""}}
        """.trimIndent()
        val restored = codec.fromJson(json).getOrThrow()
        assertEquals(1, restored.favorites.size)
        assertEquals("Good", restored.favorites.first().name)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.noobexon.xposedfakelocation.data.backup.SettingsBackupCodecTest" --console=plain`
Expected: FAIL — compilation error, `SettingsBackup` / `SettingsBackupCodec` unresolved.

- [ ] **Step 3: Write the model file**

Create `app/src/main/java/com/noobexon/xposedfakelocation/data/model/SettingsBackup.kt`:

```kotlin
package com.noobexon.xposedfakelocation.data.model

/** Current on-disk schema version for exported settings. Bump only with a migration. */
const val SETTINGS_BACKUP_VERSION = 1

/** Root of an exported settings file. */
data class SettingsBackup(
    val version: Int,
    val exportedAt: Long,
    val appVersion: String,
    val spoofing: SpoofingBackup,
    val favorites: List<FavoriteLocation>,
    val appearance: AppearanceBackup,
)

/** All spoofing-related values (LSPosed remote prefs). */
data class SpoofingBackup(
    val useRandomize: Boolean,
    val randomizeRadius: Double,
    val useAccuracy: Boolean,
    val accuracy: Double,
    val useAltitude: Boolean,
    val altitude: Double,
    val useVerticalAccuracy: Boolean,
    val verticalAccuracy: Float,
    val useMeanSeaLevel: Boolean,
    val meanSeaLevel: Double,
    val useMeanSeaLevelAccuracy: Boolean,
    val meanSeaLevelAccuracy: Float,
    val useSpeed: Boolean,
    val speed: Float,
    val useSpeedAccuracy: Boolean,
    val speedAccuracy: Float,
    val hideFakeLocationToast: Boolean,
    val targetApps: List<String>,
    val wifiIdentityEnabled: Boolean,
    val wifiSsid: String,
    val wifiBssid: String,
    val wifiRssi: Int,
)

/** Manager-only appearance preferences (local prefs). */
data class AppearanceBackup(
    val themeOption: String,
    val languageTag: String,
)
```

- [ ] **Step 4: Write the codec file**

Create `app/src/main/java/com/noobexon/xposedfakelocation/data/backup/SettingsBackupCodec.kt`:

```kotlin
package com.noobexon.xposedfakelocation.data.backup

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.noobexon.xposedfakelocation.data.model.SETTINGS_BACKUP_VERSION
import com.noobexon.xposedfakelocation.data.model.SettingsBackup

/** Thrown (wrapped in [Result]) when a settings file cannot be parsed or is unsupported. */
class SettingsBackupException(message: String) : Exception(message)

/**
 * Pure-Kotlin JSON (de)serialization and validation for [SettingsBackup]. Holds no Android
 * dependencies so it can be unit-tested directly.
 */
class SettingsBackupCodec(
    private val gson: Gson = GsonBuilder().create()
) {

    fun toJson(backup: SettingsBackup): String = gson.toJson(backup)

    /**
     * Parses [json] into a [SettingsBackup]. Returns [Result.failure] with a
     * [SettingsBackupException] for malformed input, a missing root object, or an unsupported
     * [SettingsBackup.version]. Favorites with a blank name or non-finite coordinates are dropped
     * rather than failing the whole import.
     */
    fun fromJson(json: String): Result<SettingsBackup> = runCatching {
        val parsed = gson.fromJson(json, SettingsBackup::class.java)
            ?: throw SettingsBackupException("Empty or non-object settings file")
        if (parsed.version != SETTINGS_BACKUP_VERSION) {
            throw SettingsBackupException("Unsupported settings version: ${parsed.version}")
        }
        if (parsed.spoofing == null || parsed.appearance == null) {
            throw SettingsBackupException("Settings file is missing required sections")
        }
        val favorites = (parsed.favorites ?: emptyList()).filter { fav ->
            fav.name.isNotBlank() &&
                fav.latitude.isFinite() &&
                fav.longitude.isFinite()
        }
        parsed.copy(favorites = favorites)
    }
}
```

Note: `parsed.spoofing`/`parsed.appearance` are declared non-null in Kotlin, but Gson can leave
them null when absent from the JSON; the null checks above are deliberate and the `== null`
comparisons are correct despite the non-null types.

- [ ] **Step 5: Run tests to verify they pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.noobexon.xposedfakelocation.data.backup.SettingsBackupCodecTest" --console=plain`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/noobexon/xposedfakelocation/data/model/SettingsBackup.kt app/src/main/java/com/noobexon/xposedfakelocation/data/backup/SettingsBackupCodec.kt app/src/test/java/com/noobexon/xposedfakelocation/data/backup/SettingsBackupCodecTest.kt
git commit -m "feat: add settings backup model and codec"
```

---

### Task 2: `PreferencesRepository.replaceFavorites`

**Files:**
- Modify: `app/src/main/java/com/noobexon/xposedfakelocation/data/repository/PrefrencesRepository.kt` (Favorites region, around line 363-405)

**Interfaces:**
- Consumes: existing private `saveFavorites(favorites: List<FavoriteLocation>)`.
- Produces: `suspend fun replaceFavorites(favorites: List<FavoriteLocation>)` — overwrites the entire favorites list.

- [ ] **Step 1: Add the method**

In `PrefrencesRepository.kt`, inside the `// region Favorites (local)` block, immediately after `updateFavorite(...)` (before `fun getFavorites()`), add:

```kotlin
    /** Replaces the entire favorites list (used by settings import). */
    suspend fun replaceFavorites(favorites: List<FavoriteLocation>) {
        saveFavorites(favorites)
        Log.d(tag, "Replaced favorites with ${favorites.size} entries")
    }
```

- [ ] **Step 2: Verify it compiles**

Run: `.\gradlew.bat :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```powershell
git add app/src/main/java/com/noobexon/xposedfakelocation/data/repository/PrefrencesRepository.kt
git commit -m "feat: add replaceFavorites to PreferencesRepository"
```

---

### Task 3: `SettingsBackupManager` (snapshot + apply)

**Files:**
- Create: `app/src/main/java/com/noobexon/xposedfakelocation/data/backup/SettingsBackupManager.kt`

**Interfaces:**
- Consumes: `PreferencesRepository` getters (`getUseRandomize()`, `getRandomizeRadius()`, `getUseAccuracy()`, `getAccuracy()`, `getUseAltitude()`, `getAltitude()`, `getUseVerticalAccuracy()`, `getVerticalAccuracy()`, `getUseMeanSeaLevel()`, `getMeanSeaLevel()`, `getUseMeanSeaLevelAccuracy()`, `getMeanSeaLevelAccuracy()`, `getUseSpeed()`, `getSpeed()`, `getUseSpeedAccuracy()`, `getSpeedAccuracy()`, `getHideFakeLocationToast()`, `getTargetAppsFlow()`→use a new sync read, `getEnableWifiIdentity()`, Wi-Fi flows) and setters (`saveUseRandomize`…`saveSpeedAccuracy`, `saveHideFakeLocationToast`, `saveTargetApps`, `saveEnableWifiIdentity`, `saveWifiSsid`, `saveWifiBssid`, `saveWifiRssi`, `replaceFavorites`, `saveThemeOption`, `saveLanguageTag`); `SettingsBackupCodec`; `SettingsBackup`/`SpoofingBackup`/`AppearanceBackup`.
- Produces:
  - `class SettingsBackupManager(repository, codec = SettingsBackupCodec())`
  - `fun exportToJson(appVersion: String): String`
  - `fun parse(json: String): Result<SettingsBackup>`
  - `suspend fun apply(backup: SettingsBackup, canWriteRemote: Boolean)` — always applies favorites + appearance; applies spoofing only when `canWriteRemote`.

**Note on sync reads:** `PreferencesRepository` exposes sync getters for every remote spoofing value except target apps and Wi-Fi (those are flow-only). To avoid widening the repo surface further, read Wi-Fi values from the UI state at call time instead — see Task 4 (the ViewModel passes a fully-populated `SpoofingBackup` built from `uiState`). Therefore **`SettingsBackupManager` does not read the repository for export**; it only serialises a `SettingsBackup` the caller supplies, and applies a `SettingsBackup` on import. Adjust the produced interface accordingly:
  - `fun toJson(backup: SettingsBackup): String`
  - `fun parse(json: String): Result<SettingsBackup>`
  - `suspend fun apply(backup: SettingsBackup, canWriteRemote: Boolean)`

- [ ] **Step 1: Write the file**

Create `app/src/main/java/com/noobexon/xposedfakelocation/data/backup/SettingsBackupManager.kt`:

```kotlin
package com.noobexon.xposedfakelocation.data.backup

import com.noobexon.xposedfakelocation.data.model.SettingsBackup
import com.noobexon.xposedfakelocation.data.repository.PreferencesRepository

/**
 * Maps between [SettingsBackup] and the live [PreferencesRepository]. Serialization/validation is
 * delegated to [SettingsBackupCodec]; this class owns the write-back ("apply") policy, including
 * the remote-vs-local split.
 */
class SettingsBackupManager(
    private val repository: PreferencesRepository,
    private val codec: SettingsBackupCodec = SettingsBackupCodec(),
) {

    /** Serialises an already-built [backup] to JSON. */
    fun toJson(backup: SettingsBackup): String = codec.toJson(backup)

    /** Parses and validates [json]; see [SettingsBackupCodec.fromJson]. */
    fun parse(json: String): Result<SettingsBackup> = codec.fromJson(json)

    /**
     * Writes [backup] into the repository with **replace** semantics.
     *
     * Favorites and appearance (local prefs) are always applied. Spoofing values (remote prefs)
     * are applied only when [canWriteRemote] is `true`; otherwise they are skipped so the caller
     * can tell the user to re-import once the module is active.
     */
    suspend fun apply(backup: SettingsBackup, canWriteRemote: Boolean) {
        // Local parts — always safe to write.
        repository.replaceFavorites(backup.favorites)
        repository.saveThemeOption(backup.appearance.themeOption)
        repository.saveLanguageTag(backup.appearance.languageTag)

        if (!canWriteRemote) return

        val s = backup.spoofing
        repository.saveUseRandomize(s.useRandomize)
        repository.saveRandomizeRadius(s.randomizeRadius)
        repository.saveUseAccuracy(s.useAccuracy)
        repository.saveAccuracy(s.accuracy)
        repository.saveUseAltitude(s.useAltitude)
        repository.saveAltitude(s.altitude)
        repository.saveUseVerticalAccuracy(s.useVerticalAccuracy)
        repository.saveVerticalAccuracy(s.verticalAccuracy)
        repository.saveUseMeanSeaLevel(s.useMeanSeaLevel)
        repository.saveMeanSeaLevel(s.meanSeaLevel)
        repository.saveUseMeanSeaLevelAccuracy(s.useMeanSeaLevelAccuracy)
        repository.saveMeanSeaLevelAccuracy(s.meanSeaLevelAccuracy)
        repository.saveUseSpeed(s.useSpeed)
        repository.saveSpeed(s.speed)
        repository.saveUseSpeedAccuracy(s.useSpeedAccuracy)
        repository.saveSpeedAccuracy(s.speedAccuracy)
        repository.saveHideFakeLocationToast(s.hideFakeLocationToast)
        repository.saveTargetApps(s.targetApps.toSet())
        repository.saveEnableWifiIdentity(s.wifiIdentityEnabled)
        repository.saveWifiSsid(s.wifiSsid)
        repository.saveWifiBssid(s.wifiBssid)
        repository.saveWifiRssi(s.wifiRssi)
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `.\gradlew.bat :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```powershell
git add app/src/main/java/com/noobexon/xposedfakelocation/data/backup/SettingsBackupManager.kt
git commit -m "feat: add SettingsBackupManager apply/serialize layer"
```

---

### Task 4: ViewModel export/import logic + events

**Files:**
- Modify: `app/src/main/java/com/noobexon/xposedfakelocation/manager/ui/settings/SettingsViewModel.kt`

**Interfaces:**
- Consumes: `SettingsBackupManager`, `SettingsBackup`, `SpoofingBackup`, `AppearanceBackup`, existing `uiState: StateFlow<SettingsUiState>`, `App.service`, `BuildConfig.VERSION_NAME`, `getTargetApps` (read from repository flow once). Target apps are not in `SettingsUiState`; read them via a new private sync read.
- Produces:
  - `sealed interface SettingsBackupEvent { ExportSuccess; ImportSuccess; ImportPartial; InvalidFile; ModuleNotActive; data class Error(message) }`
  - `val settingsBackupEvents: Flow<SettingsBackupEvent>`
  - `fun exportTo(uri: Uri)`
  - `fun importFrom(uri: Uri)`

**Target-apps note:** `PreferencesRepository` only exposes `getTargetAppsFlow()`. Add a sync getter to the repository in this task (small, one line) so the ViewModel can read the current set without collecting a flow.

- [ ] **Step 1: Add a sync target-apps getter to the repository**

In `PrefrencesRepository.kt`, inside `// region Target Apps (remote)`, after `saveTargetApps(...)`, add:

```kotlin
    fun getTargetApps(): Set<String> = parseTargetApps(remotePrefs()?.getString(KEY_TARGET_APPS, null))
```

- [ ] **Step 2: Add imports to SettingsViewModel.kt**

At the top of `SettingsViewModel.kt`, add these imports (alongside the existing ones):

```kotlin
import android.net.Uri
import com.noobexon.xposedfakelocation.BuildConfig
import com.noobexon.xposedfakelocation.data.backup.SettingsBackupManager
import com.noobexon.xposedfakelocation.data.model.AppearanceBackup
import com.noobexon.xposedfakelocation.data.model.SETTINGS_BACKUP_VERSION
import com.noobexon.xposedfakelocation.data.model.SettingsBackup
import com.noobexon.xposedfakelocation.data.model.SpoofingBackup
```

- [ ] **Step 3: Declare the event type**

In `SettingsViewModel.kt`, directly below the existing `sealed interface SystemHooksEvent { ... }` block (before `private const val TAG`), add:

```kotlin
/**
 * One-shot outcomes of a settings export/import, consumed exactly once by the settings UI as a
 * snackbar. Delivered through a [kotlinx.coroutines.channels.Channel] so events survive
 * configuration changes without being replayed.
 */
sealed interface SettingsBackupEvent {
    /** Export wrote the file successfully. */
    data object ExportSuccess : SettingsBackupEvent

    /** Import applied everything (module was active, so spoofing settings were restored too). */
    data object ImportSuccess : SettingsBackupEvent

    /** Import applied favorites + appearance, but skipped spoofing because the module is inactive. */
    data object ImportPartial : SettingsBackupEvent

    /** The chosen file was not a valid/supported settings backup. */
    data object InvalidFile : SettingsBackupEvent

    /** Export was blocked because the Xposed module is not active (no [App.service]). */
    data object ModuleNotActive : SettingsBackupEvent

    /** An IO or unexpected error occurred. [message] is surfaced to the user. */
    data class Error(val message: String) : SettingsBackupEvent
}
```

- [ ] **Step 4: Add the manager, channel, and logic to the ViewModel**

In `SettingsViewModel.kt`, add the manager field next to `preferencesRepository` (just after line `private val preferencesRepository = PreferencesRepository(application)`):

```kotlin
    private val backupManager = SettingsBackupManager(preferencesRepository)
```

Then add this new region at the end of the class, immediately before the final closing brace (after `setEnableSystemHooks(...)`'s closing brace):

```kotlin
    // ---- Settings backup (export / import) ---------------------------------------------------

    private val _backupEvents = Channel<SettingsBackupEvent>(Channel.BUFFERED)

    /**
     * Stream of one-shot [SettingsBackupEvent]s produced by [exportTo] / [importFrom]. Collect
     * inside `repeatOnLifecycle(STARTED)`.
     */
    val settingsBackupEvents: Flow<SettingsBackupEvent> = _backupEvents.receiveAsFlow()

    private fun currentBackup(): SettingsBackup {
        val ui = uiState.value
        return SettingsBackup(
            version = SETTINGS_BACKUP_VERSION,
            exportedAt = System.currentTimeMillis(),
            appVersion = BuildConfig.VERSION_NAME,
            spoofing = SpoofingBackup(
                useRandomize = ui.useRandomize,
                randomizeRadius = ui.randomizeRadius.toDouble(),
                useAccuracy = ui.useAccuracy,
                accuracy = ui.accuracy.toDouble(),
                useAltitude = ui.useAltitude,
                altitude = ui.altitude.toDouble(),
                useVerticalAccuracy = ui.useVerticalAccuracy,
                verticalAccuracy = ui.verticalAccuracy,
                useMeanSeaLevel = ui.useMeanSeaLevel,
                meanSeaLevel = ui.meanSeaLevel.toDouble(),
                useMeanSeaLevelAccuracy = ui.useMeanSeaLevelAccuracy,
                meanSeaLevelAccuracy = ui.meanSeaLevelAccuracy,
                useSpeed = ui.useSpeed,
                speed = ui.speed,
                useSpeedAccuracy = ui.useSpeedAccuracy,
                speedAccuracy = ui.speedAccuracy,
                hideFakeLocationToast = ui.hideFakeLocationToast,
                targetApps = preferencesRepository.getTargetApps().toList(),
                wifiIdentityEnabled = ui.wifiIdentityEnabled,
                wifiSsid = ui.wifiSsid,
                wifiBssid = ui.wifiBssid,
                wifiRssi = ui.wifiRssi,
            ),
            favorites = preferencesRepository.getFavorites(),
            appearance = AppearanceBackup(
                themeOption = ui.themeOption.tag,
                languageTag = ui.languageTag,
            ),
        )
    }

    /**
     * Serialises the current configuration to [uri]. Blocked (emits [SettingsBackupEvent.ModuleNotActive])
     * when the Xposed service is unbound, because spoofing values would just be defaults.
     */
    fun exportTo(uri: Uri) {
        if (App.service == null) {
            _backupEvents.trySend(SettingsBackupEvent.ModuleNotActive)
            return
        }
        viewModelScope.launch {
            try {
                val json = backupManager.toJson(currentBackup())
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw java.io.IOException("Could not open destination file")
                }
                _backupEvents.trySend(SettingsBackupEvent.ExportSuccess)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                _backupEvents.trySend(SettingsBackupEvent.Error(e.message ?: e.toString()))
            }
        }
    }

    /**
     * Reads, validates, and applies a settings file from [uri] with replace semantics. Local parts
     * (favorites + appearance) always apply; spoofing parts apply only when the module is active,
     * otherwise [SettingsBackupEvent.ImportPartial] is emitted.
     */
    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    } ?: throw java.io.IOException("Could not open source file")
                }
                val parsed = backupManager.parse(json)
                val backup = parsed.getOrElse {
                    Log.w(TAG, "Invalid settings file", it)
                    _backupEvents.trySend(SettingsBackupEvent.InvalidFile)
                    return@launch
                }
                val canWriteRemote = App.service != null
                backupManager.apply(backup, canWriteRemote)
                _backupEvents.trySend(
                    if (canWriteRemote) SettingsBackupEvent.ImportSuccess
                    else SettingsBackupEvent.ImportPartial
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
                _backupEvents.trySend(SettingsBackupEvent.Error(e.message ?: e.toString()))
            }
        }
    }
```

- [ ] **Step 5: Verify it compiles**

Run: `.\gradlew.bat :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/noobexon/xposedfakelocation/manager/ui/settings/SettingsViewModel.kt app/src/main/java/com/noobexon/xposedfakelocation/data/repository/PrefrencesRepository.kt
git commit -m "feat: add export/import logic and events to SettingsViewModel"
```

---

### Task 5: Settings screen UI (menu items, SAF launchers, dialog, snackbars)

**Files:**
- Modify: `app/src/main/java/com/noobexon/xposedfakelocation/manager/ui/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: `SettingsViewModel.exportTo(uri)`, `SettingsViewModel.importFrom(uri)`, `SettingsViewModel.settingsBackupEvents`, `SettingsBackupEvent`, new string resources from Task 6.
- Produces: two new overflow `DropdownMenuItem`s ("Export settings", "Import settings"), SAF launchers, an import confirmation dialog, and a backup-event collector.

**Design decisions (within approved scope):**
- Export uses `ActivityResultContracts.CreateDocument("application/json")` with suggested name `xposedfakelocation-settings-<yyyyMMdd>.json`.
- Import uses `ActivityResultContracts.OpenDocument(arrayOf("application/json"))`; after a file is picked, show the confirm dialog, and only call `importFrom(uri)` on confirm.
- Imported language applies on the next activity launch (no forced `recreate()` after import, so the result snackbar is not lost). Theme applies live via the existing theme flow.

- [ ] **Step 1: Add imports**

At the top of `SettingsScreen.kt`, add:

```kotlin
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.noobexon.xposedfakelocation.manager.ui.settings.SettingsBackupEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
```

(If `SettingsBackupEvent` is already resolvable because it is in the same package, omit that import.)

- [ ] **Step 2: Collect backup events in `SettingsScreen`**

In `SettingsScreen` (the stateful composable), extend the existing lifecycle collector `LaunchedEffect(lifecycleOwner, settingsViewModel) { ... }` by adding a second `launch`-style collector. Replace the existing block body so both flows are collected:

```kotlin
    LaunchedEffect(lifecycleOwner, settingsViewModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                settingsViewModel.systemHooksEvents.collect { event ->
                    when (event) {
                        is SystemHooksEvent.RestartRequired -> restartDialogEnabled = event.enabled
                        is SystemHooksEvent.ModuleNotActive ->
                            snackbarHostState.showSnackbar(context.getString(R.string.system_hooks_module_inactive))
                        is SystemHooksEvent.ScopeRequestFailed ->
                            snackbarHostState.showSnackbar(context.getString(R.string.system_hooks_scope_failed, event.message))
                    }
                }
            }
            launch {
                settingsViewModel.settingsBackupEvents.collect { event ->
                    val message = when (event) {
                        SettingsBackupEvent.ExportSuccess -> context.getString(R.string.settings_export_success)
                        SettingsBackupEvent.ImportSuccess -> context.getString(R.string.settings_import_success)
                        SettingsBackupEvent.ImportPartial -> context.getString(R.string.settings_import_partial)
                        SettingsBackupEvent.InvalidFile -> context.getString(R.string.settings_import_invalid)
                        SettingsBackupEvent.ModuleNotActive -> context.getString(R.string.settings_export_module_inactive)
                        is SettingsBackupEvent.Error -> context.getString(R.string.settings_backup_error, event.message)
                    }
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
    }
```

Add the needed import for `launch` if not present: `import kotlinx.coroutines.launch` (it is already imported — verify; if absent, add it).

- [ ] **Step 3: Create SAF launchers and pending-import state in `SettingsScreen`**

Immediately after `val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()` in `SettingsScreen`, add:

```kotlin
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) settingsViewModel.exportTo(uri) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) pendingImportUri = uri }

    val exportFileName = remember {
        val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        "xposedfakelocation-settings-$date.json"
    }
```

- [ ] **Step 4: Pass new callbacks into `SettingsContent`**

Add three parameters to the `SettingsContent(...)` call at the end of `SettingsScreen`:

```kotlin
    SettingsContent(
        categories = categories,
        snackbarHostState = snackbarHostState,
        restartDialogEnabled = restartDialogEnabled,
        onRestartDialogDismiss = { restartDialogEnabled = null },
        onBack = { navController.navigateUp() },
        onResetConfirmed = {
            settingsViewModel.resetToDefaults()
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.settings_reset_done))
            }
        },
        onExport = { exportLauncher.launch(exportFileName) },
        onImport = { importLauncher.launch(arrayOf("application/json")) }
    )
```

Then render the import confirmation dialog in `SettingsScreen` (after the `SettingsContent(...)` call, still inside `SettingsScreen`):

```kotlin
    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text(stringResource(R.string.settings_import_title)) },
            text = { Text(stringResource(R.string.settings_import_message)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingImportUri = null
                    settingsViewModel.importFrom(uri)
                }) {
                    Text(stringResource(R.string.action_import))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
```

`AlertDialog`, `TextButton`, `Text`, `stringResource` are already imported in this file.

- [ ] **Step 5: Add the parameters to `SettingsContent` and the menu items**

Change the `SettingsContent` signature to add the two callbacks (after `onResetConfirmed`):

```kotlin
private fun SettingsContent(
    categories: List<Pair<SettingsCategory, List<SettingEntry>>>,
    snackbarHostState: SnackbarHostState,
    restartDialogEnabled: Boolean?,
    onRestartDialogDismiss: () -> Unit,
    onBack: () -> Unit,
    onResetConfirmed: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
```

In the `DropdownMenu` block, add two items before the existing "Reset all settings" item:

```kotlin
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings_export)) },
                                onClick = {
                                    menuExpanded = false
                                    onExport()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings_import)) },
                                onClick = {
                                    menuExpanded = false
                                    onImport()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings_reset_all)) },
                                onClick = {
                                    menuExpanded = false
                                    showResetDialog = true
                                }
                            )
                        }
```

- [ ] **Step 6: Verify it compiles**

Run: `.\gradlew.bat :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL` (string resources land in Task 6; if run before Task 6, expect unresolved `R.string.*` — run Task 6 first or together).

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/java/com/noobexon/xposedfakelocation/manager/ui/settings/SettingsScreen.kt
git commit -m "feat: wire export/import UI into settings screen"
```

---

### Task 6: Strings (en / de / zh)

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-de/strings.xml`
- Modify: `app/src/main/res/values-zh/strings.xml`

**Interfaces:**
- Produces string keys used by Task 5: `settings_export`, `settings_import`, `settings_import_title`, `settings_import_message`, `action_import`, `settings_export_success`, `settings_import_success`, `settings_import_partial`, `settings_import_invalid`, `settings_export_module_inactive`, `settings_backup_error`.
- `action_cancel` already exists (reused). Verify `action_import` does not already exist; if it does, reuse it and skip adding.

- [ ] **Step 1: Add English strings**

In `app/src/main/res/values/strings.xml`, add (near the other `settings_*` entries):

```xml
    <string name="settings_export">Export settings</string>
    <string name="settings_import">Import settings</string>
    <string name="action_import">Import</string>
    <string name="settings_import_title">Import settings?</string>
    <string name="settings_import_message">Replace all current settings with this file? This cannot be undone.</string>
    <string name="settings_export_success">Settings exported</string>
    <string name="settings_import_success">Settings imported</string>
    <string name="settings_import_partial">Imported favorites &amp; appearance. Enable the module in LSPosed, then import again to restore spoofing settings.</string>
    <string name="settings_import_invalid">That file isn\'t a valid settings backup</string>
    <string name="settings_export_module_inactive">Enable the module in LSPosed before exporting spoofing settings</string>
    <string name="settings_backup_error">Couldn\'t complete that: %1$s</string>
```

- [ ] **Step 2: Add German strings**

In `app/src/main/res/values-de/strings.xml`, add:

```xml
    <string name="settings_export">Einstellungen exportieren</string>
    <string name="settings_import">Einstellungen importieren</string>
    <string name="action_import">Importieren</string>
    <string name="settings_import_title">Einstellungen importieren?</string>
    <string name="settings_import_message">Alle aktuellen Einstellungen durch diese Datei ersetzen? Dies kann nicht rückgängig gemacht werden.</string>
    <string name="settings_export_success">Einstellungen exportiert</string>
    <string name="settings_import_success">Einstellungen importiert</string>
    <string name="settings_import_partial">Favoriten &amp; Darstellung importiert. Aktiviere das Modul in LSPosed und importiere erneut, um die Spoofing-Einstellungen wiederherzustellen.</string>
    <string name="settings_import_invalid">Diese Datei ist keine gültige Einstellungssicherung</string>
    <string name="settings_export_module_inactive">Aktiviere das Modul in LSPosed, bevor du Spoofing-Einstellungen exportierst</string>
    <string name="settings_backup_error">Konnte nicht abgeschlossen werden: %1$s</string>
```

- [ ] **Step 3: Add Chinese strings**

In `app/src/main/res/values-zh/strings.xml`, add:

```xml
    <string name="settings_export">导出设置</string>
    <string name="settings_import">导入设置</string>
    <string name="action_import">导入</string>
    <string name="settings_import_title">导入设置？</string>
    <string name="settings_import_message">用此文件替换所有当前设置？此操作无法撤销。</string>
    <string name="settings_export_success">设置已导出</string>
    <string name="settings_import_success">设置已导入</string>
    <string name="settings_import_partial">已导入收藏夹和外观。请在 LSPosed 中启用模块，然后重新导入以恢复伪装设置。</string>
    <string name="settings_import_invalid">该文件不是有效的设置备份</string>
    <string name="settings_export_module_inactive">导出伪装设置前，请先在 LSPosed 中启用模块</string>
    <string name="settings_backup_error">无法完成：%1$s</string>
```

- [ ] **Step 4: Verify the full debug build compiles (merges resources + Kotlin)**

Run: `.\gradlew.bat :app:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/res/values/strings.xml app/src/main/res/values-de/strings.xml app/src/main/res/values-zh/strings.xml
git commit -m "feat: add import/export strings for en, de, zh"
```

---

## Self-Review

**1. Spec coverage**
- Versioned JSON schema → Task 1 (`SettingsBackup`, `version=1`). ✓
- Version mismatch rejected → Task 1 (`unsupportedVersionIsRejected`). ✓
- Exclusions (`is_playing`, `last_clicked_location`, `map_zoom`, `enable_system_hooks`, `enable_broadcast_control`) → not present in model (Task 1) nor apply (Task 3). ✓
- `SettingsBackupManager` with serialize/parse/apply → Task 3. ✓
- `replaceFavorites` on repo → Task 2. ✓ (Target apps reuse existing `saveTargetApps`, plus a sync `getTargetApps` getter added in Task 4.)
- Export/import entry points + one-shot events → Task 4. ✓
- Overflow menu items + SAF + import confirm dialog → Task 5. ✓
- Replace semantics → Task 3 `apply` (favorites/targetApps overwritten). ✓
- Module-not-bound (export blocked; import partial) → Task 4 (`ModuleNotActive`, `ImportPartial`). ✓
- Error/validation handling → Task 1 (codec) + Task 4 (`InvalidFile`/`Error`). ✓
- Wi-Fi/target-app normalisation on apply → Task 3 calls `saveWifiSsid/saveWifiBssid/saveWifiRssi/saveTargetApps` which normalise. ✓
- Unit tests on the mapping/serialization layer → Task 1 (codec tests: round-trip, unsupported version, malformed, corrupt-favorite skip). ✓
- Strings en/de/zh → Task 6. ✓
- **Deviation from spec (documented):** the spec described export as reading values via the repository inside `SettingsBackupManager`; because Wi-Fi/target-app values are flow-only on the repo, the ViewModel instead builds the `SpoofingBackup` from `uiState` + two sync getters and hands it to the manager to serialize. Same data, cleaner. The corrupt-favorite skip moved from the manager to the codec so it stays unit-testable without Android.

**2. Placeholder scan** — No TBD/TODO/"handle errors"/"similar to Task N" placeholders; every code step shows complete code. ✓

**3. Type consistency**
- `SettingsBackupCodec.fromJson(): Result<SettingsBackup>` used identically in Task 3 (`parse`) and Task 4. ✓
- `SettingsBackupManager.toJson/parse/apply` signatures match between Task 3 (definition) and Task 4 (calls: `toJson(currentBackup())`, `parse(json)`, `apply(backup, canWriteRemote)`). ✓
- `SettingsBackupEvent` variants emitted in Task 4 exactly match the `when` arms in Task 5. ✓
- `SpoofingBackup` field names/types match between model (Task 1), codec test (Task 1), and `currentBackup()` (Task 4): doubles = randomizeRadius/accuracy/altitude/meanSeaLevel; floats = verticalAccuracy/meanSeaLevelAccuracy/speed/speedAccuracy; `wifiRssi: Int`. ✓
- `AppearanceBackup(themeOption, languageTag)` consistent across Tasks 1/3/4. ✓
- `PreferencesRepository.replaceFavorites` (Task 2) and `getTargetApps` (Task 4) match their call sites in Tasks 3/4. ✓

No issues found.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-10-21-settings-import-export.md`. Two execution options:

1. **Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.
2. **Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
