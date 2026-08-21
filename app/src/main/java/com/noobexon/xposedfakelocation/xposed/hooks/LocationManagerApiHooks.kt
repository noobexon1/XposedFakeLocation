package com.noobexon.xposedfakelocation.xposed.hooks

import android.location.Location
import android.util.Log
import com.noobexon.xposedfakelocation.xposed.utils.LocationUtil
import com.noobexon.xposedfakelocation.xposed.utils.PreferencesUtil
import io.github.libxposed.api.XposedInterface

/**
 * Hooks [android.location.LocationManager.getLastKnownLocation] to return a fully-formed
 * fake [Location] when spoofing is active.
 *
 * Unlike [LocationApiHooks] which intercepts individual field getters, this hook replaces
 * the entire [Location] object returned by the pull-style `getLastKnownLocation` API.
 * The original provider string is preserved so the fake location appears to originate from
 * the same provider the app requested.
 */
class LocationManagerApiHooks(
    private val module: XposedInterface,
    private val classLoader: ClassLoader
) {
    private val tag = "[LocationManagerApiHooks]"

    /** Installs the [android.location.LocationManager.getLastKnownLocation] hook. */
    fun init() {
        hookLocationManager()
        module.log(Log.INFO, tag, "Instantiated hooks successfully")
    }

    /**
     * Resolves [android.location.LocationManager] and hooks `getLastKnownLocation(provider)`.
     *
     * When [PreferencesUtil.getIsPlaying] is `true`, returns a fake [Location] via
     * [LocationUtil.createFakeLocation] using the requested provider (which refreshes the
     * spoofed state internally). Otherwise, the original result is passed through unchanged.
     */
    private fun hookLocationManager() {
        runCatching {
            val locationManagerClass = Class.forName("android.location.LocationManager", false, classLoader)
            val method = locationManagerClass.getDeclaredMethod("getLastKnownLocation", String::class.java)

            module.hook(method).intercept { chain ->
                val provider = chain.getArg(0) as String
                val original = chain.proceed() as? Location
                module.log(Log.INFO, tag, "Leaving method getLastKnownLocation(provider)")
                module.log(Log.INFO, tag, "\tRequested data from: $provider")
                module.log(Log.INFO, tag, "\tOriginal location: $original")
                if (PreferencesUtil.getIsPlaying() == true) {
                    val fakeLocation = LocationUtil.createFakeLocation(provider = provider)
                    module.log(Log.INFO, tag, "\tModified location: $fakeLocation")
                    fakeLocation
                } else {
                    original
                }
            }
        }.onFailure { module.log(Log.ERROR, tag, "Error hooking LocationManager - ${it.message}") }
    }
}
