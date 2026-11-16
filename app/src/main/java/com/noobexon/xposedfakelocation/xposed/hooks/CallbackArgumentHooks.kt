package com.noobexon.xposedfakelocation.xposed.hooks

import android.util.Log
import io.github.libxposed.api.XposedInterface

/** Hooks register/unregister methods so the platform sees wrappers instead of the app's callbacks. */
internal class CallbackArgumentHooks(
    private val module: XposedInterface,
    private val tag: String,
) {
    /**
     * Registers the wrapper of [cache] instead of the app's callback. A wrapper created for this call is
     * dropped again when the registration returns `false` or throws, so a failed registration doesn't
     * keep the app callback alive.
     */
    fun <T : Any> hookRegister(clazz: Class<*>, methodName: String, callbackType: Class<T>, cache: WrapperCache<T>) {
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
    fun <T : Any> hookUnregister(clazz: Class<*>, methodName: String, callbackType: Class<T>, cache: WrapperCache<T>) {
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
        val name = "${clazz.simpleName}#$methodName"
        val methods = runCatching {
            clazz.declaredMethods.filter { method ->
                method.name == methodName && callbackType in method.parameterTypes
            }
        }.onFailure {
            module.log(Log.ERROR, tag, "Failed looking up $name: ${it.message}")
        }.getOrNull() ?: return
        if (methods.isEmpty()) {
            module.log(Log.WARN, tag, "No $name overload on this API level")
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
                module.log(Log.ERROR, tag, "Failed hooking $name: ${it.message}")
            }
        }
        if (hooked == 0) {
            module.log(Log.ERROR, tag, "No $name overload hooked.")
        } else {
            module.log(Log.INFO, tag, "Hooked $name ($hooked overloads).")
        }
    }
}
