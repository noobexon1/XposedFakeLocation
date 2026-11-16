package com.noobexon.xposedfakelocation.xposed.gnss

import android.location.GnssStatus
import android.os.SystemClock
import android.util.Log
import com.noobexon.xposedfakelocation.gnss.ChipProfile
import com.noobexon.xposedfakelocation.gnss.FakeSkyBuilder
import com.noobexon.xposedfakelocation.gnss.FirstFixPolicy
import com.noobexon.xposedfakelocation.gnss.Observer
import com.noobexon.xposedfakelocation.gnss.OrbitCodec
import com.noobexon.xposedfakelocation.gnss.OrbitElements
import com.noobexon.xposedfakelocation.gnss.SkySatellite
import com.noobexon.xposedfakelocation.xposed.utils.LocationUtil
import com.noobexon.xposedfakelocation.xposed.utils.PreferencesUtil
import kotlin.random.Random

/**
 * Process-wide fake sky for the target app: orbits from the remote preferences, the fake position from
 * [LocationUtil], and the chip profile learned from the real statuses passing through the wrappers. One sky
 * is built per chip update and shared by every callback.
 */
internal object GnssSkySource {
    private const val TAG = "[GnssSkySource]"
    private const val SKY_CACHE_MILLIS = 200L

    @Volatile var logger: ((Int, String, String) -> Unit)? = null

    /** The engine's first-fix session, shared by every status wrapper and `getGpsStatus`. */
    val firstFix = FirstFixPolicy(Random.Default)

    private val builder = FakeSkyBuilder(Random.Default)
    private var orbitsVersion = -1L
    private var orbits: List<OrbitElements> = emptyList()
    private var chip: ChipProfile? = null
    private var nmeaChip: ChipProfile? = null
    private var failureLogged = false
    private var cachedSky: List<SkySatellite>? = null
    private var cachedAtMillis = 0L

    @Synchronized
    fun observe(real: GnssStatus) {
        runCatching {
            val observations = (0 until real.satelliteCount).map { index ->
                ChipProfile.Observation(
                    real.getConstellationType(index),
                    if (real.hasCarrierFrequencyHz(index)) real.getCarrierFrequencyHz(index) else null,
                )
            }
            val observed = ChipProfile.fromObservations(observations) ?: return
            chip = chip?.merge(observed) ?: observed
        }
    }

    /** Learns the chip from a real NMEA GSV sentence; used only while no real status was observed. */
    @Synchronized
    fun observeNmea(observation: ChipProfile.Observation) {
        val observed = ChipProfile.fromObservations(listOf(observation)) ?: return
        nmeaChip = nmeaChip?.merge(observed) ?: observed
    }

    @Synchronized
    fun onEngineStarted() = builder.onEngineStarted()

    /**
     * The fake status, with no satellite used in fix unless [fixed]; zero satellites if the sky can't be
     * built, `null` only if no status can be built at all.
     */
    @Synchronized
    fun fakeGnssStatus(fixed: Boolean): GnssStatus? =
        runCatching { GnssStatusFactory.gnssStatus(sky(fixed)) }
            .recoverCatching { error ->
                val log = logger
                if (!failureLogged && log != null) {
                    failureLogged = true
                    log(Log.ERROR, TAG, "Building the fake sky failed: ${error.message}")
                }
                GnssStatusFactory.gnssStatus(emptyList())
            }
            .getOrNull()

    /**
     * The sky built within the last [SKY_CACHE_MILLIS], so drift advances once per chip update and the status
     * and NMEA of one update show the same satellites. Rebuilding it refreshes [LocationUtil].
     */
    @Synchronized
    fun sky(fixed: Boolean): List<SkySatellite> {
        val now = SystemClock.elapsedRealtime()
        val sky = cachedSky?.takeIf { now - cachedAtMillis < SKY_CACHE_MILLIS }
            ?: currentSky().also {
                cachedSky = it
                cachedAtMillis = now
            }
        return if (fixed) sky else sky.map { it.copy(usedInFix = false) }
    }

    private fun currentSky() = run {
        refreshOrbits()
        LocationUtil.updateLocation()
        builder.build(
            orbits,
            Observer(LocationUtil.latitude, LocationUtil.longitude, LocationUtil.altitude),
            System.currentTimeMillis(),
            LocationUtil.accuracy,
            chip ?: nmeaChip ?: ChipProfile.DEFAULT,
        )
    }

    private fun refreshOrbits() {
        val version = PreferencesUtil.getGnssOrbitsVersion()
        if (version == orbitsVersion) return
        orbits = OrbitCodec.decode(PreferencesUtil.getGnssOrbits().orEmpty())
        orbitsVersion = version
    }
}
