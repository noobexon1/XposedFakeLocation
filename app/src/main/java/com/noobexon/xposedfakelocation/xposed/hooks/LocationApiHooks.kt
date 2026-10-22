package com.noobexon.xposedfakelocation.xposed.hooks

import android.os.Build
import android.util.Log
import com.noobexon.xposedfakelocation.xposed.utils.LocationUtil
import com.noobexon.xposedfakelocation.xposed.utils.PreferencesUtil
import io.github.libxposed.api.XposedInterface

/**
 * Installs hooks on the public `android.location.Location` getter methods to intercept
 * and replace individual location fields with spoofed values.
 *
 * Each hook calls [LocationUtil.updateLocation] to refresh the current spoofed state from
 * preferences, then returns the spoofed value when [PreferencesUtil.getIsPlaying] is `true`
 * and the field's optional "use" toggle is enabled.
 *
 * MSL altitude hooks are only installed on API 34+ ([Build.VERSION_CODES.UPSIDE_DOWN_CAKE]).
 */
class LocationApiHooks(private val module: XposedInterface, private val classLoader: ClassLoader) {
    private val tag = "[LocationApiHooks]"

    /** Installs all `android.location.Location` getter hooks. */
    fun init() {
        hookLocation()
        module.log(Log.INFO, tag, "Instantiated hooks successfully")
    }

    /**
     * Resolves `android.location.Location` via [classLoader] and installs a [hookMethod]
     * interceptor on each spoofable getter. Failures are caught and logged without crashing
     * the target app.
     */
    private fun hookLocation() {
        runCatching {
            val locationClass = Class.forName("android.location.Location", false, classLoader)
            with(locationClass) {
                hookMethod("getLatitude", enabled = { true }) { LocationUtil.latitude }
                hookMethod("getLongitude", enabled = { true }) { LocationUtil.longitude }
                hookMethod("getAccuracy", enabled = { PreferencesUtil.getUseAccuracy() == true }) { LocationUtil.accuracy }
                hookMethod("getAltitude", enabled = { PreferencesUtil.getUseAltitude() == true }) { LocationUtil.altitude }
                hookMethod("getVerticalAccuracyMeters", enabled = { PreferencesUtil.getUseVerticalAccuracy() == true }) { LocationUtil.verticalAccuracy }
                hookMethod("getSpeed", enabled = { LocationUtil.motionOverride || PreferencesUtil.getUseSpeed() == true }) { LocationUtil.speed }
                hookMethod("getSpeedAccuracyMetersPerSecond", enabled = { LocationUtil.motionOverride || PreferencesUtil.getUseSpeedAccuracy() == true }) { LocationUtil.speedAccuracy }
                hookMethod("getBearing", enabled = { LocationUtil.motionOverride }) { LocationUtil.bearing }
                hookMethod("hasSpeed", enabled = { LocationUtil.motionOverride }) { true }
                hookMethod("hasBearing", enabled = { LocationUtil.motionOverride }) { true }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    hookMethod("getMslAltitudeMeters", enabled = { PreferencesUtil.getUseMeanSeaLevel() == true }) { LocationUtil.meanSeaLevel }
                    hookMethod("getMslAltitudeAccuracyMeters", enabled = { PreferencesUtil.getUseMeanSeaLevelAccuracy() == true }) { LocationUtil.meanSeaLevelAccuracy }
                } else {
                    module.log(Log.INFO, tag, "getMslAltitudeMeters() and getMslAltitudeAccuracyMeters() not available on this API level")
                }
            }
        }.onFailure { module.log(Log.ERROR, tag, "Error hooking Location class - ${it.message}") }
    }

    /**
     * Extension on [Class] that hooks [methodName] and replaces its return value with
     * [spoofed] when [PreferencesUtil.getIsPlaying] is `true` and [enabled] returns `true`.
     *
     * [LocationUtil.updateLocation] is called on every intercept to ensure the latest
     * preference values are reflected before the spoofed value is read.
     *
     * @param methodName Name of the no-arg getter to hook on this [Class].
     * @param enabled Additional condition that must hold alongside `isPlaying` for spoofing
     *   to activate — use `{ true }` for fields that have no separate toggle.
     * @param spoofed Lambda returning the spoofed value to substitute when active.
     */
    private fun Class<*>.hookMethod(
        methodName: String,
        enabled: () -> Boolean,
        spoofed: () -> Any?,
    ) {
        module.hook(getDeclaredMethod(methodName)).intercept { chain ->
            val original = chain.proceed()
            LocationUtil.updateLocation()
            module.log(Log.INFO, tag, "Leaving $methodName\n\tOriginal: $original")
            if (PreferencesUtil.getIsPlaying() == true && enabled()) {
                val value = spoofed()
                module.log(Log.INFO, tag, "\tModified to: $value")
                value
            } else {
                original
            }
        }
    }
}
