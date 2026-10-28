package com.noobexon.xposedfakelocation.manager.geo

import com.noobexon.xposedfakelocation.data.model.RoadProfile

/** One travel direction of a road segment to route. [key] is the route's directed key. */
data class RoadRequest(
    val key: String,
    val profile: RoadProfile,
    val fromLatitude: Double,
    val fromLongitude: Double,
    val toLatitude: Double,
    val toLongitude: Double,
)

/** A routed road: polyline6 geometry and its length. */
data class RoadGeometry(val polyline: String, val distanceM: Double)

/** Why routing failed. */
class RoutingException(val kind: Kind, cause: Throwable? = null) : Exception(kind.name, cause) {
    enum class Kind { NO_ROUTE, NETWORK, SERVER, PARSE }
}

/** Routes one direction along roads. */
interface RoutingClient {
    /** Throws only [RoutingException] (or `CancellationException`). */
    suspend fun route(request: RoadRequest): RoadGeometry
}
