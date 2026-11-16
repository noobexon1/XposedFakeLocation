package com.noobexon.xposedfakelocation.xposed.hooks

import android.location.OnNmeaMessageListener
import android.util.Log
import com.noobexon.xposedfakelocation.xposed.gnss.GnssSkySource
import com.noobexon.xposedfakelocation.xposed.gnss.NmeaListenerWrapper
import io.github.libxposed.api.XposedInterface

/**
 * App-side NMEA hooks. While spoofing, target apps' `OnNmeaMessageListener`s receive sentences for the fake
 * position, time and sky instead of the real ones. Every `addNmeaListener` overload taking the listener is
 * hooked (on API 31+ they call each other in-process; wrappers are never wrapped again). The legacy
 * `GpsStatus.NmeaListener` methods are no-ops on API 29+ and are left alone.
 */
class GnssNmeaApiHooks(
    private val module: XposedInterface,
    private val classLoader: ClassLoader,
) {
    private val tag = "[GnssNmeaApiHooks]"
    private val callbackHooks = CallbackArgumentHooks(module, tag)
    private val listeners = WrapperCache<OnNmeaMessageListener>(
        isWrapper = { it is NmeaListenerWrapper },
        create = ::NmeaListenerWrapper,
    )

    fun init() {
        GnssSkySource.logger = { priority, logTag, message -> module.log(priority, logTag, message) }
        val locationManagerClass = runCatching {
            Class.forName("android.location.LocationManager", false, classLoader)
        }.onFailure {
            module.log(Log.ERROR, tag, "LocationManager not found: ${it.message}")
        }.getOrNull() ?: return

        val listener = OnNmeaMessageListener::class.java
        callbackHooks.hookRegister(locationManagerClass, "addNmeaListener", listener, listeners)
        callbackHooks.hookUnregister(locationManagerClass, "removeNmeaListener", listener, listeners)
        module.log(Log.INFO, tag, "Instantiated hooks successfully")
    }
}
