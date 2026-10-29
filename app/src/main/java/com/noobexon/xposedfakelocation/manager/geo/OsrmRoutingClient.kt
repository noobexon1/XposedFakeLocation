package com.noobexon.xposedfakelocation.manager.geo

import com.noobexon.xposedfakelocation.data.ROUTING_PROFILE_PLACEHOLDER
import com.noobexon.xposedfakelocation.data.ROUTING_TIMEOUT_MS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** The OSRM `/route` URL for [request] on the server [template] (with an optional `{profile}`). */
fun buildRouteUrl(template: String, request: RoadRequest): String {
    val base = template.replace(ROUTING_PROFILE_PLACEHOLDER, request.profile.urlName).trimEnd('/')
    val from = "${coordinate(request.fromLongitude)},${coordinate(request.fromLatitude)}"
    val to = "${coordinate(request.toLongitude)},${coordinate(request.toLatitude)}"
    return "$base/route/v1/driving/$from;$to?overview=full&geometries=polyline6&steps=false"
}

private fun coordinate(value: Double): String = String.format(Locale.ROOT, "%.6f", value)

/** Routes with an OSRM server over HTTP. */
class OsrmRoutingClient(
    private val template: String,
    private val userAgent: String,
) : RoutingClient {
    override suspend fun route(request: RoadRequest): RoadGeometry = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(buildRouteUrl(template, request)).openConnection() as? HttpURLConnection
                ?: throw RoutingException(RoutingException.Kind.NETWORK)
            connection.requestMethod = "GET"
            connection.connectTimeout = ROUTING_TIMEOUT_MS
            connection.readTimeout = ROUTING_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) {
                val body = connection.errorStream?.bufferedReader()?.use { it.readText() }
                throw RoutingException(OsrmResponseParser.errorKind(body))
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            OsrmResponseParser.parse(body)
        } catch (e: IOException) {
            throw RoutingException(RoutingException.Kind.NETWORK, e)
        } catch (e: IllegalArgumentException) {
            // HttpURLConnection reports some bad URLs (e.g. port out of range) this way.
            throw RoutingException(RoutingException.Kind.NETWORK, e)
        } catch (e: SecurityException) {
            throw RoutingException(RoutingException.Kind.NETWORK, e)
        } finally {
            connection?.disconnect()
        }
    }
}

/** Reads the server on every call, so setting changes apply without a restart. */
class ConfiguredRoutingClient(
    private val serverUrl: () -> String,
    private val userAgent: String,
) : RoutingClient {
    override suspend fun route(request: RoadRequest): RoadGeometry =
        OsrmRoutingClient(serverUrl(), userAgent).route(request)
}
