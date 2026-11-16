package com.noobexon.xposedfakelocation.gnss

/**
 * Constellations and carrier frequencies the real chip reports, mirrored by the fake sky. GLONASS is
 * recorded by band ([GLONASS_L1_HZ] / [GLONASS_L2_HZ]); each fake GLONASS satellite gets its own channel.
 */
data class ChipProfile(
    val frequenciesHz: Map<Constellation, Set<Float>>,
    val reportsFrequencies: Boolean,
) {
    /** One satellite entry of a real `GnssStatus`. */
    data class Observation(val androidConstellationType: Int, val carrierFrequencyHz: Float?)

    fun merge(other: ChipProfile): ChipProfile = ChipProfile(
        frequenciesHz = (frequenciesHz.keys + other.frequenciesHz.keys).associateWith {
            frequenciesHz[it].orEmpty() + other.frequenciesHz[it].orEmpty()
        },
        reportsFrequencies = reportsFrequencies || other.reportsFrequencies,
    )

    companion object {
        const val GPS_L1_HZ = 1_575_420_000f
        const val GLONASS_L1_HZ = 1_602_000_000f
        const val GLONASS_L2_HZ = 1_246_000_000f
        private const val GLONASS_BAND_SPLIT_HZ = 1_400_000_000f

        /** Used until the real chip has reported a status. */
        val DEFAULT = ChipProfile(
            mapOf(Constellation.GPS to setOf(GPS_L1_HZ), Constellation.GLONASS to setOf(GLONASS_L1_HZ)),
            reportsFrequencies = true,
        )

        /** `null` when no supported constellation was observed. */
        fun fromObservations(observations: List<Observation>): ChipProfile? {
            val supported = observations.mapNotNull { observation ->
                Constellation.fromAndroidType(observation.androidConstellationType)?.let { it to observation.carrierFrequencyHz }
            }
            if (supported.isEmpty()) return null
            val frequencies = supported
                .groupBy({ it.first }, { it.second })
                .mapValues { (constellation, values) ->
                    values.filterNotNull().map { if (constellation == Constellation.GLONASS) glonassBand(it) else it }.toSet()
                }
            return ChipProfile(frequencies, reportsFrequencies = supported.any { it.second != null })
        }

        private fun glonassBand(hz: Float): Float = if (hz < GLONASS_BAND_SPLIT_HZ) GLONASS_L2_HZ else GLONASS_L1_HZ
    }
}
