package com.noobexon.xposedfakelocation.gnss.nmea

import kotlin.math.abs

/**
 * Rewrites one listener's real NMEA stream. A chip update is a burst of sentences at most [updateGapMillis]
 * apart; a repeated position sentence also starts a new update, for chips that repeat timestamps. Each update
 * gets one [NmeaFix] snapshot, one set of GSV pages and one set of GSA sentences; every other replaced sentence
 * maps to its fake counterpart. Proprietary, unknown and malformed sentences are dropped.
 */
class NmeaEpochRewriter(private val updateGapMillis: Long = 50L) {
    private var lastTimestampMillis: Long? = null
    private var fix: NmeaFix? = null
    private val deliveredTypes = mutableSetOf<String>()

    /** The fake sentences (`$...*HH\r\n`) to deliver instead of [line]. */
    @Synchronized
    fun rewrite(line: String, timestampMillis: Long, style: NmeaStyle, snapshot: () -> NmeaFix): List<String> {
        val sentence = NmeaSentence.parse(line)
        if (startsNewUpdate(sentence, timestampMillis)) {
            fix = null
            deliveredTypes.clear()
        }
        lastTimestampMillis = timestampMillis
        if (sentence == null || sentence.talker == PROPRIETARY_TALKER || sentence.type !in REPLACED_TYPES) return emptyList()
        if (sentence.type in ONCE_PER_UPDATE && sentence.type in deliveredTypes) return emptyList()
        val current = fix ?: snapshot().also { fix = it }
        deliveredTypes += sentence.type
        return NmeaWriter.sentences(sentence.type, current, style).map { it.format() }
    }

    private fun startsNewUpdate(sentence: NmeaSentence?, timestampMillis: Long): Boolean {
        val previous = lastTimestampMillis ?: return true
        if (abs(timestampMillis - previous) > updateGapMillis) return true
        if (sentence == null || sentence.talker == PROPRIETARY_TALKER) return false
        return sentence.type !in ONCE_PER_UPDATE && sentence.type in deliveredTypes
    }

    private companion object {
        const val PROPRIETARY_TALKER = "P"
        val REPLACED_TYPES = setOf("GSV", "GSA", "GGA", "RMC", "GNS", "VTG", "GLL", "ZDA", "DTM")
        val ONCE_PER_UPDATE = setOf("GSV", "GSA")
    }
}
