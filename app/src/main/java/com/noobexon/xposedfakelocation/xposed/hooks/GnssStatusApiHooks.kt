package com.noobexon.xposedfakelocation.xposed.hooks

import android.location.GnssStatus
import android.location.GpsStatus
import android.util.Log
import com.noobexon.xposedfakelocation.xposed.gnss.GnssSkySource
import com.noobexon.xposedfakelocation.xposed.gnss.GnssStatusCallbackWrapper
import com.noobexon.xposedfakelocation.xposed.gnss.GnssStatusFactory
import com.noobexon.xposedfakelocation.xposed.gnss.GpsStatusListenerWrapper
import io.github.libxposed.api.XposedInterface

/**
 * App-side satellite status hooks. While spoofing, target apps see a sky computed from real orbits for the
 * fake position instead of the real one, through `GnssStatus.Callback`, the legacy `GpsStatus.Listener` and
 * `LocationManager.getGpsStatus`.
 */
@Suppress("DEPRECATION")
class GnssStatusApiHooks(
    private val module: XposedInterface,
    private val classLoader: ClassLoader,
) {
    private val tag = "[GnssStatusApiHooks]"
    private val callbackHooks = CallbackArgumentHooks(module, tag)
    private val statusCallbacks = WrapperCache<GnssStatus.Callback>(
        isWrapper = { it is GnssStatusCallbackWrapper },
        create = ::GnssStatusCallbackWrapper,
    )
    private val gpsStatusListeners = WrapperCache<GpsStatus.Listener>(
        isWrapper = { it is GpsStatusListenerWrapper },
        create = ::GpsStatusListenerWrapper,
    )
    @Volatile private var gpsStatusFailureLogged = false

    fun init() {
        GnssSkySource.logger = { priority, logTag, message -> module.log(priority, logTag, message) }
        val locationManagerClass = runCatching {
            Class.forName("android.location.LocationManager", false, classLoader)
        }.onFailure {
            module.log(Log.ERROR, tag, "LocationManager not found: ${it.message}")
        }.getOrNull() ?: return

        val statusCallback = GnssStatus.Callback::class.java
        val gpsStatusListener = GpsStatus.Listener::class.java
        callbackHooks.hookRegister(locationManagerClass, "registerGnssStatusCallback", statusCallback, statusCallbacks)
        callbackHooks.hookUnregister(locationManagerClass, "unregisterGnssStatusCallback", statusCallback, statusCallbacks)
        callbackHooks.hookRegister(locationManagerClass, "addGpsStatusListener", gpsStatusListener, gpsStatusListeners)
        callbackHooks.hookUnregister(locationManagerClass, "removeGpsStatusListener", gpsStatusListener, gpsStatusListeners)
        hookGetGpsStatus(locationManagerClass)
        module.log(Log.INFO, tag, "Instantiated hooks successfully")
    }

    /**
     * Returns the fake sky from `getGpsStatus` while spoofing. The original runs first, so its exceptions
     * (apps targeting Android 12+) reach the app unchanged; a fake that can't be built returns `null`.
     */
    private fun hookGetGpsStatus(locationManagerClass: Class<*>) {
        runCatching {
            val method = locationManagerClass.getDeclaredMethod("getGpsStatus", GpsStatus::class.java)
            module.hook(method).intercept { chain ->
                val real = chain.proceed()
                if (!gnssSpoofingActive()) {
                    real
                } else {
                    runCatching {
                        val target = (real ?: chain.args[0]) as GpsStatus?
                        GnssSkySource.fakeGnssStatus()?.let { fake ->
                            GnssStatusFactory.gpsStatus(target, fake, GnssSkySource.timeToFirstFixMillis)
                        }
                    }.onFailure {
                        if (!gpsStatusFailureLogged) {
                            gpsStatusFailureLogged = true
                            module.log(Log.ERROR, tag, "Building the fake GpsStatus failed: ${it.message}")
                        }
                    }.getOrNull()
                }
            }
            module.log(Log.INFO, tag, "Hooked LocationManager#getGpsStatus.")
        }.onFailure {
            module.log(Log.ERROR, tag, "Failed hooking LocationManager#getGpsStatus: ${it.message}")
        }
    }
}
