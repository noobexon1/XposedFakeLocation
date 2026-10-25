// PreferencesRepository.kt
package com.noobexon.xposedfakelocation.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.noobexon.xposedfakelocation.data.DEFAULT_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_ALTITUDE
import com.noobexon.xposedfakelocation.data.DEFAULT_ENABLE_BROADCAST_CONTROL
import com.noobexon.xposedfakelocation.data.DEFAULT_ENABLE_SYSTEM_HOOKS
import com.noobexon.xposedfakelocation.data.DEFAULT_ENABLE_WIFI_IDENTITY
import com.noobexon.xposedfakelocation.data.DEFAULT_HIDE_FAKE_LOCATION_TOAST
import com.noobexon.xposedfakelocation.data.DEFAULT_LANGUAGE_TAG
import com.noobexon.xposedfakelocation.data.DEFAULT_MAP_ZOOM
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
import com.noobexon.xposedfakelocation.data.JOYSTICK_MAX_SPEED_MPS
import com.noobexon.xposedfakelocation.data.JOYSTICK_MIN_SPEED_MPS
import com.noobexon.xposedfakelocation.data.KEY_ACCURACY
import com.noobexon.xposedfakelocation.data.KEY_ALTITUDE
import com.noobexon.xposedfakelocation.data.KEY_ENABLE_BROADCAST_CONTROL
import com.noobexon.xposedfakelocation.data.KEY_ENABLE_SYSTEM_HOOKS
import com.noobexon.xposedfakelocation.data.KEY_ENABLE_WIFI_IDENTITY
import com.noobexon.xposedfakelocation.data.KEY_FAVORITES
import com.noobexon.xposedfakelocation.data.KEY_HIDE_FAKE_LOCATION_TOAST
import com.noobexon.xposedfakelocation.data.KEY_IS_PLAYING
import com.noobexon.xposedfakelocation.data.KEY_JOYSTICK_MAX_SPEED
import com.noobexon.xposedfakelocation.data.KEY_JOYSTICK_MODE
import com.noobexon.xposedfakelocation.data.KEY_JOYSTICK_OVERLAY_X
import com.noobexon.xposedfakelocation.data.KEY_JOYSTICK_OVERLAY_Y
import com.noobexon.xposedfakelocation.data.KEY_JOYSTICK_PRESET_BIKE
import com.noobexon.xposedfakelocation.data.KEY_JOYSTICK_PRESET_DRIVE
import com.noobexon.xposedfakelocation.data.KEY_JOYSTICK_PRESET_RUN
import com.noobexon.xposedfakelocation.data.KEY_JOYSTICK_PRESET_WALK
import com.noobexon.xposedfakelocation.data.KEY_JOYSTICK_SELECTED_PRESET
import com.noobexon.xposedfakelocation.data.KEY_JOYSTICK_STATE
import com.noobexon.xposedfakelocation.data.KEY_LANGUAGE_TAG
import com.noobexon.xposedfakelocation.data.KEY_LAST_CLICKED_LOCATION
import com.noobexon.xposedfakelocation.data.KEY_MAP_ZOOM
import com.noobexon.xposedfakelocation.data.KEY_MEAN_SEA_LEVEL
import com.noobexon.xposedfakelocation.data.KEY_MEAN_SEA_LEVEL_ACCURACY
import com.noobexon.xposedfakelocation.data.KEY_RANDOMIZE_RADIUS
import com.noobexon.xposedfakelocation.data.KEY_SPEED
import com.noobexon.xposedfakelocation.data.KEY_SPEED_ACCURACY
import com.noobexon.xposedfakelocation.data.KEY_TARGET_APPS
import com.noobexon.xposedfakelocation.data.KEY_THEME_OPTION
import com.noobexon.xposedfakelocation.data.KEY_USE_ACCURACY
import com.noobexon.xposedfakelocation.data.KEY_USE_ALTITUDE
import com.noobexon.xposedfakelocation.data.KEY_USE_MEAN_SEA_LEVEL
import com.noobexon.xposedfakelocation.data.KEY_USE_MEAN_SEA_LEVEL_ACCURACY
import com.noobexon.xposedfakelocation.data.KEY_USE_RANDOMIZE
import com.noobexon.xposedfakelocation.data.KEY_USE_SPEED
import com.noobexon.xposedfakelocation.data.KEY_USE_SPEED_ACCURACY
import com.noobexon.xposedfakelocation.data.KEY_USE_VERTICAL_ACCURACY
import com.noobexon.xposedfakelocation.data.KEY_VERTICAL_ACCURACY
import com.noobexon.xposedfakelocation.data.KEY_WIFI_BSSID
import com.noobexon.xposedfakelocation.data.KEY_WIFI_RSSI
import com.noobexon.xposedfakelocation.data.KEY_WIFI_SSID
import com.noobexon.xposedfakelocation.data.MAC_ADDRESS_REGEX
import com.noobexon.xposedfakelocation.data.MAX_WIFI_RSSI
import com.noobexon.xposedfakelocation.data.MIN_WIFI_RSSI
import com.noobexon.xposedfakelocation.data.REMOTE_PREFS_GROUP
import com.noobexon.xposedfakelocation.data.SHARED_PREFS_FILE
import com.noobexon.xposedfakelocation.data.model.FavoriteLocation
import com.noobexon.xposedfakelocation.data.model.JoystickMode
import com.noobexon.xposedfakelocation.data.model.JoystickPreset
import com.noobexon.xposedfakelocation.data.model.JoystickSettings
import com.noobexon.xposedfakelocation.data.model.JoystickState
import com.noobexon.xposedfakelocation.data.model.LastClickedLocation
import com.noobexon.xposedfakelocation.data.normalizeWifiSsid
import com.noobexon.xposedfakelocation.manager.App
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.util.Locale

/**
 * Single-source-of-truth preferences store.
 *
 * Each setting lives in exactly one place:
 *  - Hook-shared settings (read by the Xposed module) live in the LSPosed remote
 *    preferences exposed through [App.service]. They are only available while the
 *    XposedService is bound; when it isn't, reads fall back to defaults and writes
 *    are dropped (the UI is gated behind a bound service anyway).
 *  - Manager-only settings (language, favorites, broadcast control) live in a local
 *    [SharedPreferences] file so they are always available, including at startup and
 *    when the module is disabled.
 *
 * Doubles are encoded as raw long bits because [SharedPreferences] has no putDouble,
 * keeping read/write symmetry with the hook-side PreferencesUtil.
 */
class PreferencesRepository(context: Context) {
    private companion object {
        /** Process-wide: serializes marker writes across the per-caller repository instances. */
        val markerLock = Any()
    }

    private val tag = "PreferencesRepository"

    private val gson = Gson()

    private val localPrefs: SharedPreferences =
        context.getSharedPreferences(SHARED_PREFS_FILE, Context.MODE_PRIVATE)

    private fun remotePrefs(): SharedPreferences? =
        App.service?.getRemotePreferences(REMOTE_PREFS_GROUP)

    // region Flow helpers

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> remoteFlow(key: String, default: T, read: (SharedPreferences) -> T): Flow<T> =
        App.serviceState.flatMapLatest { service ->
            val prefs = service?.getRemotePreferences(REMOTE_PREFS_GROUP)
            if (prefs == null) {
                flowOf(default)
            } else {
                prefsChangeFlow(prefs, setOf(key)) { read(prefs) }
            }
        }

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

    // endregion

    // region Write helpers

    private inline fun editRemote(action: SharedPreferences.Editor.() -> Unit) {
        val prefs = remotePrefs()
        if (prefs == null) {
            Log.w(tag, "Remote preferences unavailable (service not bound); write skipped")
            return
        }
        prefs.edit(action = action)
    }

    private inline fun editLocal(action: SharedPreferences.Editor.() -> Unit) {
        localPrefs.edit(action = action)
    }

    private fun readRemoteDouble(key: String, default: Double): Double {
        val prefs = remotePrefs() ?: return default
        val bits = prefs.getLong(key, java.lang.Double.doubleToRawLongBits(default))
        return java.lang.Double.longBitsToDouble(bits)
    }

    // endregion

    // region Is Playing (remote)
    fun getIsPlayingFlow(): Flow<Boolean> = remoteFlow(KEY_IS_PLAYING, false) { it.getBoolean(KEY_IS_PLAYING, false) }
    suspend fun saveIsPlaying(isPlaying: Boolean) = editRemote { putBoolean(KEY_IS_PLAYING, isPlaying) }
    fun getIsPlaying(): Boolean = remotePrefs()?.getBoolean(KEY_IS_PLAYING, false) ?: false
    // endregion

    // region Last Clicked Location (remote)
    fun getLastClickedLocationFlow(): Flow<LastClickedLocation?> =
        remoteFlow<LastClickedLocation?>(KEY_LAST_CLICKED_LOCATION, null) {
            parseLastClickedLocation(it.getString(KEY_LAST_CLICKED_LOCATION, null))
        }

    suspend fun saveLastClickedLocation(latitude: Double, longitude: Double) {
        val json = gson.toJson(LastClickedLocation(latitude, longitude))
        synchronized(markerLock) { editRemote { putString(KEY_LAST_CLICKED_LOCATION, json) } }
    }

    fun getLastClickedLocation(): LastClickedLocation? =
        parseLastClickedLocation(remotePrefs()?.getString(KEY_LAST_CLICKED_LOCATION, null))

    suspend fun clearLastClickedLocation() {
        synchronized(markerLock) { editRemote { remove(KEY_LAST_CLICKED_LOCATION) } }
        saveIsPlaying(false)
        Log.d(tag, "Cleared 'LastClickedLocation' and set 'IsPlaying' to false")
    }

    private fun parseLastClickedLocation(json: String?): LastClickedLocation? {
        if (json == null) return null
        return try {
            gson.fromJson(json, LastClickedLocation::class.java)
        } catch (e: JsonSyntaxException) {
            Log.e(tag, "Error parsing LastClickedLocation: ${e.message}")
            null
        }
    }
    // endregion

    // region Joystick State (remote)
    /** Publishes the joystick motion override without touching the marker. */
    fun saveJoystickState(state: JoystickState) =
        editRemote { putString(KEY_JOYSTICK_STATE, gson.toJson(state)) }

    /**
     * Writes the joystick state and, if the marker still exists, its new position in a single
     * remote-prefs edit; serialized with [clearLastClickedLocation] so a cleared marker is never revived.
     */
    fun saveJoystickFrame(latitude: Double, longitude: Double, state: JoystickState) =
        synchronized(markerLock) {
            val markerExists = getLastClickedLocation() != null
            editRemote {
                if (markerExists) {
                    putString(KEY_LAST_CLICKED_LOCATION, gson.toJson(LastClickedLocation(latitude, longitude)))
                }
                putString(KEY_JOYSTICK_STATE, gson.toJson(state))
            }
        }
    // endregion

    // region Use Accuracy / Accuracy (remote)
    fun getUseAccuracyFlow(): Flow<Boolean> = remoteFlow(KEY_USE_ACCURACY, DEFAULT_USE_ACCURACY) { it.getBoolean(KEY_USE_ACCURACY, DEFAULT_USE_ACCURACY) }
    suspend fun saveUseAccuracy(useAccuracy: Boolean) = editRemote { putBoolean(KEY_USE_ACCURACY, useAccuracy) }
    fun getUseAccuracy(): Boolean = remotePrefs()?.getBoolean(KEY_USE_ACCURACY, DEFAULT_USE_ACCURACY) ?: DEFAULT_USE_ACCURACY

    fun getAccuracyFlow(): Flow<Double> = remoteFlow(KEY_ACCURACY, DEFAULT_ACCURACY) { readRemoteDouble(KEY_ACCURACY, DEFAULT_ACCURACY) }
    suspend fun saveAccuracy(accuracy: Double) = editRemote { putLong(KEY_ACCURACY, java.lang.Double.doubleToRawLongBits(accuracy)) }
    fun getAccuracy(): Double = readRemoteDouble(KEY_ACCURACY, DEFAULT_ACCURACY)
    // endregion

    // region Use Altitude / Altitude (remote)
    fun getUseAltitudeFlow(): Flow<Boolean> = remoteFlow(KEY_USE_ALTITUDE, DEFAULT_USE_ALTITUDE) { it.getBoolean(KEY_USE_ALTITUDE, DEFAULT_USE_ALTITUDE) }
    suspend fun saveUseAltitude(useAltitude: Boolean) = editRemote { putBoolean(KEY_USE_ALTITUDE, useAltitude) }
    fun getUseAltitude(): Boolean = remotePrefs()?.getBoolean(KEY_USE_ALTITUDE, DEFAULT_USE_ALTITUDE) ?: DEFAULT_USE_ALTITUDE

    fun getAltitudeFlow(): Flow<Double> = remoteFlow(KEY_ALTITUDE, DEFAULT_ALTITUDE) { readRemoteDouble(KEY_ALTITUDE, DEFAULT_ALTITUDE) }
    suspend fun saveAltitude(altitude: Double) = editRemote { putLong(KEY_ALTITUDE, java.lang.Double.doubleToRawLongBits(altitude)) }
    fun getAltitude(): Double = readRemoteDouble(KEY_ALTITUDE, DEFAULT_ALTITUDE)
    // endregion

    // region Use Randomize / Randomize Radius (remote)
    fun getUseRandomizeFlow(): Flow<Boolean> = remoteFlow(KEY_USE_RANDOMIZE, DEFAULT_USE_RANDOMIZE) { it.getBoolean(KEY_USE_RANDOMIZE, DEFAULT_USE_RANDOMIZE) }
    suspend fun saveUseRandomize(randomize: Boolean) = editRemote { putBoolean(KEY_USE_RANDOMIZE, randomize) }
    fun getUseRandomize(): Boolean = remotePrefs()?.getBoolean(KEY_USE_RANDOMIZE, DEFAULT_USE_RANDOMIZE) ?: DEFAULT_USE_RANDOMIZE

    fun getRandomizeRadiusFlow(): Flow<Double> = remoteFlow(KEY_RANDOMIZE_RADIUS, DEFAULT_RANDOMIZE_RADIUS) { readRemoteDouble(KEY_RANDOMIZE_RADIUS, DEFAULT_RANDOMIZE_RADIUS) }
    suspend fun saveRandomizeRadius(radius: Double) = editRemote { putLong(KEY_RANDOMIZE_RADIUS, java.lang.Double.doubleToRawLongBits(radius)) }
    fun getRandomizeRadius(): Double = readRemoteDouble(KEY_RANDOMIZE_RADIUS, DEFAULT_RANDOMIZE_RADIUS)
    // endregion

    // region Vertical Accuracy (remote)
    fun getUseVerticalAccuracyFlow(): Flow<Boolean> = remoteFlow(KEY_USE_VERTICAL_ACCURACY, DEFAULT_USE_VERTICAL_ACCURACY) { it.getBoolean(KEY_USE_VERTICAL_ACCURACY, DEFAULT_USE_VERTICAL_ACCURACY) }
    suspend fun saveUseVerticalAccuracy(useVerticalAccuracy: Boolean) = editRemote { putBoolean(KEY_USE_VERTICAL_ACCURACY, useVerticalAccuracy) }
    fun getUseVerticalAccuracy(): Boolean = remotePrefs()?.getBoolean(KEY_USE_VERTICAL_ACCURACY, DEFAULT_USE_VERTICAL_ACCURACY) ?: DEFAULT_USE_VERTICAL_ACCURACY

    fun getVerticalAccuracyFlow(): Flow<Float> = remoteFlow(KEY_VERTICAL_ACCURACY, DEFAULT_VERTICAL_ACCURACY) { it.getFloat(KEY_VERTICAL_ACCURACY, DEFAULT_VERTICAL_ACCURACY) }
    suspend fun saveVerticalAccuracy(verticalAccuracy: Float) = editRemote { putFloat(KEY_VERTICAL_ACCURACY, verticalAccuracy) }
    fun getVerticalAccuracy(): Float = remotePrefs()?.getFloat(KEY_VERTICAL_ACCURACY, DEFAULT_VERTICAL_ACCURACY) ?: DEFAULT_VERTICAL_ACCURACY
    // endregion

    // region Mean Sea Level (remote)
    fun getUseMeanSeaLevelFlow(): Flow<Boolean> = remoteFlow(KEY_USE_MEAN_SEA_LEVEL, DEFAULT_USE_MEAN_SEA_LEVEL) { it.getBoolean(KEY_USE_MEAN_SEA_LEVEL, DEFAULT_USE_MEAN_SEA_LEVEL) }
    suspend fun saveUseMeanSeaLevel(useMeanSeaLevel: Boolean) = editRemote { putBoolean(KEY_USE_MEAN_SEA_LEVEL, useMeanSeaLevel) }
    fun getUseMeanSeaLevel(): Boolean = remotePrefs()?.getBoolean(KEY_USE_MEAN_SEA_LEVEL, DEFAULT_USE_MEAN_SEA_LEVEL) ?: DEFAULT_USE_MEAN_SEA_LEVEL

    fun getMeanSeaLevelFlow(): Flow<Double> = remoteFlow(KEY_MEAN_SEA_LEVEL, DEFAULT_MEAN_SEA_LEVEL) { readRemoteDouble(KEY_MEAN_SEA_LEVEL, DEFAULT_MEAN_SEA_LEVEL) }
    suspend fun saveMeanSeaLevel(meanSeaLevel: Double) = editRemote { putLong(KEY_MEAN_SEA_LEVEL, java.lang.Double.doubleToRawLongBits(meanSeaLevel)) }
    fun getMeanSeaLevel(): Double = readRemoteDouble(KEY_MEAN_SEA_LEVEL, DEFAULT_MEAN_SEA_LEVEL)

    fun getUseMeanSeaLevelAccuracyFlow(): Flow<Boolean> = remoteFlow(KEY_USE_MEAN_SEA_LEVEL_ACCURACY, DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY) { it.getBoolean(KEY_USE_MEAN_SEA_LEVEL_ACCURACY, DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY) }
    suspend fun saveUseMeanSeaLevelAccuracy(useMeanSeaLevelAccuracy: Boolean) = editRemote { putBoolean(KEY_USE_MEAN_SEA_LEVEL_ACCURACY, useMeanSeaLevelAccuracy) }
    fun getUseMeanSeaLevelAccuracy(): Boolean = remotePrefs()?.getBoolean(KEY_USE_MEAN_SEA_LEVEL_ACCURACY, DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY) ?: DEFAULT_USE_MEAN_SEA_LEVEL_ACCURACY

    fun getMeanSeaLevelAccuracyFlow(): Flow<Float> = remoteFlow(KEY_MEAN_SEA_LEVEL_ACCURACY, DEFAULT_MEAN_SEA_LEVEL_ACCURACY) { it.getFloat(KEY_MEAN_SEA_LEVEL_ACCURACY, DEFAULT_MEAN_SEA_LEVEL_ACCURACY) }
    suspend fun saveMeanSeaLevelAccuracy(meanSeaLevelAccuracy: Float) = editRemote { putFloat(KEY_MEAN_SEA_LEVEL_ACCURACY, meanSeaLevelAccuracy) }
    fun getMeanSeaLevelAccuracy(): Float = remotePrefs()?.getFloat(KEY_MEAN_SEA_LEVEL_ACCURACY, DEFAULT_MEAN_SEA_LEVEL_ACCURACY) ?: DEFAULT_MEAN_SEA_LEVEL_ACCURACY
    // endregion

    // region Speed (remote)
    fun getUseSpeedFlow(): Flow<Boolean> = remoteFlow(KEY_USE_SPEED, DEFAULT_USE_SPEED) { it.getBoolean(KEY_USE_SPEED, DEFAULT_USE_SPEED) }
    suspend fun saveUseSpeed(useSpeed: Boolean) = editRemote { putBoolean(KEY_USE_SPEED, useSpeed) }
    fun getUseSpeed(): Boolean = remotePrefs()?.getBoolean(KEY_USE_SPEED, DEFAULT_USE_SPEED) ?: DEFAULT_USE_SPEED

    fun getSpeedFlow(): Flow<Float> = remoteFlow(KEY_SPEED, DEFAULT_SPEED) { it.getFloat(KEY_SPEED, DEFAULT_SPEED) }
    suspend fun saveSpeed(speed: Float) = editRemote { putFloat(KEY_SPEED, speed) }
    fun getSpeed(): Float = remotePrefs()?.getFloat(KEY_SPEED, DEFAULT_SPEED) ?: DEFAULT_SPEED

    fun getUseSpeedAccuracyFlow(): Flow<Boolean> = remoteFlow(KEY_USE_SPEED_ACCURACY, DEFAULT_USE_SPEED_ACCURACY) { it.getBoolean(KEY_USE_SPEED_ACCURACY, DEFAULT_USE_SPEED_ACCURACY) }
    suspend fun saveUseSpeedAccuracy(useSpeedAccuracy: Boolean) = editRemote { putBoolean(KEY_USE_SPEED_ACCURACY, useSpeedAccuracy) }
    fun getUseSpeedAccuracy(): Boolean = remotePrefs()?.getBoolean(KEY_USE_SPEED_ACCURACY, DEFAULT_USE_SPEED_ACCURACY) ?: DEFAULT_USE_SPEED_ACCURACY

    fun getSpeedAccuracyFlow(): Flow<Float> = remoteFlow(KEY_SPEED_ACCURACY, DEFAULT_SPEED_ACCURACY) { it.getFloat(KEY_SPEED_ACCURACY, DEFAULT_SPEED_ACCURACY) }
    suspend fun saveSpeedAccuracy(speedAccuracy: Float) = editRemote { putFloat(KEY_SPEED_ACCURACY, speedAccuracy) }
    fun getSpeedAccuracy(): Float = remotePrefs()?.getFloat(KEY_SPEED_ACCURACY, DEFAULT_SPEED_ACCURACY) ?: DEFAULT_SPEED_ACCURACY
    // endregion

    // region Enable System-Level Hooks (remote)
    fun getEnableSystemHooksFlow(): Flow<Boolean> = remoteFlow(KEY_ENABLE_SYSTEM_HOOKS, DEFAULT_ENABLE_SYSTEM_HOOKS) { it.getBoolean(KEY_ENABLE_SYSTEM_HOOKS, DEFAULT_ENABLE_SYSTEM_HOOKS) }
    suspend fun saveEnableSystemHooks(enabled: Boolean) = editRemote { putBoolean(KEY_ENABLE_SYSTEM_HOOKS, enabled) }
    fun getEnableSystemHooks(): Boolean = remotePrefs()?.getBoolean(KEY_ENABLE_SYSTEM_HOOKS, DEFAULT_ENABLE_SYSTEM_HOOKS) ?: DEFAULT_ENABLE_SYSTEM_HOOKS
    // endregion

    // region Enable Wi-Fi Identity Hooks (remote)
    fun getEnableWifiIdentityFlow(): Flow<Boolean> =
        remoteFlow(KEY_ENABLE_WIFI_IDENTITY, DEFAULT_ENABLE_WIFI_IDENTITY) {
            it.getBoolean(KEY_ENABLE_WIFI_IDENTITY, DEFAULT_ENABLE_WIFI_IDENTITY)
        }

    suspend fun saveEnableWifiIdentity(enabled: Boolean) =
        editRemote { putBoolean(KEY_ENABLE_WIFI_IDENTITY, enabled) }

    fun getEnableWifiIdentity(): Boolean =
        remotePrefs()?.getBoolean(KEY_ENABLE_WIFI_IDENTITY, DEFAULT_ENABLE_WIFI_IDENTITY)
            ?: DEFAULT_ENABLE_WIFI_IDENTITY
    // endregion

    // region Hide Fake Location Toast (remote)
    fun getHideFakeLocationToastFlow(): Flow<Boolean> = remoteFlow(KEY_HIDE_FAKE_LOCATION_TOAST, DEFAULT_HIDE_FAKE_LOCATION_TOAST) { it.getBoolean(KEY_HIDE_FAKE_LOCATION_TOAST, DEFAULT_HIDE_FAKE_LOCATION_TOAST) }
    suspend fun saveHideFakeLocationToast(hideFakeLocationToast: Boolean) = editRemote { putBoolean(KEY_HIDE_FAKE_LOCATION_TOAST, hideFakeLocationToast) }
    fun getHideFakeLocationToast(): Boolean = remotePrefs()?.getBoolean(KEY_HIDE_FAKE_LOCATION_TOAST, DEFAULT_HIDE_FAKE_LOCATION_TOAST) ?: DEFAULT_HIDE_FAKE_LOCATION_TOAST
    // endregion

    // region Wi-Fi identity (remote)
    fun getWifiSsidFlow(): Flow<String> =
        remoteFlow(KEY_WIFI_SSID, DEFAULT_WIFI_SSID) {
            normalizeWifiSsid(it.getString(KEY_WIFI_SSID, DEFAULT_WIFI_SSID))
        }

    suspend fun saveWifiSsid(ssid: String) =
        editRemote { putString(KEY_WIFI_SSID, normalizeWifiSsid(ssid)) }

    fun getWifiBssidFlow(): Flow<String> =
        remoteFlow(KEY_WIFI_BSSID, DEFAULT_WIFI_BSSID) {
            normalizeWifiBssid(it.getString(KEY_WIFI_BSSID, DEFAULT_WIFI_BSSID).orEmpty())
        }

    suspend fun saveWifiBssid(bssid: String) =
        editRemote { putString(KEY_WIFI_BSSID, normalizeWifiBssid(bssid)) }

    fun getWifiRssiFlow(): Flow<Int> =
        remoteFlow(KEY_WIFI_RSSI, DEFAULT_WIFI_RSSI) {
            it.getInt(KEY_WIFI_RSSI, DEFAULT_WIFI_RSSI).coerceIn(MIN_WIFI_RSSI, MAX_WIFI_RSSI)
        }

    suspend fun saveWifiRssi(rssi: Int) =
        editRemote { putInt(KEY_WIFI_RSSI, rssi.coerceIn(MIN_WIFI_RSSI, MAX_WIFI_RSSI)) }

    private fun normalizeWifiBssid(bssid: String): String {
        val trimmed = bssid.trim()
        return if (MAC_ADDRESS_REGEX.matches(trimmed)) {
            trimmed.uppercase(Locale.US)
        } else {
            DEFAULT_WIFI_BSSID
        }
    }
    // endregion

    // region Target Apps (remote)
    fun getTargetAppsFlow(): Flow<Set<String>> =
        remoteFlow(KEY_TARGET_APPS, emptySet()) { parseTargetApps(it.getString(KEY_TARGET_APPS, null)) }

    suspend fun saveTargetApps(packageNames: Set<String>) {
        val normalized = packageNames
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        val json = gson.toJson(normalized)
        editRemote { putString(KEY_TARGET_APPS, json) }
    }

    fun getTargetApps(): Set<String> = parseTargetApps(remotePrefs()?.getString(KEY_TARGET_APPS, null))

    private fun parseTargetApps(json: String?): Set<String> {
        if (json.isNullOrBlank()) return emptySet()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(json, type).toSet()
        } catch (e: JsonSyntaxException) {
            Log.e(tag, "Error parsing target apps: ${e.message}")
            emptySet()
        }
    }
    // endregion

    // region Favorites (local)
    fun getFavoritesFlow(): Flow<List<FavoriteLocation>> =
        localFlow(KEY_FAVORITES) { parseFavorites(it.getString(KEY_FAVORITES, null)) }

    suspend fun addFavorite(favorite: FavoriteLocation) {
        val updated = getFavorites().toMutableList().apply { add(favorite) }
        saveFavorites(updated)
        Log.d(tag, "Added Favorite: $favorite")
    }

    suspend fun removeFavorite(favorite: FavoriteLocation) {
        val updated = getFavorites().toMutableList().apply { remove(favorite) }
        saveFavorites(updated)
        Log.d(tag, "Removed Favorite: $favorite")
    }

    suspend fun updateFavorite(old: FavoriteLocation, new: FavoriteLocation) {
        val updated = getFavorites().toMutableList().apply {
            val index = indexOf(old)
            if (index != -1) set(index, new)
        }
        saveFavorites(updated)
        Log.d(tag, "Updated Favorite: $old -> $new")
    }

    /** Replaces the entire favorites list (used by settings import). */
    suspend fun replaceFavorites(favorites: List<FavoriteLocation>) {
        saveFavorites(favorites)
        Log.d(tag, "Replaced favorites with ${favorites.size} entries")
    }

    fun getFavorites(): List<FavoriteLocation> = parseFavorites(localPrefs.getString(KEY_FAVORITES, null))

    private fun saveFavorites(favorites: List<FavoriteLocation>) {
        val json = gson.toJson(favorites)
        editLocal { putString(KEY_FAVORITES, json) }
    }

    private fun parseFavorites(json: String?): List<FavoriteLocation> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<FavoriteLocation>>() {}.type
            gson.fromJson(json, type)
        } catch (e: JsonSyntaxException) {
            Log.e(tag, "Error parsing Favorites: ${e.message}")
            emptyList()
        }
    }
    // endregion

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
            maxSpeedMps = readJoystickSpeed(prefs, KEY_JOYSTICK_MAX_SPEED, defaults.maxSpeedMps),
            walkSpeedMps = readJoystickSpeed(prefs, KEY_JOYSTICK_PRESET_WALK, defaults.walkSpeedMps),
            runSpeedMps = readJoystickSpeed(prefs, KEY_JOYSTICK_PRESET_RUN, defaults.runSpeedMps),
            bikeSpeedMps = readJoystickSpeed(prefs, KEY_JOYSTICK_PRESET_BIKE, defaults.bikeSpeedMps),
            driveSpeedMps = readJoystickSpeed(prefs, KEY_JOYSTICK_PRESET_DRIVE, defaults.driveSpeedMps),
            selectedPreset = JoystickPreset.entries.firstOrNull { it.name == presetName } ?: defaults.selectedPreset,
        )
    }

    /** A stored speed outside the supported range (e.g. hand-edited prefs) falls back to [default]. */
    private fun readJoystickSpeed(prefs: SharedPreferences, key: String, default: Float): Float =
        prefs.getFloat(key, default).takeIf { it in JOYSTICK_MIN_SPEED_MPS..JOYSTICK_MAX_SPEED_MPS } ?: default

    private fun presetSpeedKey(preset: JoystickPreset): String = when (preset) {
        JoystickPreset.WALK -> KEY_JOYSTICK_PRESET_WALK
        JoystickPreset.RUN -> KEY_JOYSTICK_PRESET_RUN
        JoystickPreset.BIKE -> KEY_JOYSTICK_PRESET_BIKE
        JoystickPreset.DRIVE -> KEY_JOYSTICK_PRESET_DRIVE
    }
    // endregion
    // region Map Zoom (local)
    fun getMapZoom(): Double {
        val bits = localPrefs.getLong(KEY_MAP_ZOOM, java.lang.Double.doubleToRawLongBits(DEFAULT_MAP_ZOOM))
        return java.lang.Double.longBitsToDouble(bits)
    }

    fun saveMapZoom(zoom: Double) =
        editLocal { putLong(KEY_MAP_ZOOM, java.lang.Double.doubleToRawLongBits(zoom)) }
    // endregion

    // region Broadcast Control (local)
    fun getEnableBroadcastControlFlow(): Flow<Boolean> = localFlow(KEY_ENABLE_BROADCAST_CONTROL) { it.getBoolean(KEY_ENABLE_BROADCAST_CONTROL, DEFAULT_ENABLE_BROADCAST_CONTROL) }
    suspend fun saveEnableBroadcastControl(enable: Boolean) = editLocal { putBoolean(KEY_ENABLE_BROADCAST_CONTROL, enable) }
    // endregion

    // region Language (local; shared with LocaleController)
    fun getLanguageTagFlow(): Flow<String> = localFlow(KEY_LANGUAGE_TAG) { it.getString(KEY_LANGUAGE_TAG, DEFAULT_LANGUAGE_TAG) ?: DEFAULT_LANGUAGE_TAG }
    suspend fun saveLanguageTag(languageTag: String) = editLocal { putString(KEY_LANGUAGE_TAG, languageTag) }
    // endregion

    // region Theme (local)
    fun getThemeOptionFlow(): Flow<String> = localFlow(KEY_THEME_OPTION) { it.getString(KEY_THEME_OPTION, DEFAULT_THEME_OPTION) ?: DEFAULT_THEME_OPTION }
    suspend fun saveThemeOption(themeTag: String) = editLocal { putString(KEY_THEME_OPTION, themeTag) }
    // endregion

}
