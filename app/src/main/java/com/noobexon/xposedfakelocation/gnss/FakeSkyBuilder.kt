package com.noobexon.xposedfakelocation.gnss

import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Builds the fake sky for a position and time: every satellite above the horizon, C/N0 from elevation and
 * the spoofed accuracy with a slow per-satellite drift, and a stable used-in-fix set sized by accuracy.
 * Keeps drift and used-in-fix state between calls; not thread-safe.
 */
class FakeSkyBuilder(private val random: Random) {
    private val drift = HashMap<Pair<Constellation, Int>, Double>()
    private var usedKeys: Set<Pair<Constellation, Int>> = emptySet()
    private var accuracyBand: AccuracyBand? = null
    private var targetUsed: Int? = null

    /** A new engine session picks its used-in-fix count again. */
    fun onEngineStarted() {
        targetUsed = null
        usedKeys = emptySet()
    }

    fun build(
        elements: List<OrbitElements>,
        observer: Observer,
        timeMillis: Long,
        accuracyMeters: Float,
        chip: ChipProfile,
    ): List<SkySatellite> {
        val looks = elements
            .filter { abs(timeMillis - it.epochMillis) <= MAX_ELEMENT_AGE_MILLIS }
            .distinctBy { it.key }
            .map { it to LookAngles.compute(observer, OrbitPropagator.ecef(it, timeMillis)) }
        return buildFromLooks(looks, accuracyMeters, chip)
    }

    internal fun buildFromLooks(
        looks: List<Pair<OrbitElements, LookAngle>>,
        accuracyMeters: Float,
        chip: ChipProfile,
    ): List<SkySatellite> {
        val band = AccuracyBand.of(accuracyMeters)
        if (band != accuracyBand) {
            accuracyBand = band
            targetUsed = null
        }
        val target = targetUsed ?: band.usedRange.random(random).also { targetUsed = it }

        val visible = looks.filter { (elements, look) ->
            look.elevationDeg > 0 &&
                elements.constellation in chip.frequenciesHz &&
                (elements.constellation != Constellation.GLONASS || elements.glonassChannel != null)
        }
        val cn0 = visible.associate { (elements, look) -> elements.key to cn0(elements.key, look.elevationDeg, band) }
        usedKeys = selectUsed(visible, cn0, target)
        return visible.flatMap { (elements, look) -> entries(elements, look, cn0.getValue(elements.key), chip) }
    }

    private fun cn0(key: Pair<Constellation, Int>, elevationDeg: Double, band: AccuracyBand): Double {
        val next = ((drift[key] ?: 0.0) + random.nextDouble(-DRIFT_STEP, DRIFT_STEP)).coerceIn(-DRIFT_MAX, DRIFT_MAX)
        drift[key] = next
        return (CN0_BASE + CN0_ELEVATION_GAIN * sin(Math.toRadians(elevationDeg)) - band.cn0Penalty + next)
            .coerceIn(CN0_MIN, CN0_MAX)
    }

    /** Keeps used satellites that are still candidates, then fills up round-robin across constellations by C/N0. */
    private fun selectUsed(
        visible: List<Pair<OrbitElements, LookAngle>>,
        cn0: Map<Pair<Constellation, Int>, Double>,
        target: Int,
    ): Set<Pair<Constellation, Int>> {
        val candidates = visible.filter { it.second.elevationDeg > USED_MIN_ELEVATION_DEG }.map { it.first.key }
        val selected = usedKeys.filter { it in candidates }.take(target).toMutableList()
        val queues = candidates
            .filterNot { it in selected }
            .sortedByDescending { cn0.getValue(it) }
            .groupBy { it.first }
            .values
            .map { ArrayDeque(it) }
        while (selected.size < target && queues.any { it.isNotEmpty() }) {
            queues.forEach { queue -> if (selected.size < target) queue.removeFirstOrNull()?.let(selected::add) }
        }
        return selected.toSet()
    }

    private fun entries(elements: OrbitElements, look: LookAngle, primaryCn0: Double, chip: ChipProfile): List<SkySatellite> =
        frequencies(elements, chip).mapIndexed { index, frequency ->
            SkySatellite(
                constellation = elements.constellation,
                svid = elements.svid,
                cn0DbHz = (primaryCn0 - if (index == 0) 0.0 else SECONDARY_BAND_PENALTY).coerceIn(CN0_MIN, CN0_MAX).toFloat(),
                elevationDeg = look.elevationDeg.toFloat(),
                azimuthDeg = look.azimuthDeg.toFloat(),
                hasEphemeris = look.elevationDeg > EPHEMERIS_MIN_ELEVATION_DEG,
                hasAlmanac = true,
                usedInFix = elements.key in usedKeys,
                carrierFrequencyHz = frequency,
            )
        }

    /** Primary (highest) frequency first. */
    private fun frequencies(elements: OrbitElements, chip: ChipProfile): List<Float?> {
        val recorded = chip.frequenciesHz[elements.constellation].orEmpty().sortedDescending()
        if (!chip.reportsFrequencies || recorded.isEmpty()) return listOf(null)
        if (elements.constellation != Constellation.GLONASS) return recorded
        val channel = elements.glonassChannel ?: return emptyList()
        return recorded.map { band ->
            if (band == ChipProfile.GLONASS_L2_HZ) band + channel * GLONASS_L2_STEP_HZ else band + channel * GLONASS_L1_STEP_HZ
        }
    }

    private val OrbitElements.key: Pair<Constellation, Int> get() = constellation to svid

    internal enum class AccuracyBand(val usedRange: IntRange, val cn0Penalty: Double) {
        GOOD(10..14, 0.0),
        MEDIUM(6..9, 2.0),
        POOR(4..6, 5.0);

        companion object {
            fun of(accuracyMeters: Float): AccuracyBand = when {
                accuracyMeters <= 5f -> GOOD
                accuracyMeters <= 15f -> MEDIUM
                else -> POOR
            }
        }
    }

    private companion object {
        const val MAX_ELEMENT_AGE_MILLIS = 30L * 24 * 60 * 60 * 1000
        const val EPHEMERIS_MIN_ELEVATION_DEG = 5.0
        const val USED_MIN_ELEVATION_DEG = 10.0
        const val CN0_BASE = 25.0
        const val CN0_ELEVATION_GAIN = 20.0
        const val CN0_MIN = 15.0
        const val CN0_MAX = 50.0
        const val SECONDARY_BAND_PENALTY = 3.0
        const val DRIFT_STEP = 0.3
        const val DRIFT_MAX = 2.0
        const val GLONASS_L1_STEP_HZ = 562_500f
        const val GLONASS_L2_STEP_HZ = 437_500f
    }
}
