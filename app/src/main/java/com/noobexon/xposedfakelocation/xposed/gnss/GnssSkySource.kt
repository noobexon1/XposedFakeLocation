package com.noobexon.xposedfakelocation.xposed.gnss

import android.location.GnssStatus
import android.util.Log
import com.noobexon.xposedfakelocation.gnss.ChipProfile
import com.noobexon.xposedfakelocation.gnss.FakeSkyBuilder
import com.noobexon.xposedfakelocation.gnss.Observer
import com.noobexon.xposedfakelocation.gnss.OrbitCodec
import com.noobexon.xposedfakelocation.gnss.OrbitElements
import com.noobexon.xposedfakelocation.xposed.utils.LocationUtil
import com.noobexon.xposedfakelocation.xposed.utils.PreferencesUtil
import kotlin.random.Random

/**
 * Process-wide fake sky for the target app: orbits from the remote preferences, the fake position from
 * [LocationUtil], and the chip profile learned from the real statuses passing through the wrappers.
 */
internal object GnssSkySource {
    private const val TAG = "[GnssSkySource]"

    @Volatile var logger: ((Int, String, String) -> Unit)? = null

    /** TTFF last reported through a synthesized first fix, for `getGpsStatus`. */
    @Volatile var timeToFirstFixMillis: Int = 0

    private val builder = FakeSkyBuilder(Random.Default)
    private var orbitsVersion = -1L
    private var orbits: List<OrbitElements> = emptyList()
    private var chip: ChipProfile? = null
    private var failureLogged = false

    @Synchronized
    fun observe(real: GnssStatus) {
        val observations = (0 until real.satelliteCount).map { index ->
            ChipProfile.Observation(
                real.getConstellationType(index),
                if (real.hasCarrierFrequencyHz(index)) real.getCarrierFrequencyHz(index) else null,
            )
        }
        val observed = ChipProfile.fromObservations(observations) ?: return
        chip = chip?.merge(observed) ?: observed
    }

    @Synchronized
    fun onEngineStarted() = builder.onEngineStarted()

    /** The fake status; zero satellites if the sky can't be built, `null` only if no status can be built at all. */
    @Synchronized
    fun fakeGnssStatus(): GnssStatus? =
        runCatching { GnssStatusFactory.gnssStatus(currentSky()) }
            .recoverCatching { error ->
                if (!failureLogged) {
                    failureLogged = true
                    logger?.invoke(Log.ERROR, TAG, "Building the fake sky failed: ${error.message}")
                }
                GnssStatusFactory.gnssStatus(emptyList())
            }
            .getOrNull()

    private fun currentSky() = run {
        refreshOrbits()
        LocationUtil.updateLocation()
        builder.build(
            orbits,
            Observer(LocationUtil.latitude, LocationUtil.longitude, LocationUtil.altitude),
            System.currentTimeMillis(),
            LocationUtil.accuracy,
            chip ?: ChipProfile.DEFAULT,
        )
    }

    private fun refreshOrbits() {
        val version = PreferencesUtil.getGnssOrbitsVersion()
        if (version == orbitsVersion) return
        orbits = OrbitCodec.decode(PreferencesUtil.getGnssOrbits().orEmpty())
        orbitsVersion = version
    }
}
