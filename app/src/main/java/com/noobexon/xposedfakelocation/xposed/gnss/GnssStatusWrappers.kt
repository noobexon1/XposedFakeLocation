package com.noobexon.xposedfakelocation.xposed.gnss

import android.location.GnssStatus
import android.location.GpsStatus
import android.os.SystemClock
import com.noobexon.xposedfakelocation.gnss.FirstFixPolicy
import com.noobexon.xposedfakelocation.xposed.hooks.gnssSpoofingActive

/**
 * Forwards to the app's status callback, replacing the real sky and first fix while spoofing. The first fix
 * comes from the process-wide [GnssSkySource.firstFix] session, delivered once per engine session.
 */
internal class GnssStatusCallbackWrapper(
    private val app: GnssStatus.Callback,
) : GnssStatus.Callback() {
    @Volatile private var firstFixDelivered = false

    override fun onStarted() {
        firstFixDelivered = false
        GnssSkySource.firstFix.onStarted(SystemClock.elapsedRealtime())
        if (gnssSpoofingActive()) GnssSkySource.onEngineStarted()
        app.onStarted()
    }

    override fun onStopped() {
        firstFixDelivered = false
        GnssSkySource.firstFix.onStopped()
        app.onStopped()
    }

    override fun onFirstFix(ttffMillis: Int) {
        if (!FirstFixPolicy.deliversRealFirstFix(gnssSpoofingActive())) return
        GnssSkySource.firstFix.onRealFirstFix(ttffMillis)
        firstFixDelivered = true
        app.onFirstFix(ttffMillis)
    }

    override fun onSatelliteStatusChanged(status: GnssStatus) {
        GnssSkySource.observe(status)
        if (!gnssSpoofingActive()) {
            app.onSatelliteStatusChanged(status)
            return
        }
        val fixed = GnssSkySource.firstFix.isFixed(SystemClock.elapsedRealtime())
        val fake = GnssSkySource.fakeGnssStatus(fixed) ?: return
        if (fixed && !firstFixDelivered) {
            firstFixDelivered = true
            app.onFirstFix(GnssSkySource.firstFix.timeToFirstFixMillis)
        }
        app.onSatelliteStatusChanged(fake)
    }
}

/**
 * Forwards to the app's legacy listener while spoofing: the real first fix is replaced by one from the
 * shared [GnssSkySource.firstFix] session. The satellites themselves are read through the hooked
 * `LocationManager.getGpsStatus`.
 */
@Suppress("DEPRECATION")
internal class GpsStatusListenerWrapper(
    private val app: GpsStatus.Listener,
) : GpsStatus.Listener {
    @Volatile private var firstFixDelivered = false

    override fun onGpsStatusChanged(event: Int) {
        val spoofing = gnssSpoofingActive()
        when (event) {
            GpsStatus.GPS_EVENT_STARTED -> {
                firstFixDelivered = false
                GnssSkySource.firstFix.onStarted(SystemClock.elapsedRealtime())
                if (spoofing) GnssSkySource.onEngineStarted()
            }
            GpsStatus.GPS_EVENT_STOPPED -> {
                firstFixDelivered = false
                GnssSkySource.firstFix.onStopped()
            }
            GpsStatus.GPS_EVENT_FIRST_FIX -> {
                if (!FirstFixPolicy.deliversRealFirstFix(spoofing)) return
                GnssSkySource.firstFix.onRealFirstFix(null)
                firstFixDelivered = true
            }
            GpsStatus.GPS_EVENT_SATELLITE_STATUS ->
                if (spoofing && !firstFixDelivered && GnssSkySource.firstFix.isFixed(SystemClock.elapsedRealtime())) {
                    firstFixDelivered = true
                    app.onGpsStatusChanged(GpsStatus.GPS_EVENT_FIRST_FIX)
                }
        }
        app.onGpsStatusChanged(event)
    }
}
