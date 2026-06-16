package com.noobexon.xposedfakelocation.data.model

/**
 * A route consisting of a name and an ordered list of waypoints.
 * Persisted as GSON JSON in local SharedPreferences.
 */
data class Route(
    val name: String,
    val waypoints: List<RouteWaypoint>,
    val createdAt: Long = System.currentTimeMillis(),
)
