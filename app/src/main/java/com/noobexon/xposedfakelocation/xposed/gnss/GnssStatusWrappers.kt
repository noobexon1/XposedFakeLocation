package com.noobexon.xposedfakelocation.xposed.gnss

import android.location.GnssStatus
import android.location.GpsStatus
import android.os.SystemClock
import com.noobexon.xposedfakelocation.gnss.FirstFixPolicy
import com.noobexon.xposedfakelocation.xposed.hooks.gnssSpoofingActive
import kotlin.random.Random

/** Forwards to the app's status callback, replacing the real sky and first fix while spoofing. */
internal class GnssStatusCallbackWrapper(
    private val app: GnssStatus.Callback,
) : GnssStatus.Callback() {
    private val firstFix = FirstFixPolicy(Random.Default)

    override fun onStarted() {
        firstFix.onStarted(SystemClock.elapsedRealtime())
        if (gnssSpoofingActive()) GnssSkySource.onEngineStarted()
        app.onStarted()
    }

    override fun onStopped() {
        firstFix.onStopped()
        app.onStopped()
    }

    override fun onFirstFix(ttffMillis: Int) {
        if (FirstFixPolicy.deliversRealFirstFix(gnssSpoofingActive())) app.onFirstFix(ttffMillis)
    }

    override fun onSatelliteStatusChanged(status: GnssStatus) {
        GnssSkySource.observe(status)
        if (!gnssSpoofingActive()) {
            app.onSatelliteStatusChanged(status)
            return
        }
        val fake = GnssSkySource.fakeGnssStatus() ?: return
        firstFix.firstFixDue(SystemClock.elapsedRealtime())?.let { ttff ->
            GnssSkySource.timeToFirstFixMillis = ttff
            app.onFirstFix(ttff)
        }
        app.onSatelliteStatusChanged(fake)
    }
}

/**
 * Forwards to the app's legacy listener while spoofing: the real first fix is replaced by a synthesized one.
 * The satellites themselves are read through the hooked `LocationManager.getGpsStatus`.
 */
@Suppress("DEPRECATION")
internal class GpsStatusListenerWrapper(
    private val app: GpsStatus.Listener,
) : GpsStatus.Listener {
    private val firstFix = FirstFixPolicy(Random.Default)

    override fun onGpsStatusChanged(event: Int) {
        val spoofing = gnssSpoofingActive()
        when (event) {
            GpsStatus.GPS_EVENT_STARTED -> {
                firstFix.onStarted(SystemClock.elapsedRealtime())
                if (spoofing) GnssSkySource.onEngineStarted()
            }
            GpsStatus.GPS_EVENT_STOPPED -> firstFix.onStopped()
            GpsStatus.GPS_EVENT_FIRST_FIX -> if (!FirstFixPolicy.deliversRealFirstFix(spoofing)) return
            GpsStatus.GPS_EVENT_SATELLITE_STATUS -> if (spoofing) {
                firstFix.firstFixDue(SystemClock.elapsedRealtime())?.let { ttff ->
                    GnssSkySource.timeToFirstFixMillis = ttff
                    app.onGpsStatusChanged(GpsStatus.GPS_EVENT_FIRST_FIX)
                }
            }
        }
        app.onGpsStatusChanged(event)
    }
}
