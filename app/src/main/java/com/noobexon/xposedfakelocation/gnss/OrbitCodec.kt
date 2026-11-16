package com.noobexon.xposedfakelocation.gnss

/** Compact one-line-per-satellite encoding of [OrbitElements] for the remote preferences. */
object OrbitCodec {
    fun encode(elements: List<OrbitElements>): String = elements.joinToString("\n") { e ->
        listOf(
            e.constellation.name, e.svid, e.glonassChannel ?: "", e.epochMillis, e.meanMotionRevPerDay,
            e.eccentricity, e.inclinationDeg, e.raanDeg, e.argPerigeeDeg, e.meanAnomalyDeg,
        ).joinToString(",")
    }

    /** Lines that can't be decoded are skipped. */
    fun decode(encoded: String): List<OrbitElements> = encoded.lineSequence()
        .mapNotNull { line ->
            runCatching {
                val c = line.split(',')
                OrbitElements(
                    constellation = Constellation.valueOf(c[0]),
                    svid = c[1].toInt(),
                    glonassChannel = c[2].toIntOrNull(),
                    epochMillis = c[3].toLong(),
                    meanMotionRevPerDay = c[4].toDouble(),
                    eccentricity = c[5].toDouble(),
                    inclinationDeg = c[6].toDouble(),
                    raanDeg = c[7].toDouble(),
                    argPerigeeDeg = c[8].toDouble(),
                    meanAnomalyDeg = c[9].toDouble(),
                )
            }.getOrNull()
        }
        .toList()
}
