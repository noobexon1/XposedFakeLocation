package com.noobexon.xposedfakelocation.gnss.nmea

import com.noobexon.xposedfakelocation.gnss.ChipProfile
import com.noobexon.xposedfakelocation.gnss.Constellation
import kotlin.math.abs

/** NMEA 4.10 signal IDs of each band as Qualcomm chips number them, primary band first. */
internal object NmeaSignals {
    private class Band(val id: String, val megahertz: Double)

    private const val TOLERANCE_MHZ = 5.0

    private val BANDS = mapOf(
        Constellation.GPS to listOf(Band("1", 1575.42), Band("6", 1227.60), Band("8", 1176.45)),
        Constellation.GLONASS to listOf(Band("1", 1602.0), Band("3", 1246.0)),
        Constellation.GALILEO to listOf(Band("7", 1575.42), Band("1", 1176.45), Band("2", 1207.14)),
        Constellation.BEIDOU to listOf(Band("1", 1561.098), Band("3", 1575.42), Band("5", 1176.45), Band("B", 1207.14)),
    )

    val GSV_TALKERS: Map<String, Constellation> = mapOf(
        "GP" to Constellation.GPS,
        "GL" to Constellation.GLONASS,
        "GA" to Constellation.GALILEO,
        "GB" to Constellation.BEIDOU,
        "BD" to Constellation.BEIDOU,
    )

    /** The signal ID of [carrierFrequencyHz]; the primary band when it is unknown or matches no band. */
    fun signalId(constellation: Constellation, carrierFrequencyHz: Float?): String {
        val bands = BANDS.getValue(constellation)
        val megahertz = carrierFrequencyHz?.div(1e6) ?: return bands.first().id
        return bands.firstOrNull { abs(it.megahertz - megahertz) <= TOLERANCE_MHZ }?.id ?: bands.first().id
    }

    /** Position of [signalId] in the output order of [constellation]'s bands. */
    fun order(constellation: Constellation, signalId: String): Int =
        BANDS.getValue(constellation).indexOfFirst { it.id == signalId }

    /**
     * What a real GSV sentence shows of the chip: its system and band (signal ID after the satellite groups,
     * primary band when absent or unknown). `null` for other sentences and systems outside the fake sky.
     */
    fun observation(sentence: NmeaSentence): ChipProfile.Observation? {
        if (sentence.type != "GSV") return null
        val constellation = GSV_TALKERS[sentence.talker] ?: return null
        val signalId = sentence.fields.takeIf { it.size % 4 == 0 }?.last()
        val bands = BANDS.getValue(constellation)
        val band = bands.firstOrNull { it.id == signalId } ?: bands.first()
        return ChipProfile.Observation(constellation.androidType, (band.megahertz * 1e6).toFloat())
    }
}
