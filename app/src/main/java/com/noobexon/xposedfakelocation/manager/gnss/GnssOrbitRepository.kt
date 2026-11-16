package com.noobexon.xposedfakelocation.manager.gnss

import android.content.Context
import android.util.Log
import com.noobexon.xposedfakelocation.BuildConfig
import com.noobexon.xposedfakelocation.data.repository.PreferencesRepository
import com.noobexon.xposedfakelocation.gnss.CelestrakCsvParser
import com.noobexon.xposedfakelocation.gnss.Constellation
import com.noobexon.xposedfakelocation.gnss.OrbitCodec
import com.noobexon.xposedfakelocation.gnss.OrbitUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Keeps the satellite orbits in the remote preferences fresh for the hooks: seeds the bundled snapshot when
 * nothing is stored, and downloads current CelesTrak data when the stored data is older than a day.
 * Failures are logged and leave the stored data unchanged.
 */
class GnssOrbitRepository(
    private val context: Context,
    private val preferences: PreferencesRepository = PreferencesRepository(context),
) {
    suspend fun refreshIfStale() = withContext(Dispatchers.IO) {
        refreshLock.withLock {
            runCatching {
                val svidMap = CelestrakCsvParser.parseSvidMap(readAsset(SVID_MAP_ASSET))
                if (preferences.getGnssOrbitsVersion() == 0L) seedBundled(svidMap)
                if (OrbitUpdate.isStale(preferences.getGnssOrbitsVersion(), System.currentTimeMillis())) download(svidMap)
            }.onFailure { Log.w(TAG, "Orbit refresh failed", it) }
        }
    }

    /** The bundled snapshot's version is its newest epoch, so it counts as stale and a download follows. */
    private fun seedBundled(svidMap: Map<Int, CelestrakCsvParser.SvidEntry>) {
        val bundled = CelestrakCsvParser.parse(readAsset(ORBITS_ASSET), svidMap)
        if (bundled.isEmpty()) return
        preferences.saveGnssOrbits(OrbitCodec.encode(bundled), bundled.maxOf { it.epochMillis })
    }

    private fun download(svidMap: Map<Int, CelestrakCsvParser.SvidEntry>) {
        val downloaded = GROUPS.mapValues { (constellation, group) ->
            fetch(URL_TEMPLATE.format(group))
                ?.let { csv -> CelestrakCsvParser.parse(csv, svidMap).filter { it.constellation == constellation } }
        }
        if (downloaded.values.all { it.isNullOrEmpty() }) return
        val previous = OrbitCodec.decode(preferences.getGnssOrbits().orEmpty())
        preferences.saveGnssOrbits(OrbitCodec.encode(OrbitUpdate.merge(previous, downloaded)), System.currentTimeMillis())
    }

    private fun fetch(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as? HttpURLConnection ?: return null
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "XposedFakeLocation/${BuildConfig.VERSION_NAME}")
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            Log.w(TAG, "Download failed: $url", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun readAsset(name: String): String = context.assets.open(name).bufferedReader().use { it.readText() }

    private companion object {
        const val TAG = "GnssOrbitRepository"
        const val ORBITS_ASSET = "gnss/orbits.csv"
        const val SVID_MAP_ASSET = "gnss/svid_map.csv"
        const val URL_TEMPLATE = "https://celestrak.org/NORAD/elements/gp.php?GROUP=%s&FORMAT=csv"
        const val TIMEOUT_MS = 15_000
        val GROUPS = mapOf(
            Constellation.GPS to "gps-ops",
            Constellation.GLONASS to "glo-ops",
            Constellation.GALILEO to "galileo",
            Constellation.BEIDOU to "beidou",
        )

        /** Process-wide: app start and Play can trigger refreshes at the same time. */
        val refreshLock = Mutex()
    }
}
