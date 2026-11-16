package com.noobexon.xposedfakelocation.xposed.gnss

import android.location.GnssStatus
import android.location.GpsStatus
import android.os.Build
import androidx.annotation.RequiresApi
import com.noobexon.xposedfakelocation.gnss.LegacyStatusEncoding
import com.noobexon.xposedfakelocation.gnss.SkySatellite

/** Turns a fake sky into platform `GnssStatus` / `GpsStatus` objects on every supported API level. */
@Suppress("DEPRECATION")
internal object GnssStatusFactory {
    private val legacyConstructor by lazy {
        GnssStatus::class.java.getDeclaredConstructor(
            Int::class.javaPrimitiveType,
            IntArray::class.java,
            FloatArray::class.java,
            FloatArray::class.java,
            FloatArray::class.java,
            FloatArray::class.java,
        ).apply { isAccessible = true }
    }
    private val gpsStatusConstructor by lazy {
        GpsStatus::class.java.getDeclaredConstructor().apply { isAccessible = true }
    }
    private val gpsStatusSetStatus by lazy {
        GpsStatus::class.java.getDeclaredMethod("setStatus", GnssStatus::class.java, Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
    }

    fun gnssStatus(sky: List<SkySatellite>): GnssStatus =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) build(sky) else construct(sky)

    /**
     * Fills [target] when given (callers may ignore the return value); otherwise, or when the hidden
     * `setStatus` is not reachable on Android 11+, creates a new status.
     */
    fun gpsStatus(target: GpsStatus?, status: GnssStatus, ttffMillis: Int): GpsStatus {
        if (target != null) {
            val filled = runCatching { gpsStatusSetStatus.invoke(target, status, ttffMillis) }
            if (filled.isSuccess || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return target.also { filled.getOrThrow() }
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            GpsStatus.create(status, ttffMillis)
        } else {
            gpsStatusConstructor.newInstance().also { gpsStatusSetStatus.invoke(it, status, ttffMillis) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun build(sky: List<SkySatellite>): GnssStatus = GnssStatus.Builder().apply {
        sky.forEach { satellite ->
            addSatellite(
                satellite.constellation.androidType,
                satellite.svid,
                satellite.cn0DbHz,
                satellite.elevationDeg,
                satellite.azimuthDeg,
                satellite.hasEphemeris,
                satellite.hasAlmanac,
                satellite.usedInFix,
                satellite.carrierFrequencyHz != null,
                satellite.carrierFrequencyHz ?: 0f,
                false,
                0f,
            )
        }
    }.build()

    private fun construct(sky: List<SkySatellite>): GnssStatus = legacyConstructor.newInstance(
        sky.size,
        IntArray(sky.size) { LegacyStatusEncoding.svidWithFlags(sky[it]) },
        FloatArray(sky.size) { sky[it].cn0DbHz },
        FloatArray(sky.size) { sky[it].elevationDeg },
        FloatArray(sky.size) { sky[it].azimuthDeg },
        FloatArray(sky.size) { sky[it].carrierFrequencyHz ?: 0f },
    )
}
