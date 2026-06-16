package com.noobexon.xposedfakelocation.manager.ui.routes

import androidx.compose.runtime.Immutable
import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.data.model.RouteWaypoint

/**
 * UI state for the route overview screen.
 *
 * @property routes The currently stored list of all routes.
 * @property isLoading `true` while initially loading routes.
 */
@Immutable
data class RoutesUiState(
    val routes: List<Route> = emptyList(),
    val isLoading: Boolean = false,
)

/**
 * UI state for the route detail screen.
 *
 * @property route The currently displayed route (can be updated during editing).
 * @property isPlaying `true` when this route is currently playing.
 * @property playbackSpeed Speed in m/s for route playback.
 * @property isLooping `true` when the route should repeat in a loop.
 * @property waypoints The waypoints of the route (ordered list).
 */
@Immutable
data class RouteDetailUiState(
    val route: Route = Route(name = "", waypoints = emptyList()),
    val isPlaying: Boolean = false,
    val playbackSpeed: Double = 10.0,
    val isLooping: Boolean = false,
    val waypoints: List<RouteWaypoint> = emptyList(),
)
