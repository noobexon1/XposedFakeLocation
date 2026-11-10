package com.noobexon.xposedfakelocation.xposed.hooks

import android.location.GnssMeasurementsEvent
import android.location.GnssNavigationMessage
import android.os.Build
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
        hookRegister(locationManagerClass, "registerGnssMeasurementsCallback", measurementsCallback, measurementWrappers)
        hookUnregister(locationManagerClass, "unregisterGnssMeasurementsCallback", measurementsCallback, measurementWrappers)
        hookRegister(locationManagerClass, "registerGnssNavigationMessageCallback", navigationCallback, navigationWrappers)
        hookUnregister(locationManagerClass, "unregisterGnssNavigationMessageCallback", navigationCallback, navigationWrappers)
        hookCapabilities()
        module.log(Log.INFO, tag, "Instantiated hooks successfully")
    }

    /**
     * Registers the wrapper of [cache] instead of the app's callback. A wrapper created for this call is
     * dropped again when the registration returns `false` or throws, so a failed registration doesn't
     * keep the app callback alive.
     */
    private fun <T : Any> hookRegister(clazz: Class<*>, methodName: String, callbackType: Class<T>, cache: WrapperCache<T>) {
        hookCallbackArgument(clazz, methodName, callbackType) { callback, proceed ->
            val (wrapper, created) = cache.acquire(callback)
            try {
                proceed(wrapper).also { result -> if (created && result == false) cache.discard(callback) }
            } catch (t: Throwable) {
                if (created) cache.discard(callback)
                throw t
            }
        }
    }

    /** Unregisters the wrapper of [cache] that was registered for the app's callback. */
    private fun <T : Any> hookUnregister(clazz: Class<*>, methodName: String, callbackType: Class<T>, cache: WrapperCache<T>) {
        hookCallbackArgument(clazz, methodName, callbackType) { callback, proceed -> proceed(cache.release(callback)) }
    }

    /**
     * Hooks every overload of [methodName] taking a [callbackType]. [onCall] gets that argument and a
     * `proceed` function that runs the original method with the argument replaced.
     */
    private fun <T : Any> hookCallbackArgument(
        clazz: Class<*>,
        methodName: String,
        callbackType: Class<T>,
        onCall: (callback: T, proceed: (replacement: T) -> Any?) -> Any?,
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
                        onCall(callbackType.cast(callback)!!) { replacement ->
                            val args = chain.args.toTypedArray()
                            args[index] = replacement
                            chain.proceed(args)
                        }
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

        methods
            .filter { method ->
                method.name in GnssHookPolicy.UNSUPPORTED_CAPABILITIES &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == java.lang.Boolean.TYPE
            }
            .forEach { method ->
                runCatching {
                    module.hook(method).intercept { chain ->
                        val real = chain.proceed() as Boolean
                        GnssHookPolicy.capability(method.name, gnssSpoofingActive(), real)
                    }
                    module.log(Log.INFO, tag, "Hooked GnssCapabilities#${method.name}.")
                }.onFailure {
                    module.log(Log.ERROR, tag, "Failed hooking GnssCapabilities#${method.name}: ${it.message}")
                }
            }
    }
}

/** The wrappers handed to the platform in place of the app's callbacks, keyed by the app's callback. */
private class WrapperCache<T : Any>(
    private val isWrapper: (T) -> Boolean,
    private val create: (T) -> T,
) {
    private val wrappers = WeakHashMap<T, T>()

    /** The wrapper to register for [callback], and whether it was created by this call. */
    fun acquire(callback: T): Pair<T, Boolean> {
        if (isWrapper(callback)) return callback to false
        synchronized(wrappers) {
            wrappers[callback]?.let { return it to false }
            return create(callback).also { wrappers[callback] = it } to true
        }
    }

    /** Forgets the wrapper created for [callback]. */
    fun discard(callback: T) {
        synchronized(wrappers) { wrappers.remove(callback) }
    }

    /** The wrapper registered for [callback], dropped from the cache since it is being unregistered. */
    fun release(callback: T): T = synchronized(wrappers) { wrappers.remove(callback) } ?: callback
}
