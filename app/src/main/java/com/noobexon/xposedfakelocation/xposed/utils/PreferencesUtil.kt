package com.noobexon.xposedfakelocation.xposed.utils

import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.noobexon.xposedfakelocation.data.DEFAULT_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_ALTITUDE
import com.noobexon.xposedfakelocation.data.DEFAULT_MEAN_SEA_LEVEL
import com.noobexon.xposedfakelocation.data.DEFAULT_MEAN_SEA_LEVEL_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_RANDOMIZE_RADIUS
import com.noobexon.xposedfakelocation.data.DEFAULT_SPEED
import com.noobexon.xposedfakelocation.data.DEFAULT_SPEED_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_VERTICAL_ACCURACY
import com.noobexon.xposedfakelocation.data.DEFAULT_WIFI_BSSID
import com.noobexon.xposedfakelocation.data.DEFAULT_WIFI_RSSI
import com.noobexon.xposedfakelocation.data.DEFAULT_WIFI_SSID
import com.noobexon.xposedfakelocation.data.KEY_ACCURACY
import com.noobexon.xposedfakelocation.data.KEY_ALTITUDE
import com.noobexon.xposedfakelocation.data.KEY_ENABLE_SYSTEM_HOOKS
import com.noobexon.xposedfakelocation.data.KEY_HIDE_FAKE_LOCATION_TOAST
import com.noobexon.xposedfakelocation.data.KEY_IS_PLAYING
import com.noobexon.xposedfakelocation.data.KEY_JOYSTICK_STATE
import com.noobexon.xposedfakelocation.data.KEY_LAST_CLICKED_LOCATION
import com.noobexon.xposedfakelocation.data.KEY_MEAN_SEA_LEVEL
import com.noobexon.xposedfakelocation.data.KEY_MEAN_SEA_LEVEL_ACCURACY
import com.noobexon.xposedfakelocation.data.KEY_RANDOMIZE_RADIUS
import com.noobexon.xposedfakelocation.data.KEY_SPEED
import com.noobexon.xposedfakelocation.data.KEY_SPEED_ACCURACY
import com.noobexon.xposedfakelocation.data.KEY_TARGET_APPS
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
import com.noobexon.xposedfakelocation.data.model.JoystickState
import com.noobexon.xposedfakelocation.data.model.LastClickedLocation
import com.noobexon.xposedfakelocation.data.normalizeWifiSsid
import com.noobexon.xposedfakelocation.xposed.utils.PreferencesUtil.gson
import com.noobexon.xposedfakelocation.xposed.utils.PreferencesUtil.init

/**
 * Hook-side accessor for the LSPosed remote [SharedPreferences] written by the manager app.
 *
 * All spoofing settings (coordinates, toggles, target app list) live in the remote preference
 * group and are read here on every hook intercept, ensuring hooks always reflect the latest
 * manager state without requiring a process restart.
 *
 * Must be initialised via [init] before any getter is called. Typically called from
 * [com.noobexon.xposedfakelocation.xposed.ModuleEntry.onPackageLoaded].
 */
object PreferencesUtil {
    private const val TAG = "[PreferencesUtil]"
    private val gson = Gson()

    /**
     * Optional logger wired in by [com.noobexon.xposedfakelocation.xposed.ModuleEntry].
     * Routes log calls through the libxposed logging channel.
     */
    @Volatile var logger: ((Int, String, String) -> Unit)? = null
    private fun log(msg: String, priority: Int = Log.INFO) = logger?.invoke(priority, TAG, msg)

    @Volatile private var preferences: SharedPreferences? = null

    /**
     * IMPORTANT: must be held as a strong reference. [SharedPreferences] registers listeners
     * weakly, so a listener with no other reference will be GC'd and silently stop firing.
     */
    private val changeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        log("Remote pref changed: $key")
    }

    /**
     * Initialises this util with the LSPosed remote [SharedPreferences] for the module's
     * settings group. Registers a change listener to log preference updates.
     *
     * Safe to call multiple times — subsequent calls replace the previous preferences instance.
     */
    fun init(prefs: SharedPreferences) {
        preferences = prefs
        prefs.registerOnSharedPreferenceChangeListener(changeListener)
        log("Initialized with remote preferences")
    }

    /** @return `true` if spoofing is currently active, `false`/`null` if not set. */
    fun getIsPlaying(): Boolean? = getPreference(KEY_IS_PLAYING)
    fun getLastClickedLocation(): LastClickedLocation? = getPreference(KEY_LAST_CLICKED_LOCATION)
    fun getUseAccuracy(): Boolean? = getPreference(KEY_USE_ACCURACY)
    fun getAccuracy(): Double? = getPreference(KEY_ACCURACY)
    fun getUseAltitude(): Boolean? = getPreference(KEY_USE_ALTITUDE)
    fun getAltitude(): Double? = getPreference(KEY_ALTITUDE)
    fun getUseRandomize(): Boolean? = getPreference(KEY_USE_RANDOMIZE)
    fun getRandomizeRadius(): Double? = getPreference(KEY_RANDOMIZE_RADIUS)
    fun getUseVerticalAccuracy(): Boolean? = getPreference(KEY_USE_VERTICAL_ACCURACY)
    fun getVerticalAccuracy(): Float? = getPreference(KEY_VERTICAL_ACCURACY)
    fun getUseMeanSeaLevel(): Boolean? = getPreference(KEY_USE_MEAN_SEA_LEVEL)
    fun getMeanSeaLevel(): Double? = getPreference(KEY_MEAN_SEA_LEVEL)
    fun getUseMeanSeaLevelAccuracy(): Boolean? = getPreference(KEY_USE_MEAN_SEA_LEVEL_ACCURACY)
    fun getMeanSeaLevelAccuracy(): Float? = getPreference(KEY_MEAN_SEA_LEVEL_ACCURACY)
    fun getUseSpeed(): Boolean? = getPreference(KEY_USE_SPEED)
    fun getSpeed(): Float? = getPreference(KEY_SPEED)
    fun getUseSpeedAccuracy(): Boolean? = getPreference(KEY_USE_SPEED_ACCURACY)
    fun getSpeedAccuracy(): Float? = getPreference(KEY_SPEED_ACCURACY)
    fun getHideFakeLocationToast(): Boolean? = getPreference(KEY_HIDE_FAKE_LOCATION_TOAST)
    fun getJoystickState(): JoystickState? = getPreference(KEY_JOYSTICK_STATE)
    fun getEnableSystemHooks(): Boolean = preferences?.getBoolean(KEY_ENABLE_SYSTEM_HOOKS, false) ?: false

    fun getWifiSsid(): String =
        normalizeWifiSsid(preferences?.getString(KEY_WIFI_SSID, DEFAULT_WIFI_SSID))

    fun getWifiBssid(): String =
        preferences?.getString(KEY_WIFI_BSSID, DEFAULT_WIFI_BSSID)?.trim()?.takeIf(MAC_ADDRESS_REGEX::matches)
            ?: DEFAULT_WIFI_BSSID

    fun getWifiRssi(): Int =
        preferences?.getInt(KEY_WIFI_RSSI, DEFAULT_WIFI_RSSI)?.coerceIn(MIN_WIFI_RSSI, MAX_WIFI_RSSI)
            ?: DEFAULT_WIFI_RSSI

    /**
     * Returns the set of package names selected by the user as spoofing targets.
     *
     * Stored as a JSON array in remote preferences and parsed on each call.
     * Returns an empty set if preferences are uninitialised, the key is absent, or parsing fails.
     */
    fun getTargetApps(): Set<String> {
        val prefs = preferences ?: return emptySet()
        val json = prefs.getString(KEY_TARGET_APPS, null) ?: return emptySet()
        return runCatching {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>?>(json, type)?.toSet() ?: emptySet()
        }.onFailure { log("Error parsing $KEY_TARGET_APPS JSON: ${it.message}", Log.ERROR) }
            .getOrDefault(emptySet())
    }

    /**
     * Generic preference reader. Dispatches to the correct [SharedPreferences] getter based
     * on the reified type [T]:
     * - [Double] — stored as raw long bits via [java.lang.Double.doubleToRawLongBits] to work
     *   around the lack of a `putDouble` API on [SharedPreferences].
     * - [Float] / [Boolean] — stored natively.
     * - Everything else — stored as a JSON string and deserialised with [gson].
     *
     * Returns `null` if preferences are not yet initialised or the key is absent.
     */
    private inline fun <reified T> getPreference(key: String): T? {
        val preferences = preferences ?: return null
        return when (T::class) {
            Double::class -> {
                val defaultValue = when (key) {
                    KEY_ACCURACY -> java.lang.Double.doubleToRawLongBits(DEFAULT_ACCURACY)
                    KEY_ALTITUDE -> java.lang.Double.doubleToRawLongBits(DEFAULT_ALTITUDE)
                    KEY_RANDOMIZE_RADIUS -> java.lang.Double.doubleToRawLongBits(DEFAULT_RANDOMIZE_RADIUS)
                    KEY_MEAN_SEA_LEVEL -> java.lang.Double.doubleToRawLongBits(DEFAULT_MEAN_SEA_LEVEL)
                    else -> -1L
                }
                val bits = preferences.getLong(key, defaultValue)
                java.lang.Double.longBitsToDouble(bits) as? T
            }
            Float::class -> {
                val defaultValue = when (key) {
                    KEY_VERTICAL_ACCURACY -> DEFAULT_VERTICAL_ACCURACY
                    KEY_MEAN_SEA_LEVEL_ACCURACY -> DEFAULT_MEAN_SEA_LEVEL_ACCURACY
                    KEY_SPEED -> DEFAULT_SPEED
                    KEY_SPEED_ACCURACY -> DEFAULT_SPEED_ACCURACY
                    else -> -1f
                }
                preferences.getFloat(key, defaultValue) as? T
            }
            Boolean::class -> preferences.getBoolean(key, false) as? T
            else -> {
                val json = preferences.getString(key, null) ?: return null.also { log("$key not found in preferences.") }
                runCatching { gson.fromJson(json, T::class.java).also { log("Retrieved $key: $it") } }
                    .onFailure { log("Error parsing $key JSON: ${it.message}") }
                    .getOrNull()
            }
        }
    }

}
