package com.noobexon.xposedfakelocation.manager.geo

/** A coordinate used to bias search results. */
data class LatLon(val latitude: Double, val longitude: Double)

/** One search result. [subtitle] may be empty. */
data class Place(
    val title: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
)

/** A failed search, classified by [kind]. */
class GeocodingException(val kind: Kind, cause: Throwable? = null) : Exception(kind.name, cause) {
    enum class Kind { NETWORK, SERVER, PARSE }
}

/** Looks up places by free-text query or by coordinate. */
interface GeocodingClient {
    /** Results for [query], ranked closer to [near] when given. Throws [GeocodingException]. */
    suspend fun search(query: String, near: LatLon?): List<Place>

    /** The place at [at], or `null` if there is none. Throws [GeocodingException]. */
    suspend fun reverse(at: LatLon): Place? = null
}
