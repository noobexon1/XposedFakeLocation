package com.noobexon.xposedfakelocation.gnss

/** When stored orbits need refreshing, and how a partial download is merged into them. */
object OrbitUpdate {
    const val MAX_AGE_MILLIS = 24 * 60 * 60 * 1000L

    fun isStale(versionMillis: Long, nowMillis: Long): Boolean = nowMillis - versionMillis > MAX_AGE_MILLIS

    /** Constellations downloaded successfully (non-empty) replace their old satellites; the others keep [previous]. */
    fun merge(previous: List<OrbitElements>, downloaded: Map<Constellation, List<OrbitElements>?>): List<OrbitElements> =
        Constellation.entries.flatMap { constellation ->
            downloaded[constellation]?.takeIf { it.isNotEmpty() }
                ?: previous.filter { it.constellation == constellation }
        }
}
