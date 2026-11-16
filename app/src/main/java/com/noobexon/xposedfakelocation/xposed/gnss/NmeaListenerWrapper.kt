package com.noobexon.xposedfakelocation.xposed.gnss

import android.location.OnNmeaMessageListener
import android.os.SystemClock
import android.util.Log
import com.noobexon.xposedfakelocation.gnss.nmea.NmeaEpochRewriter
import com.noobexon.xposedfakelocation.xposed.hooks.gnssSpoofingActive

/**
 * Forwards to the app's NMEA listener. While spoofing, each real sentence is replaced by the fake sentences of
 * [NmeaEpochRewriter], delivered with the real timestamp; the real sentence never reaches the app, even when
 * building the fake one fails. The fix state comes from the shared [GnssSkySource.firstFix] session.
 */
internal class NmeaListenerWrapper(
    private val app: OnNmeaMessageListener,
) : OnNmeaMessageListener {
    private val rewriter = NmeaEpochRewriter()

    override fun onNmeaMessage(message: String, timestamp: Long) {
        NmeaStyleSource.observe(message)
        if (!gnssSpoofingActive()) {
            app.onNmeaMessage(message, timestamp)
            return
        }
        val fake = runCatching {
            rewriter.rewrite(message, timestamp, NmeaStyleSource.style) {
                val fixed = GnssSkySource.firstFix.fixedTtff(SystemClock.elapsedRealtime()) != null
                NmeaFixSource.snapshot(timestamp, fixed)
            }
        }.getOrElse { error ->
            logFailureOnce(error)
            emptyList()
        }
        fake.forEach { app.onNmeaMessage(it, timestamp) }
    }

    private companion object {
        const val TAG = "[NmeaListenerWrapper]"
        @Volatile var failureLogged = false

        fun logFailureOnce(error: Throwable) {
            val log = GnssSkySource.logger ?: return
            if (failureLogged) return
            failureLogged = true
            log(Log.ERROR, TAG, "Building fake NMEA failed: ${error.message}")
        }
    }
}
