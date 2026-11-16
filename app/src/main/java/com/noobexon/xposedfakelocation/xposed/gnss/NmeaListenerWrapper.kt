package com.noobexon.xposedfakelocation.xposed.gnss

import android.location.OnNmeaMessageListener
import android.os.SystemClock
import android.util.Log
import com.noobexon.xposedfakelocation.gnss.nmea.NmeaEpochRewriter
import com.noobexon.xposedfakelocation.xposed.hooks.gnssSpoofingActive
import kotlin.math.abs

/**
 * Forwards to the app's NMEA listener. While spoofing, each real sentence is replaced by the fake sentences of
 * [NmeaEpochRewriter], delivered with the real timestamp; the real sentence never reaches the app, even when
 * building the fake one fails. The fix state comes from the shared [GnssSkySource.firstFix] session (a sentence
 * trailing the engine stop doesn't fix it again).
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
                val fixed = GnssSkySource.firstFix.fixedTtff(SystemClock.elapsedRealtime(), recoverStopped = false) != null
                NmeaFixSource.snapshot(utcMillis(timestamp), fixed)
            }
        }.getOrElse { error ->
            logFailureOnce(error)
            emptyList()
        }
        fake.forEach { app.onNmeaMessage(it, timestamp) }
    }

    private companion object {
        const val TAG = "[NmeaListenerWrapper]"
        const val MAX_CLOCK_SKEW_MILLIS = 10_000L
        @Volatile var failureLogged = false

        /** The sentence's timestamp, or the system clock when the HAL's timestamp is far off (0 or stale). */
        fun utcMillis(timestamp: Long): Long {
            val now = System.currentTimeMillis()
            return if (abs(timestamp - now) <= MAX_CLOCK_SKEW_MILLIS) timestamp else now
        }

        fun logFailureOnce(error: Throwable) {
            val log = GnssSkySource.logger ?: return
            if (failureLogged) return
            failureLogged = true
            log(Log.ERROR, TAG, "Building fake NMEA failed: ${error.message}")
        }
    }
}
