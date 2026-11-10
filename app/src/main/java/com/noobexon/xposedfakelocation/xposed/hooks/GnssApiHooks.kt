package com.noobexon.xposedfakelocation.xposed.hooks

import android.location.GnssMeasurementsEvent
import android.location.GnssNavigationMessage
import android.util.Log
import io.github.libxposed.api.XposedInterface
import java.util.WeakHashMap

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
    private val measurementWrappers = WeakHashMap<GnssMeasurementsEvent.Callback, MeasurementsCallbackWrapper>()
    private val navigationWrappers = WeakHashMap<GnssNavigationMessage.Callback, NavigationMessageCallbackWrapper>()

    fun init() {
        val locationManagerClass = runCatching {
            Class.forName("android.location.LocationManager", false, classLoader)
        }.onFailure {
            module.log(Log.ERROR, tag, "LocationManager not found: ${it.message}")
        }.getOrNull() ?: return

        val measurementsCallback = GnssMeasurementsEvent.Callback::class.java
        val navigationCallback = GnssNavigationMessage.Callback::class.java
        hookCallbackArgument(locationManagerClass, "registerGnssMeasurementsCallback", measurementsCallback, ::measurementWrapper)
        hookCallbackArgument(locationManagerClass, "unregisterGnssMeasurementsCallback", measurementsCallback, ::releaseMeasurementWrapper)
        hookCallbackArgument(locationManagerClass, "registerGnssNavigationMessageCallback", navigationCallback, ::navigationWrapper)
        hookCallbackArgument(locationManagerClass, "unregisterGnssNavigationMessageCallback", navigationCallback, ::releaseNavigationWrapper)
        hookCapabilities()
        module.log(Log.INFO, tag, "Instantiated hooks successfully")
    }

    private fun measurementWrapper(callback: GnssMeasurementsEvent.Callback): GnssMeasurementsEvent.Callback =
        if (callback is MeasurementsCallbackWrapper) callback
        else synchronized(measurementWrappers) { measurementWrappers.getOrPut(callback) { MeasurementsCallbackWrapper(callback) } }

    /** The wrapper registered for [callback], dropped from the cache since it is being unregistered. */
    private fun releaseMeasurementWrapper(callback: GnssMeasurementsEvent.Callback): GnssMeasurementsEvent.Callback =
        synchronized(measurementWrappers) { measurementWrappers.remove(callback) } ?: callback

    private fun navigationWrapper(callback: GnssNavigationMessage.Callback): GnssNavigationMessage.Callback =
        if (callback is NavigationMessageCallbackWrapper) callback
        else synchronized(navigationWrappers) { navigationWrappers.getOrPut(callback) { NavigationMessageCallbackWrapper(callback) } }

    /** The wrapper registered for [callback], dropped from the cache since it is being unregistered. */
    private fun releaseNavigationWrapper(callback: GnssNavigationMessage.Callback): GnssNavigationMessage.Callback =
        synchronized(navigationWrappers) { navigationWrappers.remove(callback) } ?: callback

    /** Hooks every overload of [methodName] taking a [callbackType] and replaces that argument with [replace]. */
    private fun <T : Any> hookCallbackArgument(
        clazz: Class<*>,
        methodName: String,
        callbackType: Class<T>,
        replace: (T) -> T,
    ) {
        val methods = runCatching {
            clazz.declaredMethods.filter { method ->
                method.name == methodName && callbackType in method.parameterTypes
            }
        }.onFailure {
            module.log(Log.ERROR, tag, "Failed looking up LocationManager#$methodName: ${it.message}")
        }.getOrNull() ?: return
        if (methods.isEmpty()) {
            module.log(Log.WARN, tag, "No $methodName overload on this API level")
            return
        }
        var hooked = 0
        methods.forEach { method ->
            runCatching {
                val index = method.parameterTypes.indexOf(callbackType)
                module.hook(method).intercept { chain ->
                    val callback = chain.args[index]
                    if (callbackType.isInstance(callback)) {
                        val args = chain.args.toTypedArray()
                        args[index] = replace(callbackType.cast(callback)!!)
                        chain.proceed(args)
                    } else {
                        chain.proceed()
                    }
                }
                hooked++
            }.onFailure {
                module.log(Log.ERROR, tag, "Failed hooking LocationManager#$methodName: ${it.message}")
            }
        }
        if (hooked == 0) {
            module.log(Log.ERROR, tag, "No LocationManager#$methodName overload hooked.")
        } else {
            module.log(Log.INFO, tag, "Hooked LocationManager#$methodName ($hooked overloads).")
        }
    }

    private fun hookCapabilities() = Unit
}
