package com.noobexon.xposedfakelocation.xposed.hooks

import java.util.WeakHashMap

/** The wrappers handed to the platform in place of the app's callbacks, keyed by the app's callback. */
internal class WrapperCache<T : Any>(
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
