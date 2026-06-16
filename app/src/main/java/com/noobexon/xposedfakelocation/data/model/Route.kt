package com.noobexon.xposedfakelocation.data.model

/**
 * Eine Route besteht aus einem Namen und einer geordneten Liste von Wegpunkten.
 * Wird als GSON-JSON in den lokalen SharedPreferences gespeichert.
 */
data class Route(
    val name: String,
    val waypoints: List<RouteWaypoint>,
    val createdAt: Long = System.currentTimeMillis(),
)
