package com.noobexon.xposedfakelocation.manager.ui.map.maplibre

import android.content.Context
import android.util.Log
import org.maplibre.android.MapLibre

/** Loads MapLibre on first use, so osmdroid users never load its native code. Main thread only. */
internal object MapLibreSupport {
    private const val TAG = "MapLibreSupport"
    private var available: Boolean? = null

    /** Initializes MapLibre once; `false` if it can't run on this device. */
    fun ensure(context: Context): Boolean {
        available?.let { return it }
        val loaded = try {
            MapLibre.getInstance(context.applicationContext)
            true
        } catch (e: LinkageError) {
            // Native code is missing for this ABI (the APK ships ARM only).
            Log.w(TAG, "MapLibre native code is unavailable", e)
            false
        } catch (e: RuntimeException) {
            Log.w(TAG, "MapLibre failed to initialize", e)
            false
        }
        available = loaded
        return loaded
    }
}
