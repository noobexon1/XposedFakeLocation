package com.noobexon.xposedfakelocation.manager.ui.routes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.data.model.RouteWaypoint
import com.noobexon.xposedfakelocation.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the route overview screen.
 *
 * Loads stored routes from [PreferencesRepository] and provides
 * functions for adding, deleting, and updating routes.
 */
class RoutesViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesRepository = PreferencesRepository(application)

    /** Observable list of all stored routes. */
    val routes: StateFlow<List<Route>> =
        preferencesRepository.getRoutesFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Removes a route permanently from storage.
     *
     * @param route The route to delete.
     */
    fun removeRoute(route: Route) {
        viewModelScope.launch {
            preferencesRepository.removeRoute(route)
        }
    }

    /**
     * Updates the name of an existing route.
     *
     * @param route The route with the new name.
     */
    fun updateRoute(route: Route) {
        viewModelScope.launch {
            preferencesRepository.updateRoute(route, route)
        }
    }
}

/**
 * ViewModel for the route detail screen.
 *
 * Manages state of a single route, including waypoints,
 * playback settings, and communication with the Xposed module.
 */
class RouteDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesRepository = PreferencesRepository(application)

    private val _uiState = MutableStateFlow(RouteDetailUiState())
    val uiState: StateFlow<RouteDetailUiState> = _uiState.asStateFlow()

    /**
     * Loads a route from the repository by its name.
     * Sets the UI state to the loaded route including playback settings.
     *
     * @param routeName The name of the route to load.
     */
    fun loadRoute(routeName: String) {
        viewModelScope.launch {
            val route = preferencesRepository.getRoutes().find { it.name == routeName } ?: return@launch
            val playbackSpeed = preferencesRepository.getRoutePlaybackSpeedFlow()
            val isLooping = preferencesRepository.getRouteLoopFlow()
            val isRoutePlaying = preferencesRepository.getRoutePlayingFlow()

            combine(
                preferencesRepository.getRoutePlayingFlow(),
                preferencesRepository.getRoutePlaybackSpeedFlow(),
                preferencesRepository.getRouteLoopFlow(),
            ) { playing, speed, loop ->
                _uiState.update {
                    it.copy(
                        route = route,
                        waypoints = route.waypoints,
                        isPlaying = playing,
                        playbackSpeed = speed,
                        isLooping = loop,
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RouteDetailUiState())
        }
    }

    /**
     * Starts playback of this route.
     * Saves waypoints and settings to remote preferences
     * so the Xposed module can access them.
     */
    fun startPlaying() {
        val state = _uiState.value
        viewModelScope.launch {
            preferencesRepository.saveActiveRouteName(state.route.name)
            preferencesRepository.saveActiveRouteWaypoints(state.waypoints)
            preferencesRepository.saveActiveRouteWaypointIndex(0)
            preferencesRepository.saveActiveRouteProgress(0.0)
            preferencesRepository.saveRoutePlaybackSpeed(state.playbackSpeed)
            preferencesRepository.saveRouteLoop(state.isLooping)
            preferencesRepository.saveRoutePlaying(true)
            preferencesRepository.saveIsPlaying(true)
            _uiState.update { it.copy(isPlaying = true) }
        }
    }

    /**
     * Stops playback of this route.
     */
    fun stopPlaying() {
        viewModelScope.launch {
            preferencesRepository.saveRoutePlaying(false)
            preferencesRepository.saveIsPlaying(false)
            _uiState.update { it.copy(isPlaying = false) }
        }
    }

    /**
     * Updates the playback speed.
     *
     * @param speed Speed in m/s.
     */
    fun updatePlaybackSpeed(speed: Double) {
        _uiState.update { it.copy(playbackSpeed = speed) }
        viewModelScope.launch {
            preferencesRepository.saveRoutePlaybackSpeed(speed)
        }
    }

    /**
     * Toggles loop mode.
     *
     * @param loop `true` when the route should repeat.
     */
    fun updateLooping(loop: Boolean) {
        _uiState.update { it.copy(isLooping = loop) }
        viewModelScope.launch {
            preferencesRepository.saveRouteLoop(loop)
        }
    }

    /**
     * Adds a new waypoint to the route.
     *
     * @param waypoint The waypoint to add.
     */
    fun addWaypoint(waypoint: RouteWaypoint) {
        val state = _uiState.value
        val updatedWaypoints = state.waypoints + waypoint.copy(order = state.waypoints.size)
        val updatedRoute = state.route.copy(waypoints = updatedWaypoints)
        _uiState.update { it.copy(route = updatedRoute, waypoints = updatedWaypoints) }
        viewModelScope.launch {
            preferencesRepository.updateRoute(state.route, updatedRoute)
        }
    }

    /**
     * Removes a waypoint from the route.
     *
     * @param waypoint The waypoint to remove.
     */
    fun removeWaypoint(waypoint: RouteWaypoint) {
        val state = _uiState.value
        val updatedWaypoints = state.waypoints
            .filter { it != waypoint }
            .mapIndexed { index, wp -> wp.copy(order = index) }
        val updatedRoute = state.route.copy(waypoints = updatedWaypoints)
        _uiState.update { it.copy(route = updatedRoute, waypoints = updatedWaypoints) }
        viewModelScope.launch {
            preferencesRepository.updateRoute(state.route, updatedRoute)
        }
    }

    /**
     * Swaps two waypoints in order.
     *
     * @param fromIndex Current index of the waypoint to move.
     * @param toIndex   Target index of the waypoint.
     */
    fun reorderWaypoint(fromIndex: Int, toIndex: Int) {
        val state = _uiState.value
        val mutableWaypoints = state.waypoints.toMutableList()
        val item = mutableWaypoints.removeAt(fromIndex)
        mutableWaypoints.add(toIndex, item)
        val updatedWaypoints = mutableWaypoints.mapIndexed { index, wp -> wp.copy(order = index) }
        val updatedRoute = state.route.copy(waypoints = updatedWaypoints)
        _uiState.update { it.copy(route = updatedRoute, waypoints = updatedWaypoints) }
        viewModelScope.launch {
            preferencesRepository.updateRoute(state.route, updatedRoute)
        }
    }
}
