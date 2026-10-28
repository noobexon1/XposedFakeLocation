package com.noobexon.xposedfakelocation.manager.geo

import com.noobexon.xposedfakelocation.data.SEARCH_RESULT_LIMIT
import com.noobexon.xposedfakelocation.data.SEARCH_TIMEOUT_MS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

private val PHOTON_LANGUAGES = setOf("en", "de", "fr")

/** The Photon `/api` request URL for [query], optionally biased toward [near] and localized to [language]. */
fun buildSearchUrl(
    serverUrl: String,
    query: String,
    near: LatLon?,
    language: String?,
    limit: Int = SEARCH_RESULT_LIMIT,
): String = buildString {
    append(serverUrl.trimEnd('/'))
    append("/api?q=").append(URLEncoder.encode(query, Charsets.UTF_8.name()))
    append("&limit=").append(limit)
    near?.let { append("&lat=").append(it.latitude).append("&lon=").append(it.longitude) }
    language?.let { append("&lang=").append(it) }
}

/** Photon's `lang` value for [locale], or `null` so Photon returns local names. */
fun photonLanguage(locale: Locale): String? = locale.language.takeIf { it in PHOTON_LANGUAGES }

/** Searches a Photon server at [serverUrl] over HTTP. */
class PhotonGeocodingClient(
    private val serverUrl: String,
    private val language: String?,
    private val userAgent: String,
) : GeocodingClient {
    override suspend fun search(query: String, near: LatLon?): List<Place> = withContext(Dispatchers.IO) {
        val url = buildSearchUrl(serverUrl, query, near, language)
        val connection = try {
            URL(url).openConnection() as? HttpURLConnection
        } catch (e: IOException) {
            throw GeocodingException(GeocodingException.Kind.NETWORK, e)
        } ?: throw GeocodingException(GeocodingException.Kind.NETWORK)
        connection.apply {
            requestMethod = "GET"
            connectTimeout = SEARCH_TIMEOUT_MS
            readTimeout = SEARCH_TIMEOUT_MS
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) throw GeocodingException(GeocodingException.Kind.SERVER)
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            PhotonResponseParser.parse(body)
        } catch (e: IOException) {
            throw GeocodingException(GeocodingException.Kind.NETWORK, e)
        } finally {
            connection.disconnect()
        }
    }
}

/** Reads the server and language on every search, so setting changes apply without a restart. */
class ConfiguredGeocodingClient(
    private val serverUrl: () -> String,
    private val language: () -> String?,
    private val userAgent: String,
) : GeocodingClient {
    override suspend fun search(query: String, near: LatLon?): List<Place> =
        PhotonGeocodingClient(serverUrl(), language(), userAgent).search(query, near)
}
