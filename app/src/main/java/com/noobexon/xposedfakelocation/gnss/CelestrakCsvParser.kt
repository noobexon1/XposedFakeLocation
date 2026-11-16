package com.noobexon.xposedfakelocation.gnss

import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Parses CelesTrak GP data in CSV form. GPS and BeiDou svids come from the object name; Galileo and
 * GLONASS svids (and GLONASS channels) come from the bundled svid table, keyed by NORAD catalog number.
 */
object CelestrakCsvParser {
    data class SvidEntry(val constellation: Constellation, val svid: Int, val glonassChannel: Int?)

    private const val HEADER_FIRST_COLUMN = "OBJECT_NAME"
    private val gpsPrn = Regex("""\(PRN (\d+)\)""")
    private val beidouPrn = Regex("""\(C(\d+)\)""")

    /** Parses `NORAD_CAT_ID,constellation,svid,glonass_channel` lines; the first line is the header. */
    fun parseSvidMap(csv: String): Map<Int, SvidEntry> = csv.lineSequence()
        .drop(1)
        .mapNotNull { line ->
            val cols = line.trim().split(',')
            val norad = cols.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val constellation = Constellation.entries.firstOrNull { it.name == cols.getOrNull(1) } ?: return@mapNotNull null
            val svid = cols.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull null
            norad to SvidEntry(constellation, svid, cols.getOrNull(3)?.toIntOrNull())
        }
        .toMap()

    /** Parses one or more concatenated CelesTrak CSV groups, each starting with its header line. */
    fun parse(csv: String, svidMap: Map<Int, SvidEntry>): List<OrbitElements> {
        var columns = emptyMap<String, Int>()
        val result = mutableListOf<OrbitElements>()
        csv.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            val cols = line.split(',')
            if (cols.first() == HEADER_FIRST_COLUMN) {
                columns = cols.withIndex().associate { (index, name) -> name to index }
            } else {
                parseRow(cols, columns, svidMap)?.let(result::add)
            }
        }
        return result
    }

    private fun parseRow(cols: List<String>, columns: Map<String, Int>, svidMap: Map<Int, SvidEntry>): OrbitElements? =
        runCatching {
            fun col(name: String): String = cols[columns.getValue(name)]
            val entry = identify(col("OBJECT_NAME"), col("NORAD_CAT_ID").toInt(), svidMap) ?: return null
            OrbitElements(
                constellation = entry.constellation,
                svid = entry.svid,
                glonassChannel = entry.glonassChannel,
                epochMillis = LocalDateTime.parse(col("EPOCH")).toInstant(ZoneOffset.UTC).toEpochMilli(),
                meanMotionRevPerDay = col("MEAN_MOTION").toDouble(),
                eccentricity = col("ECCENTRICITY").toDouble(),
                inclinationDeg = col("INCLINATION").toDouble(),
                raanDeg = col("RA_OF_ASC_NODE").toDouble(),
                argPerigeeDeg = col("ARG_OF_PERICENTER").toDouble(),
                meanAnomalyDeg = col("MEAN_ANOMALY").toDouble(),
            )
        }.getOrNull()

    private fun identify(name: String, norad: Int, svidMap: Map<Int, SvidEntry>): SvidEntry? = when {
        name.startsWith("GPS") ->
            gpsPrn.find(name)?.let { SvidEntry(Constellation.GPS, it.groupValues[1].toInt(), null) }
        name.startsWith("BEIDOU") ->
            beidouPrn.find(name)?.let { SvidEntry(Constellation.BEIDOU, it.groupValues[1].toInt(), null) }
        else -> svidMap[norad]
    }
}
