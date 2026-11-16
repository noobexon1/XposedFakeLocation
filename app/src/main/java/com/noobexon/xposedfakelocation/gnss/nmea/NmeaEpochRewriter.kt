package com.noobexon.xposedfakelocation.gnss.nmea

import kotlin.math.abs

/**
 * Rewrites one listener's real NMEA stream. Sentences less than [updateGapMillis] apart belong to one chip
 * update, which gets one [NmeaFix] snapshot, one set of GSV pages and one set of GSA sentences; every other
 * replaced sentence maps to its fake counterpart. Proprietary, unknown and malformed sentences are dropped.
 */
class NmeaEpochRewriter(private val updateGapMillis: Long = 500L) {
    private var lastTimestampMillis: Long? = null
    private var fix: NmeaFix? = null
    private val emittedOncePerUpdate = mutableSetOf<String>()

    /** The fake sentences (`$...*HH\r\n`) to deliver instead of [line]. */
    @Synchronized
    fun rewrite(line: String, timestampMillis: Long, style: NmeaStyle, snapshot: () -> NmeaFix): List<String> {
        val previous = lastTimestampMillis
        if (previous == null || abs(timestampMillis - previous) > updateGapMillis) {
            fix = null
            emittedOncePerUpdate.clear()
        }
        lastTimestampMillis = timestampMillis
        val sentence = NmeaSentence.parse(line) ?: return emptyList()
        if (sentence.talker == PROPRIETARY_TALKER || sentence.type !in REPLACED_TYPES) return emptyList()
        if (sentence.type in ONCE_PER_UPDATE && !emittedOncePerUpdate.add(sentence.type)) return emptyList()
        val current = fix ?: snapshot().also { fix = it }
        return NmeaWriter.sentences(sentence.type, current, style).map { it.format() }
    }

    private companion object {
        const val PROPRIETARY_TALKER = "P"
        val REPLACED_TYPES = setOf("GSV", "GSA", "GGA", "RMC", "GNS", "VTG", "GLL", "ZDA", "DTM")
        val ONCE_PER_UPDATE = setOf("GSV", "GSA")
    }
}
