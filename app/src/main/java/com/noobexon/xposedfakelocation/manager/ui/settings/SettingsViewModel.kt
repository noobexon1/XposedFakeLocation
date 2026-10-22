package com.noobexon.xposedfakelocation.manager.ui.settings

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.noobexon.xposedfakelocation.BuildConfig
import com.noobexon.xposedfakelocation.data.backup.SettingsBackupManager
import com.noobexon.xposedfakelocation.data.model.AppearanceBackup
import com.noobexon.xposedfakelocation.data.model.JoystickBackup
import com.noobexon.xposedfakelocation.data.model.JoystickPreset
import com.noobexon.xposedfakelocation.data.model.JoystickSettings
import com.noobexon.xposedfakelocation.data.model.SETTINGS_BACKUP_VERSION
import com.noobexon.xposedfakelocation.data.model.SettingsBackup
import com.noobexon.xposedfakelocation.data.model.SpoofingBackup
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noobexon.xposedfakelocation.data.DEFAULT_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_ALTITUDE
import com.noobexon.xposedfakelocation.data.DEFAULT_ENABLE_BROADCAST_CONTROL
import com.noobexon.xposedfakelocation.data.DEFAULT_ENABLE_SYSTEM_HOOKS
import com.noobexon.xposedfakelocation.data.DEFAULT_ENABLE_WIFI_IDENTITY
import com.noobexon.xposedfakelocation.data.DEFAULT_HIDE_FAKE_LOCATION_TOAST
import com.noobexon.xposedfakelocation.data.DEFAULT_JOYSTICK_MAX_SPEED
import com.noobexon.xposedfakelocation.data.DEFAULT_JOYSTICK_PRESET_BIKE
import com.noobexon.xposedfakelocation.data.DEFAULT_JOYSTICK_PRESET_DRIVE
import com.noobexon.xposedfakelocation.data.DEFAULT_JOYSTICK_PRESET_RUN
import com.noobexon.xposedfakelocation.data.DEFAULT_JOYSTICK_PRESET_WALK
import com.noobexon.xposedfakelocation.data.DEFAULT_LANGUAGE_TAG
import com.noobexon.xposedfakelocation.data.DEFAULT_MEAN_SEA_LEVEL
import com.noobexon.xposedfakelocation.data.DEFAULT_MEAN_SEA_LEVEL_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_RANDOMIZE_RADIUS
import com.noobexon.xposedfakelocation.data.DEFAULT_SPEED
import com.noobexon.xposedfakelocation.data.DEFAULT_SPEED_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_THEME_OPTION
import com.noobexon.xposedfakelocation.data.DEFAULT_USE_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_USE_ALTITUDE
import com.noobexon.xposedfakelocation.data.DEFAULT_USE_MEAN_SEA_LEVEL
import com.noobexon.xposedfakelocation.data.DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_USE_RANDOMIZE
import com.noobexon.xposedfakelocation.data.DEFAULT_USE_SPEED
import com.noobexon.xposedfakelocation.data.DEFAULT_USE_SPEED_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_USE_VERTICAL_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_VERTICAL_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_WIFI_BSSID
import com.noobexon.xposedfakelocation.data.DEFAULT_WIFI_RSSI
import com.noobexon.xposedfakelocation.data.DEFAULT_WIFI_SSID
import com.noobexon.xposedfakelocation.data.MAX_WIFI_RSSI
import com.noobexon.xposedfakelocation.data.MIN_WIFI_RSSI
import com.noobexon.xposedfakelocation.data.SYSTEM_HOOK_PACKAGES
import com.noobexon.xposedfakelocation.data.repository.PreferencesRepository
import com.noobexon.xposedfakelocation.manager.App
import com.noobexon.xposedfakelocation.manager.control.ControlReceiver
import com.noobexon.xposedfakelocation.manager.localization.LocaleController
import com.noobexon.xposedfakelocation.manager.ui.theme.ThemeOption
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One-shot messages emitted while toggling system-level hooks, consumed exactly once by the
 * settings UI (shown as a snackbar or restart dialog). Delivered through a
 * [kotlinx.coroutines.channels.Channel] so events are never dropped or replayed across
 * configuration changes.
 */
sealed interface SystemHooksEvent {
    /**
     * The scope change succeeded and the device must be rebooted for it to take effect (or be
     * undone). The UI should show an informational dialog prompting the user to reboot.
     *
     * @property enabled `true` if hooks were just enabled, `false` if they were just disabled.
     */
    data class RestartRequired(val enabled: Boolean) : SystemHooksEvent

    /**
     * The Xposed module is not currently active (no [App.service] connection), so the scope cannot
     * be changed. The UI should inform the user that LSPosed must be active.
     */
    data object ModuleNotActive : SystemHooksEvent

    /**
     * The scope request was rejected by the service or threw an unexpected exception. The UI should
     * surface [message] so the user knows why the toggle did not apply.
     *
     * @property message Human-readable failure reason returned by the Xposed service.
     */
    data class ScopeRequestFailed(val message: String) : SystemHooksEvent
}

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

private const val TAG = "SettingsViewModel"

/**
 * Backs the settings screen. Exposes all spoofing and app preferences as a single [uiState]
 * [StateFlow] that the UI collects once, with per-preference setters that update state optimistically
 * and persist through [PreferencesRepository].
 *
 * All standard preferences are managed by the private [Preference] holder. System-level hooks are
 * the exception: they require a live Xposed service call that may fail or need a reboot, so their
 * outcome is surfaced as one-shot [SystemHooksEvent]s rather than an optimistic state update.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferencesRepository = PreferencesRepository(application)
    private val backupManager = SettingsBackupManager(preferencesRepository)

    /**
     * Holds a single preference value as a hot [StateFlow] and wires it to disk persistence.
     *
     * On creation the [flow] is collected for the ViewModel's lifetime so [state] always reflects
     * the repository's current value. [set] writes to [state] immediately (optimistic update) and
     * then fires a background coroutine to call [save], ensuring instant UI feedback without
     * blocking the main thread.
     *
     * @param T The preference value type (e.g. [Boolean], [Double], [Float], [String]).
     * @param initialValue Value emitted by [state] until the repository's first emission arrives.
     * @param flow Source-of-truth stream; its emissions overwrite [state].
     * @param save Suspending function that persists a new value to the repository.
     */
    private inner class Preference<T>(
        initialValue: T,
        flow: Flow<T>,
        private val save: suspend (T) -> Unit
    ) {
        private val _state = MutableStateFlow(initialValue)

        /**
         * The current preference value. Updated immediately by [set] for optimistic feedback, then
         * again when the repository confirms the write via [flow].
         */
        val state: StateFlow<T> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                flow.collect { _state.value = it }
            }
        }

        /**
         * Applies [value] to [state] immediately, then persists it asynchronously. Persistence
         * failures are logged but not rethrown — the in-memory state already reflects intent and
         * the user sees the correct value. [CancellationException] is rethrown to honour structured
         * concurrency.
         *
         * @param value The new value to apply and persist.
         */
        fun set(value: T) {
            _state.value = value
            viewModelScope.launch {
                try {
                    save(value)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to persist preference value: $value", e)
                }
            }
        }
    }

    // ---- Spoofing preferences ----------------------------------------------------------------

    private val _useAccuracy = Preference(
        DEFAULT_USE_ACCURACY,
        preferencesRepository.getUseAccuracyFlow(),
        preferencesRepository::saveUseAccuracy
    )
    private val _accuracy = Preference(
        DEFAULT_ACCURACY,
        preferencesRepository.getAccuracyFlow(),
        preferencesRepository::saveAccuracy
    )
    private val _useAltitude = Preference(
        DEFAULT_USE_ALTITUDE,
        preferencesRepository.getUseAltitudeFlow(),
        preferencesRepository::saveUseAltitude
    )
    private val _altitude = Preference(
        DEFAULT_ALTITUDE,
        preferencesRepository.getAltitudeFlow(),
        preferencesRepository::saveAltitude
    )
    private val _useRandomize = Preference(
        DEFAULT_USE_RANDOMIZE,
        preferencesRepository.getUseRandomizeFlow(),
        preferencesRepository::saveUseRandomize
    )
    private val _randomizeRadius = Preference(
        DEFAULT_RANDOMIZE_RADIUS,
        preferencesRepository.getRandomizeRadiusFlow(),
        preferencesRepository::saveRandomizeRadius
    )
    private val _useVerticalAccuracy = Preference(
        DEFAULT_USE_VERTICAL_ACCURACY,
        preferencesRepository.getUseVerticalAccuracyFlow(),
        preferencesRepository::saveUseVerticalAccuracy
    )
    private val _verticalAccuracy = Preference(
        DEFAULT_VERTICAL_ACCURACY,
        preferencesRepository.getVerticalAccuracyFlow(),
        preferencesRepository::saveVerticalAccuracy
    )
    private val _useMeanSeaLevel = Preference(
        DEFAULT_USE_MEAN_SEA_LEVEL,
        preferencesRepository.getUseMeanSeaLevelFlow(),
        preferencesRepository::saveUseMeanSeaLevel
    )
    private val _meanSeaLevel = Preference(
        DEFAULT_MEAN_SEA_LEVEL,
        preferencesRepository.getMeanSeaLevelFlow(),
        preferencesRepository::saveMeanSeaLevel
    )
    private val _useMeanSeaLevelAccuracy = Preference(
        DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY,
        preferencesRepository.getUseMeanSeaLevelAccuracyFlow(),
        preferencesRepository::saveUseMeanSeaLevelAccuracy
    )
    private val _meanSeaLevelAccuracy = Preference(
        DEFAULT_MEAN_SEA_LEVEL_ACCURACY,
        preferencesRepository.getMeanSeaLevelAccuracyFlow(),
        preferencesRepository::saveMeanSeaLevelAccuracy
    )
    private val _useSpeed = Preference(
        DEFAULT_USE_SPEED,
        preferencesRepository.getUseSpeedFlow(),
        preferencesRepository::saveUseSpeed
    )
    private val _speed = Preference(
        DEFAULT_SPEED,
        preferencesRepository.getSpeedFlow(),
        preferencesRepository::saveSpeed
    )
    private val _useSpeedAccuracy = Preference(
        DEFAULT_USE_SPEED_ACCURACY,
        preferencesRepository.getUseSpeedAccuracyFlow(),
        preferencesRepository::saveUseSpeedAccuracy
    )
    private val _speedAccuracy = Preference(
        DEFAULT_SPEED_ACCURACY,
        preferencesRepository.getSpeedAccuracyFlow(),
        preferencesRepository::saveSpeedAccuracy
    )

    // ---- Behaviour preferences ---------------------------------------------------------------

    private val _hideFakeLocationToast = Preference(
        DEFAULT_HIDE_FAKE_LOCATION_TOAST,
        preferencesRepository.getHideFakeLocationToastFlow(),
        preferencesRepository::saveHideFakeLocationToast
    )

    // ---- Wi-Fi identity ----------------------------------------------------------------------

    private val _enableWifiIdentity = Preference(
        DEFAULT_ENABLE_WIFI_IDENTITY,
        preferencesRepository.getEnableWifiIdentityFlow(),
        preferencesRepository::saveEnableWifiIdentity
    )
    private val _wifiSsid = Preference(
        DEFAULT_WIFI_SSID,
        preferencesRepository.getWifiSsidFlow(),
        preferencesRepository::saveWifiSsid
    )
    private val _wifiBssid = Preference(
        DEFAULT_WIFI_BSSID,
        preferencesRepository.getWifiBssidFlow(),
        preferencesRepository::saveWifiBssid
    )
    private val _wifiRssi = Preference(
        DEFAULT_WIFI_RSSI,
        preferencesRepository.getWifiRssiFlow(),
        preferencesRepository::saveWifiRssi
    )

    // ---- External control --------------------------------------------------------------------

    private val _enableBroadcastControl = Preference(
        DEFAULT_ENABLE_BROADCAST_CONTROL,
        preferencesRepository.getEnableBroadcastControlFlow(),
        preferencesRepository::saveEnableBroadcastControl
    )

    // ---- Joystick (local) --------------------------------------------------------------------

    /** Joystick configuration; speeds in m/s. */
    val joystickSettings: StateFlow<JoystickSettings> = preferencesRepository.getJoystickSettingsFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, preferencesRepository.getJoystickSettings())

    /** @param speedMps Dynamic-mode top speed in m/s. */
    fun setJoystickMaxSpeed(speedMps: Float) = preferencesRepository.saveJoystickMaxSpeed(speedMps)

    /** @param speedMps Constant-mode speed for [preset] in m/s. */
    fun setJoystickPresetSpeed(preset: JoystickPreset, speedMps: Float) =
        preferencesRepository.saveJoystickPresetSpeed(preset, speedMps)

    // ---- System hooks ------------------------------------------------------------------------

    /**
     * Whether the system framework packages ([SYSTEM_HOOK_PACKAGES]) are in the module's hook
     * scope. Unlike other preferences, this is a read-only view backed directly by the repository
     * — it is only updated once a scope change actually succeeds (see [setEnableSystemHooks]),
     * so the switch never flips optimistically on a failed service call.
     */
    val enableSystemHooks: StateFlow<Boolean> = preferencesRepository.getEnableSystemHooksFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_ENABLE_SYSTEM_HOOKS)

    private val _systemHooksEvents = Channel<SystemHooksEvent>(Channel.BUFFERED)

    /**
     * Stream of one-shot [SystemHooksEvent]s produced by [setEnableSystemHooks]. Collect this
     * inside `repeatOnLifecycle(STARTED)` to avoid consuming events while the screen is in the
     * background.
     */
    val systemHooksEvents: Flow<SystemHooksEvent> = _systemHooksEvents.receiveAsFlow()

    // ---- App preferences ---------------------------------------------------------------------

    private val _languageTag = Preference(
        DEFAULT_LANGUAGE_TAG,
        preferencesRepository.getLanguageTagFlow(),
        preferencesRepository::saveLanguageTag
    )

    private val _themeOption = Preference(
        DEFAULT_THEME_OPTION,
        preferencesRepository.getThemeOptionFlow(),
        preferencesRepository::saveThemeOption
    )

    // ---- UI state ----------------------------------------------------------------------------

    /**
     * Single atomic snapshot of every preference exposed to the settings UI. Combines all
     * individual [Preference] states and [enableSystemHooks] into one [StateFlow] so the screen
     * collects once and receives a complete, consistent update on every change.
     *
     * [Double]-typed repository values ([SettingsUiState.accuracy], [SettingsUiState.altitude],
     * [SettingsUiState.meanSeaLevel], [SettingsUiState.randomizeRadius]) are converted to [Float]
     * here, so the UI layer works uniformly in [Float] without per-field conversion at the
     * collection site.
     */
    val uiState: StateFlow<SettingsUiState> = combine(
        _useRandomize.state, _randomizeRadius.state,
        _useAccuracy.state, _accuracy.state,
        _useVerticalAccuracy.state
    ) { useRand, rand, useAcc, acc, useVert ->
        SettingsUiState(
            useRandomize = useRand,
            randomizeRadius = rand.toFloat(),
            useAccuracy = useAcc,
            accuracy = acc.toFloat(),
            useVerticalAccuracy = useVert,
        )
    }
        .combine(_verticalAccuracy.state)        { s, v -> s.copy(verticalAccuracy = v) }
        .combine(_useAltitude.state)             { s, v -> s.copy(useAltitude = v) }
        .combine(_altitude.state)                { s, v -> s.copy(altitude = v.toFloat()) }
        .combine(_useMeanSeaLevel.state)         { s, v -> s.copy(useMeanSeaLevel = v) }
        .combine(_meanSeaLevel.state)            { s, v -> s.copy(meanSeaLevel = v.toFloat()) }
        .combine(_useMeanSeaLevelAccuracy.state) { s, v -> s.copy(useMeanSeaLevelAccuracy = v) }
        .combine(_meanSeaLevelAccuracy.state)    { s, v -> s.copy(meanSeaLevelAccuracy = v) }
        .combine(_useSpeed.state)                { s, v -> s.copy(useSpeed = v) }
        .combine(_speed.state)                   { s, v -> s.copy(speed = v) }
        .combine(_useSpeedAccuracy.state)        { s, v -> s.copy(useSpeedAccuracy = v) }
        .combine(_speedAccuracy.state)           { s, v -> s.copy(speedAccuracy = v) }
        .combine(_hideFakeLocationToast.state)   { s, v -> s.copy(hideFakeLocationToast = v) }
        .combine(_enableBroadcastControl.state)  { s, v -> s.copy(enableBroadcastControl = v) }
        .combine(enableSystemHooks)              { s, v -> s.copy(systemHooksEnabled = v) }
        .combine(_enableWifiIdentity.state)      { s, v -> s.copy(wifiIdentityEnabled = v) }
        .combine(_wifiSsid.state)                { s, v -> s.copy(wifiSsid = v) }
        .combine(_wifiBssid.state)               { s, v -> s.copy(wifiBssid = v) }
        .combine(_wifiRssi.state)                { s, v -> s.copy(wifiRssi = v) }
        .combine(_languageTag.state)             { s, v -> s.copy(languageTag = v) }
        .combine(_themeOption.state)             { s, v -> s.copy(themeOption = ThemeOption.fromTag(v)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    // ---- Setters -----------------------------------------------------------------------------

    /**
     * Enables or disables custom horizontal accuracy reporting.
     * @param value `true` to include [SettingsUiState.accuracy] in each spoofed fix.
     */
    fun setUseAccuracy(value: Boolean) = _useAccuracy.set(value)

    /**
     * Sets the horizontal accuracy radius included in each spoofed fix.
     * @param value Accuracy in metres. Only applied when [SettingsUiState.useAccuracy] is `true`.
     */
    fun setAccuracy(value: Double) = _accuracy.set(value)

    /**
     * Enables or disables custom ellipsoidal altitude reporting.
     * @param value `true` to include [SettingsUiState.altitude] in each spoofed fix.
     */
    fun setUseAltitude(value: Boolean) = _useAltitude.set(value)

    /**
     * Sets the ellipsoidal altitude included in each spoofed fix.
     * @param value Altitude above the WGS84 ellipsoid in metres. Only applied when [SettingsUiState.useAltitude] is `true`.
     */
    fun setAltitude(value: Double) = _altitude.set(value)

    /**
     * Enables or disables per-fix randomisation around the chosen point.
     * @param value `true` to jitter each reported location within [SettingsUiState.randomizeRadius].
     */
    fun setUseRandomize(value: Boolean) = _useRandomize.set(value)

    /**
     * Sets the radius of the randomisation circle around the chosen point.
     * @param value Radius in metres. Only applied when [SettingsUiState.useRandomize] is `true`.
     */
    fun setRandomizeRadius(value: Double) = _randomizeRadius.set(value)

    /**
     * Enables or disables custom vertical accuracy reporting.
     * @param value `true` to include [SettingsUiState.verticalAccuracy] in each spoofed fix.
     */
    fun setUseVerticalAccuracy(value: Boolean) = _useVerticalAccuracy.set(value)

    /**
     * Sets the vertical accuracy included in each spoofed fix.
     * @param value Accuracy in metres. Only applied when [SettingsUiState.useVerticalAccuracy] is `true`.
     */
    fun setVerticalAccuracy(value: Float) = _verticalAccuracy.set(value)

    /**
     * Enables or disables custom mean-sea-level altitude reporting.
     * @param value `true` to include [SettingsUiState.meanSeaLevel] in each spoofed fix.
     */
    fun setUseMeanSeaLevel(value: Boolean) = _useMeanSeaLevel.set(value)

    /**
     * Sets the mean-sea-level altitude included in each spoofed fix.
     * @param value MSL altitude in metres. Only applied when [SettingsUiState.useMeanSeaLevel] is `true`.
     */
    fun setMeanSeaLevel(value: Double) = _meanSeaLevel.set(value)

    /**
     * Enables or disables custom MSL accuracy reporting.
     * @param value `true` to include [SettingsUiState.meanSeaLevelAccuracy] in each spoofed fix.
     */
    fun setUseMeanSeaLevelAccuracy(value: Boolean) = _useMeanSeaLevelAccuracy.set(value)

    /**
     * Sets the MSL accuracy included in each spoofed fix.
     * @param value Accuracy in metres. Only applied when [SettingsUiState.useMeanSeaLevelAccuracy] is `true`.
     */
    fun setMeanSeaLevelAccuracy(value: Float) = _meanSeaLevelAccuracy.set(value)

    /**
     * Enables or disables custom ground speed reporting.
     * @param value `true` to include [SettingsUiState.speed] in each spoofed fix.
     */
    fun setUseSpeed(value: Boolean) = _useSpeed.set(value)

    /**
     * Sets the ground speed included in each spoofed fix.
     * @param value Speed in metres per second. Only applied when [SettingsUiState.useSpeed] is `true`.
     */
    fun setSpeed(value: Float) = _speed.set(value)

    /**
     * Enables or disables custom speed accuracy reporting.
     * @param value `true` to include [SettingsUiState.speedAccuracy] in each spoofed fix.
     */
    fun setUseSpeedAccuracy(value: Boolean) = _useSpeedAccuracy.set(value)

    /**
     * Sets the speed accuracy included in each spoofed fix.
     * @param value Accuracy in metres per second. Only applied when [SettingsUiState.useSpeedAccuracy] is `true`.
     */
    fun setSpeedAccuracy(value: Float) = _speedAccuracy.set(value)

    /**
     * Suppresses or restores the per-fix toast shown by the Xposed hook.
     * @param value `true` to hide the toast; `false` to show it.
     */
    fun setHideFakeLocationToast(value: Boolean) = _hideFakeLocationToast.set(value)

    fun setEnableWifiIdentity(value: Boolean) = _enableWifiIdentity.set(value)

    fun setWifiSsid(value: String) =
        _wifiSsid.set(value.trim().ifBlank { DEFAULT_WIFI_SSID })

    fun setWifiBssid(value: String) =
        _wifiBssid.set(value.trim().ifBlank { DEFAULT_WIFI_BSSID })

    fun setWifiRssi(value: String) {
        val rssi = value.trim().toIntOrNull()?.coerceIn(MIN_WIFI_RSSI, MAX_WIFI_RSSI)
            ?: DEFAULT_WIFI_RSSI
        _wifiRssi.set(rssi)
    }

    /**
     * Applies the given [option] as the active UI theme. The preference is persisted locally and
     * reflected immediately in [SettingsUiState.themeOption]; [AppViewModel] observes the same
     * underlying flow so [MainActivity] recomposes with the new [darkTheme] without any Activity
     * recreation.
     *
     * @param option The [ThemeOption] to activate.
     */
    fun setTheme(option: ThemeOption) = _themeOption.set(option.tag)

    /**
     * Selects the UI language identified by [tag]. Persists the choice through both the normal
     * [PreferencesRepository] store and [LocaleController]'s synchronous store, which
     * `attachBaseContext` reads before this ViewModel is created. The caller is responsible for
     * recreating the Activity so the new locale takes effect immediately.
     *
     * @param tag BCP-47 language tag (e.g. `"en"`, `"zh"`), or an empty string to follow the
     *   system locale.
     */
    fun setLanguage(tag: String) {
        _languageTag.set(tag)
        LocaleController.persistLanguageTag(getApplication(), tag)
    }

    /**
     * Enables or disables external broadcast control and keeps the [ControlReceiver] manifest
     * component state in lockstep. This ensures the stored preference flag and the receiver's
     * exported/unexported state never diverge — toggling via ADB or the UI always produces the
     * same outcome.
     *
     * @param value `true` to enable the [ControlReceiver] and allow external broadcast commands;
     *   `false` to disable it.
     */
    fun setEnableBroadcastControl(value: Boolean) {
        _enableBroadcastControl.set(value)
        val context = getApplication<Application>()
        val component = ComponentName(context, ControlReceiver::class.java)
        val newState = if (value) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        context.packageManager.setComponentEnabledSetting(
            component,
            newState,
            PackageManager.DONT_KILL_APP
        )
    }

    // ---- Per-setting reset -------------------------------------------------------------------

    /**
     * Resets only the **value** of a single numeric row to its default from `data/Constants.kt`,
     * leaving the row's enable toggle untouched. Backs the per-row reset icon in the settings UI,
     * which is intended to clear a customised value without turning the setting off.
     *
     * @param setting The numeric row whose value to restore to its default.
     */
    fun resetNumeric(setting: NumericSetting) {
        when (setting) {
            NumericSetting.RANDOMIZE_RADIUS -> setRandomizeRadius(DEFAULT_RANDOMIZE_RADIUS)
            NumericSetting.HORIZONTAL_ACCURACY -> setAccuracy(DEFAULT_ACCURACY)
            NumericSetting.VERTICAL_ACCURACY -> setVerticalAccuracy(DEFAULT_VERTICAL_ACCURACY)
            NumericSetting.ALTITUDE -> setAltitude(DEFAULT_ALTITUDE)
            NumericSetting.MEAN_SEA_LEVEL -> setMeanSeaLevel(DEFAULT_MEAN_SEA_LEVEL)
            NumericSetting.MEAN_SEA_LEVEL_ACCURACY -> setMeanSeaLevelAccuracy(DEFAULT_MEAN_SEA_LEVEL_ACCURACY)
            NumericSetting.SPEED -> setSpeed(DEFAULT_SPEED)
            NumericSetting.SPEED_ACCURACY -> setSpeedAccuracy(DEFAULT_SPEED_ACCURACY)
            NumericSetting.JOYSTICK_MAX_SPEED -> setJoystickMaxSpeed(DEFAULT_JOYSTICK_MAX_SPEED)
            NumericSetting.JOYSTICK_PRESET_WALK -> setJoystickPresetSpeed(JoystickPreset.WALK, DEFAULT_JOYSTICK_PRESET_WALK)
            NumericSetting.JOYSTICK_PRESET_RUN -> setJoystickPresetSpeed(JoystickPreset.RUN, DEFAULT_JOYSTICK_PRESET_RUN)
            NumericSetting.JOYSTICK_PRESET_BIKE -> setJoystickPresetSpeed(JoystickPreset.BIKE, DEFAULT_JOYSTICK_PRESET_BIKE)
            NumericSetting.JOYSTICK_PRESET_DRIVE -> setJoystickPresetSpeed(JoystickPreset.DRIVE, DEFAULT_JOYSTICK_PRESET_DRIVE)
        }
    }

    /** Resets the Wi-Fi SSID row value to its default. */
    fun resetWifiSsid() = setWifiSsid(DEFAULT_WIFI_SSID)

    /** Resets the Wi-Fi BSSID row value to its default. */
    fun resetWifiBssid() = setWifiBssid(DEFAULT_WIFI_BSSID)

    /** Resets the Wi-Fi RSSI row value to its default. */
    fun resetWifiRssi() = setWifiRssi(DEFAULT_WIFI_RSSI.toString())

    /**
     * Restores every spoofing and behavioural preference to its constant default value (see
     * `data/Constants.kt`). Unlike the per-row reset icons (which only clear a value), this bulk
     * action also turns every optional "use" toggle and plain switch back off. Numeric and Wi-Fi
     * values are delegated to the same per-row reset helpers so values stay in sync. Updates all
     * flows optimistically so the UI reflects the reset immediately without waiting for disk writes.
     *
     * Three preferences are intentionally excluded from this reset:
     * - **[enableSystemHooks]** — resetting it would trigger a live Xposed scope change, which
     *   requires the service, may fail, and prompts a reboot dialog.
     * - **[SettingsUiState.languageTag]** — resetting it would require recreating the Activity to
     *   apply the new locale, which is a disruptive side effect for a "reset spoofing defaults"
     *   action.
     * - **[SettingsUiState.themeOption]** — an appearance preference unrelated to spoofing
     *   behaviour; excluded for the same reason as language.
     */
    fun resetToDefaults() {
        // Turn every optional "use" toggle back off.
        setUseRandomize(DEFAULT_USE_RANDOMIZE)
        setUseAccuracy(DEFAULT_USE_ACCURACY)
        setUseVerticalAccuracy(DEFAULT_USE_VERTICAL_ACCURACY)
        setUseAltitude(DEFAULT_USE_ALTITUDE)
        setUseMeanSeaLevel(DEFAULT_USE_MEAN_SEA_LEVEL)
        setUseMeanSeaLevelAccuracy(DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY)
        setUseSpeed(DEFAULT_USE_SPEED)
        setUseSpeedAccuracy(DEFAULT_USE_SPEED_ACCURACY)
        // Reset every numeric value.
        NumericSetting.entries.forEach(::resetNumeric)
        // Reset plain switches and Wi-Fi identity (enable flag + values).
        setHideFakeLocationToast(DEFAULT_HIDE_FAKE_LOCATION_TOAST)
        setEnableWifiIdentity(DEFAULT_ENABLE_WIFI_IDENTITY)
        resetWifiSsid()
        resetWifiBssid()
        resetWifiRssi()
        setEnableBroadcastControl(DEFAULT_ENABLE_BROADCAST_CONTROL)
    }

    /**
     * Requests that the system framework packages ([SYSTEM_HOOK_PACKAGES]) are added to (or
     * removed from) the module's Xposed hook scope.
     *
     * The stored [enableSystemHooks] preference is only updated once the scope change actually
     * succeeds, after which a [SystemHooksEvent.RestartRequired] is emitted to prompt a reboot.
     * If no service connection exists a [SystemHooksEvent.ModuleNotActive] is emitted instead and
     * no change is made. Any service-level failure produces a [SystemHooksEvent.ScopeRequestFailed]
     * with the reason from the Xposed service.
     *
     * @param enabled `true` to add the system scope; `false` to remove it.
     */
    fun setEnableSystemHooks(enabled: Boolean) {
        val service = App.service
        if (service == null) {
            _systemHooksEvents.trySend(SystemHooksEvent.ModuleNotActive)
            return
        }

        if (enabled) {
            val callback = object : XposedService.OnScopeEventListener {
                override fun onScopeRequestApproved(approved: List<String>) {
                    viewModelScope.launch {
                        preferencesRepository.saveEnableSystemHooks(true)
                        _systemHooksEvents.trySend(SystemHooksEvent.RestartRequired(true))
                    }
                }

                override fun onScopeRequestFailed(message: String) {
                    _systemHooksEvents.trySend(SystemHooksEvent.ScopeRequestFailed(message))
                }
            }
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) { service.requestScope(SYSTEM_HOOK_PACKAGES, callback) }
                } catch (e: XposedService.ServiceException) {
                    _systemHooksEvents.trySend(SystemHooksEvent.ScopeRequestFailed(e.message ?: e.toString()))
                }
            }
        } else {
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) { service.removeScope(SYSTEM_HOOK_PACKAGES) }
                    preferencesRepository.saveEnableSystemHooks(false)
                    _systemHooksEvents.trySend(SystemHooksEvent.RestartRequired(false))
                } catch (e: XposedService.ServiceException) {
                    _systemHooksEvents.trySend(SystemHooksEvent.ScopeRequestFailed(e.message ?: e.toString()))
                }
            }
        }
    }

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
            joystick = JoystickBackup.from(preferencesRepository.getJoystickSettings()),
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

}
