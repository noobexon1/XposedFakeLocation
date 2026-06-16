package com.noobexon.xposedfakelocation.data.model

/** A single waypoint of a route. */
data class RouteWaypoint(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val order: Int,
)
