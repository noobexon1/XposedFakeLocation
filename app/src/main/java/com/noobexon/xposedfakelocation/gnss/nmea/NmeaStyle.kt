package com.noobexon.xposedfakelocation.gnss.nmea

import com.noobexon.xposedfakelocation.gnss.Constellation

/**
 * How the chip writes NMEA, learned from its real sentences so the fake ones look the same: the talker of
 * each system, the decimals of latitude/longitude minutes, the length of the GNS mode string and the datum
 * offsets of DTM. [DEFAULT] is a Qualcomm chip (OnePlus 6T, Android 11).
 */
data class NmeaStyle(
    val talkers: Map<Constellation, String>,
    val minuteDecimals: Int,
    val gnsModeLength: Int,
    val dtmFields: List<String>,
) {
    fun talker(constellation: Constellation): String = talkers.getValue(constellation)

    /** This style, updated with what [sentence] shows. Sentences without a fix teach nothing. */
    fun observe(sentence: NmeaSentence): NmeaStyle {
        val fields = sentence.fields
        return when (sentence.type) {
            "GSV" -> GSV_TALKERS[sentence.talker]
                ?.let { constellation -> copy(talkers = talkers + (constellation to sentence.talker)) }
                ?: this
            "GGA", "GNS" -> withDecimals(fields.getOrNull(1)).withGnsMode(sentence)
            "RMC" -> withDecimals(fields.getOrNull(2))
            "GLL" -> withDecimals(fields.getOrNull(0))
            "DTM" -> if (fields.firstOrNull().isNullOrEmpty()) this else copy(dtmFields = fields)
            else -> this
        }
    }

    private fun withDecimals(latitude: String?): NmeaStyle {
        val decimals = latitude?.let(LATITUDE::matchEntire)?.groupValues?.get(1)?.length ?: return this
        return copy(minuteDecimals = decimals.coerceIn(1, MAX_MINUTE_DECIMALS))
    }

    private fun withGnsMode(sentence: NmeaSentence): NmeaStyle {
        if (sentence.type != "GNS") return this
        val mode = sentence.fields.getOrNull(5) ?: return this
        return if (mode.length > 1 && mode.all(Char::isLetter)) copy(gnsModeLength = mode.length) else this
    }

    companion object {
        private const val MAX_MINUTE_DECIMALS = 8
        private val LATITUDE = Regex("""\d{4}\.(\d+)""")
        private val GSV_TALKERS = mapOf(
            "GP" to Constellation.GPS,
            "GL" to Constellation.GLONASS,
            "GA" to Constellation.GALILEO,
            "GB" to Constellation.BEIDOU,
            "BD" to Constellation.BEIDOU,
        )

        val DEFAULT = NmeaStyle(
            talkers = mapOf(
                Constellation.GPS to "GP",
                Constellation.GLONASS to "GL",
                Constellation.GALILEO to "GA",
                Constellation.BEIDOU to "GB",
            ),
            minuteDecimals = 6,
            gnsModeLength = 6,
            dtmFields = listOf("P90", "", "0000.000000", "N", "00000.000000", "E", "0.000", "W84"),
        )
    }
}
