package com.noobexon.xposedfakelocation.xposed.hooks

import android.location.GnssMeasurementsEvent
import android.location.GnssNavigationMessage
import android.os.Build
import android.util.Log
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

/**
 * App-side GNSS raw-data hooks. While spoofing, target apps see a device without raw GNSS
 * measurements or navigation messages, so they can't compute the real position from them.
 *
 * App callbacks are wrapped at the public `LocationManager` register methods and gated at delivery,
 * so callbacks registered before spoofing started are covered too.
 */
class GnssApiHooks(
    private val module: XposedInterface,
    private val classLoader: ClassLoader,
) {
    private val tag = "[GnssApiHooks]"
    private val callbackHooks = CallbackArgumentHooks(module, tag)
    private val measurementWrappers = WrapperCache<GnssMeasurementsEvent.Callback>(
        isWrapper = { it is MeasurementsCallbackWrapper },
        create = ::MeasurementsCallbackWrapper,
    )
    private val navigationWrappers = WrapperCache<GnssNavigationMessage.Callback>(
        isWrapper = { it is NavigationMessageCallbackWrapper },
        create = ::NavigationMessageCallbackWrapper,
    )

    fun init() {
        val locationManagerClass = runCatching {
            Class.forName("android.location.LocationManager", false, classLoader)
        }.onFailure {
            module.log(Log.ERROR, tag, "LocationManager not found: ${it.message}")
        }.getOrNull() ?: return

        val measurementsCallback = GnssMeasurementsEvent.Callback::class.java
        val navigationCallback = GnssNavigationMessage.Callback::class.java
        callbackHooks.hookRegister(locationManagerClass, "registerGnssMeasurementsCallback", measurementsCallback, measurementWrappers)
        callbackHooks.hookUnregister(locationManagerClass, "unregisterGnssMeasurementsCallback", measurementsCallback, measurementWrappers)
        callbackHooks.hookRegister(locationManagerClass, "registerGnssNavigationMessageCallback", navigationCallback, navigationWrappers)
        callbackHooks.hookUnregister(locationManagerClass, "unregisterGnssNavigationMessageCallback", navigationCallback, navigationWrappers)
        hookCapabilities()
        module.log(Log.INFO, tag, "Instantiated hooks successfully")
    }

    /** Reports no raw-data support from `GnssCapabilities` (API 30+) while spoofing. */
    private fun hookCapabilities() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val capabilitiesClass = runCatching {
            Class.forName("android.location.GnssCapabilities", false, classLoader)
        }.onFailure {
            module.log(Log.ERROR, tag, "GnssCapabilities not found: ${it.message}")
        }.getOrNull() ?: return
        val methods = runCatching { capabilitiesClass.declaredMethods }.onFailure {
            module.log(Log.ERROR, tag, "Failed listing GnssCapabilities methods: ${it.message}")
        }.getOrNull() ?: return

        var hooked = 0
        methods.filter { it.parameterTypes.isEmpty() }.forEach { method ->
            val hookedMethod = when {
                method.name in GnssHookPolicy.UNSUPPORTED_CAPABILITIES && method.returnType == java.lang.Boolean.TYPE ->
                    hookCapability(method) { real ->
                        GnssHookPolicy.capability(method.name, gnssSpoofingActive(), real as Boolean)
                    }
                method.name == GnssHookPolicy.ACCUMULATED_DELTA_RANGE_GETTER && method.returnType == java.lang.Integer.TYPE ->
                    hookCapability(method) { real ->
                        GnssHookPolicy.accumulatedDeltaRange(gnssSpoofingActive(), real as Int)
                    }
                else -> false
            }
            if (hookedMethod) hooked++
        }
        if (hooked == 0) module.log(Log.WARN, tag, "No GnssCapabilities getter hooked.")
    }

    /** Replaces the result of the no-arg `GnssCapabilities` getter [method] with [transform] of its real result. */
    private fun hookCapability(method: Method, transform: (Any?) -> Any?): Boolean =
        runCatching {
            module.hook(method).intercept { chain -> transform(chain.proceed()) }
            module.log(Log.INFO, tag, "Hooked GnssCapabilities#${method.name}.")
        }.onFailure {
            module.log(Log.ERROR, tag, "Failed hooking GnssCapabilities#${method.name}: ${it.message}")
        }.isSuccess
}
