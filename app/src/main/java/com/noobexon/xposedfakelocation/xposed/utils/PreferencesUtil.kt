// PreferencesUtil.kt
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
import com.noobexon.xposedfakelocation.data.KEY_ACCURACY
import com.noobexon.xposedfakelocation.data.KEY_ALTITUDE
import com.noobexon.xposedfakelocation.data.KEY_HIDE_FAKE_LOCATION_TOAST
import com.noobexon.xposedfakelocation.data.KEY_IS_PLAYING
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
import com.noobexon.xposedfakelocation.data.model.LastClickedLocation

object PreferencesUtil {
    private const val TAG = "[PreferencesUtil]"

    @Volatile var logger: ((Int, String, String) -> Unit)? = null
    private fun log(msg: String, priority: Int = Log.INFO) = logger?.invoke(priority, TAG, msg)

    @Volatile private var preferences: SharedPreferences? = null

    // IMPORTANT: keep a strong reference. SharedPreferences holds listeners *weakly*,
    // so a listener that isn't referenced anywhere gets GC'd and silently stops firing.
    private val changeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            log("Remote pref changed: $key")
    }

    fun init(prefs: SharedPreferences) {
        preferences = prefs
        prefs.registerOnSharedPreferenceChangeListener(changeListener)
        log("Initialized with remote preferences")
    }

    /** Returns the raw remote preferences, e.g. for [RoutePlayer]. */
    fun getPreferences(): SharedPreferences? = preferences

    private val locationProxyPackages = setOf(
        "com.android.location.fused",
        "com.google.android.gms"
    )

    fun getIsPlaying(): Boolean? {
        return getPreference<Boolean>(KEY_IS_PLAYING)
    }

    fun getLastClickedLocation(): LastClickedLocation? {
        return getPreference<LastClickedLocation>(KEY_LAST_CLICKED_LOCATION)
    }

    fun getUseAccuracy(): Boolean? {
        return getPreference<Boolean>(KEY_USE_ACCURACY)
    }

    fun getAccuracy(): Double? {
        return getPreference<Double>(KEY_ACCURACY)
    }

    fun getUseAltitude(): Boolean? {
        return getPreference<Boolean>(KEY_USE_ALTITUDE)
    }

    fun getAltitude(): Double? {
        return getPreference<Double>(KEY_ALTITUDE)
    }

    fun getUseRandomize(): Boolean? {
        return getPreference<Boolean>(KEY_USE_RANDOMIZE)
    }

    fun getRandomizeRadius(): Double? {
        return getPreference<Double>(KEY_RANDOMIZE_RADIUS)
    }

    fun getUseVerticalAccuracy(): Boolean? {
        return getPreference<Boolean>(KEY_USE_VERTICAL_ACCURACY)
    }

    fun getVerticalAccuracy(): Float? {
        return getPreference<Float>(KEY_VERTICAL_ACCURACY)
    }

    fun getUseMeanSeaLevel(): Boolean? {
        return getPreference<Boolean>(KEY_USE_MEAN_SEA_LEVEL)
    }

    fun getMeanSeaLevel(): Double? {
        return getPreference<Double>(KEY_MEAN_SEA_LEVEL)
    }

    fun getUseMeanSeaLevelAccuracy(): Boolean? {
        return getPreference<Boolean>(KEY_USE_MEAN_SEA_LEVEL_ACCURACY)
    }

    fun getMeanSeaLevelAccuracy(): Float? {
        return getPreference<Float>(KEY_MEAN_SEA_LEVEL_ACCURACY)
    }

    fun getUseSpeed(): Boolean? {
        return getPreference<Boolean>(KEY_USE_SPEED)
    }

    fun getSpeed(): Float? {
        return getPreference<Float>(KEY_SPEED)
    }

    fun getUseSpeedAccuracy(): Boolean? {
        return getPreference<Boolean>(KEY_USE_SPEED_ACCURACY)
    }

    fun getSpeedAccuracy(): Float? {
        return getPreference<Float>(KEY_SPEED_ACCURACY)
    }

    fun getHideFakeLocationToast(): Boolean? {
        return getPreference<Boolean>(KEY_HIDE_FAKE_LOCATION_TOAST)
    }

    // Mirrors the manager-side scope selection. Stored by PreferencesRepository as a JSON array
    // of package names, so it must be parsed the same way here (not via the generic getPreference).
    fun getTargetApps(): Set<String> {
        val prefs = preferences ?: return emptySet()
        val json = prefs.getString(KEY_TARGET_APPS, null) ?: return emptySet()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson<List<String>?>(json, type)?.toSet() ?: emptySet()
        } catch (e: Exception) {
            log("Error parsing $KEY_TARGET_APPS JSON: ${e.message}", Log.ERROR)
            emptySet()
        }
    }

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
                val json = preferences.getString(key, null)
                if (json != null) {
                    try {
                        Gson().fromJson(json, T::class.java).also {
                            log("Retrieved $key: $it")
                        }
                    } catch (e: Exception) {
                        log("Error parsing $key JSON: ${e.message}")
                        null
                    }
                } else {
                    log("$key not found in preferences.")
                    null
                }
            }
        }
    }
}
