package com.noobexon.xposedfakelocation.manager.ui.map

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.model.FavoriteLocation
import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.data.model.RouteWaypoint
import com.noobexon.xposedfakelocation.data.repository.PreferencesRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint

/** Valid latitude values accepted by the "Go to point" and "Add to favorites" dialogs. */
private val LATITUDE_RANGE = -90.0..90.0

/** Valid longitude values accepted by the "Go to point" and "Add to favorites" dialogs. */
private val LONGITUDE_RANGE = -180.0..180.0

/**
 * ViewModel for the Map screen.
 *
 * Owns all Map-screen state via a single [uiState] [StateFlow], exposes one-shot camera events as
 * [Channel]-backed [Flow]s, and provides typed mutation functions so the UI never writes directly to
 * [_uiState].
 *
 * **Persistence**: [MapUiState.isPlaying], [MapUiState.lastClickedLocation], and [MapUiState.mapZoom]
 * are kept in sync with [PreferencesRepository] — all three are read on init and written back
 * whenever they change, so they survive the app being fully closed and reopened.
 *
 * **Dialog lifecycle**: each dialog has a paired `show*` / `hide*` function that guards setup and
 * teardown. The corresponding `confirm*` function validates input; on success it acts (emits event
 * or persists) and dismisses; on failure it writes error strings into the state so the dialog can
 * render inline validation messages.
 *
 * **Drawer re-open**: a simple boolean flag ([reopenDrawerRequested]) stores the intent to re-open
 * the navigation drawer when the map screen is returned to after a drawer-triggered navigation.
 * The flag is consumed exactly once via [consumeReopenDrawerRequest].
 */
class MapViewModel(application: Application) : AndroidViewModel(application) {
    private val preferencesRepository = PreferencesRepository(application)

    private val _uiState = MutableStateFlow(
        MapUiState(mapZoom = preferencesRepository.getMapZoom())
    )

    /** Snapshot of the full Map-screen UI state, updated atomically. */
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    /**
     * One-shot event that tells [MapViewEffects.HandleGoToPointEvent] to animate the camera to a
     * specific coordinate and place the spoof marker there.
     */
    private val _goToPointEvent = Channel<GeoPoint>(Channel.BUFFERED)
    val goToPointEvent: Flow<GeoPoint> = _goToPointEvent.receiveAsFlow()

    /**
     * One-shot event that tells [MapViewEffects.HandleCenterMapEvent] to animate the camera back
     * to the user's real location.
     */
    private val _centerMapEvent = Channel<Unit>(Channel.BUFFERED)
    val centerMapEvent: Flow<Unit> = _centerMapEvent.receiveAsFlow()

    /**
     * One-shot event emitted after a favorite is successfully saved. [MapScreen] collects this
     * and navigates the user to the Favorites screen so they can immediately see the new entry.
     */
    private val _navigateToFavoritesEvent = Channel<Unit>(Channel.BUFFERED)
    val navigateToFavoritesEvent: Flow<Unit> = _navigateToFavoritesEvent.receiveAsFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.getIsPlayingFlow()
                .flowOn(Dispatchers.IO)
                .collect { isPlaying ->
                    val waypoints = withContext(Dispatchers.IO) {
                        preferencesRepository.getActiveRouteWaypoints()
                    }
                    _uiState.update {
                        it.copy(
                            isPlaying = isPlaying,
                            activeRouteWaypoints = waypoints,
                            currentRoutePosition = null
                        )
                    }
                }
        }

        viewModelScope.launch {
            while (true) {
                delay(2000L)
                if (_uiState.value.isPlaying) {
                    val lat = withContext(Dispatchers.IO) { preferencesRepository.getCurrentRouteLat() }
                    val lon = withContext(Dispatchers.IO) { preferencesRepository.getCurrentRouteLon() }
                    if (lat != 0.0 || lon != 0.0) {
                        _uiState.update { it.copy(currentRoutePosition = GeoPoint(lat, lon)) }
                    }
                }
            }
        }

        viewModelScope.launch {
            preferencesRepository.getLastClickedLocationFlow()
                .flowOn(Dispatchers.IO)
                .collect { location ->
                    val geoPoint = location?.let { GeoPoint(it.latitude, it.longitude) }
                    _uiState.update { it.copy(lastClickedLocation = geoPoint) }
                }
        }
    }

    /**
     * Toggles location-spoofing on/off and persists the new value.
     *
     * The optimistic local update ensures the FAB reflects the new state immediately, while the
     * coroutine write to [PreferencesRepository] happens asynchronously.
     */
    fun togglePlaying() {
        val currentIsPlaying = !_uiState.value.isPlaying
        _uiState.update { it.copy(isPlaying = currentIsPlaying) }

        viewModelScope.launch {
            preferencesRepository.saveIsPlaying(currentIsPlaying)
        }
    }

    /**
     * Updates the cached real-device location that is used for the "center on me" action and for
     * restoring the camera on re-entry when no spoof marker exists.
     *
     * @param location The most-recent device location reported by the location overlay.
     */
    fun updateUserLocation(location: GeoPoint) {
        _uiState.update { it.copy(userLocation = location) }
    }

    /**
     * Sets or clears the spoof-target marker and persists the change.
     *
     * Passing `null` removes the marker and clears the persisted location so that reopening the
     * app starts with a clean slate.
     *
     * @param geoPoint The new spoof target, or `null` to clear it.
     */
    fun updateClickedLocation(geoPoint: GeoPoint?) {
        _uiState.update { it.copy(lastClickedLocation = geoPoint) }

        viewModelScope.launch {
            geoPoint?.let {
                preferencesRepository.saveLastClickedLocation(it.latitude, it.longitude)
            } ?: preferencesRepository.clearLastClickedLocation()
        }
    }

    /**
     * Stores the map's current zoom level so it can be restored when the screen is re-entered or
     * the app is reopened after being fully closed. Called from [MapViewEffects.ManageMapViewLifecycle]
     * on dispose, capturing the zoom at the exact moment the map is torn down.
     *
     * @param zoom The zoom level to persist.
     */
    fun updateMapZoom(zoom: Double) {
        _uiState.update { it.copy(mapZoom = zoom) }
        preferencesRepository.saveMapZoom(zoom)
    }

    /**
     * Clears the loading state once [MapViewEffects.CenterMapOnUserLocation] has finished
     * determining the initial camera position. After this call [MapUiState.isLoading] is `false`
     * and the map view becomes visible.
     */
    fun setLoadingFinished() {
        _uiState.update { it.copy(isLoading = false) }
    }

    /** Marks that the one-time initial camera positioning has completed. */
    fun markInitialLocationResolved() {
        _uiState.update { it.copy(hasResolvedInitialLocation = true) }
    }

    /**
     * One-shot flag that signals the map screen should reopen the navigation drawer when it becomes
     * active again. Set when the user navigates away via the drawer; consumed once on re-entry.
     * Survives the map composable being destroyed because it lives in the ViewModel.
     */
    private var reopenDrawerRequested = false

    /** Records that the drawer should be reopened the next time the map screen is shown. */
    fun requestReopenDrawer() {
        reopenDrawerRequested = true
    }

    /** Returns whether a drawer-reopen was requested, consuming the one-shot flag. */
    fun consumeReopenDrawerRequest(): Boolean {
        val requested = reopenDrawerRequested
        reopenDrawerRequested = false
        return requested
    }

    /**
     * Enqueues a [centerMapEvent] to animate the camera back to the user's real location.
     * Called when the user taps the "My Location" icon in the top bar.
     */
    fun triggerCenterMapEvent() {
        _centerMapEvent.trySend(Unit)
    }

    // ---- Go to point dialog ----

    /** Makes the "Go to point" dialog visible. */
    fun showGoToPointDialog() {
        _uiState.update { it.copy(isGoToPointDialogVisible = true) }
    }

    /**
     * Dismisses the "Go to point" dialog and resets its input state so it is clean the next time
     * it is opened.
     */
    fun hideGoToPointDialog() {
        _uiState.update {
            it.copy(isGoToPointDialogVisible = false, goToPointState = GoToPointInputState())
        }
    }

    /**
     * Updates the latitude field of the "Go to point" dialog without triggering validation.
     * Validation only runs on [confirmGoToPoint].
     *
     * @param value The raw string typed by the user.
     */
    fun onGoToPointLatitudeChange(value: String) {
        _uiState.update {
            it.copy(
                goToPointState = it.goToPointState.copy(
                    latitude = it.goToPointState.latitude.copy(value = value)
                )
            )
        }
    }

    /**
     * Updates the longitude field of the "Go to point" dialog without triggering validation.
     * Validation only runs on [confirmGoToPoint].
     *
     * @param value The raw string typed by the user.
     */
    fun onGoToPointLongitudeChange(value: String) {
        _uiState.update {
            it.copy(
                goToPointState = it.goToPointState.copy(
                    longitude = it.goToPointState.longitude.copy(value = value)
                )
            )
        }
    }

    /**
     * Validates the "Go to point" inputs. On success, emits a [goToPointEvent] and dismisses the
     * dialog; on failure, updates the input fields with validation errors and keeps the dialog open.
     */
    fun confirmGoToPoint() {
        val state = _uiState.value.goToPointState
        val latitudeError = validateInput(state.latitude.value, LATITUDE_RANGE, R.string.validation_latitude_range)
        val longitudeError = validateInput(state.longitude.value, LONGITUDE_RANGE, R.string.validation_longitude_range)

        if (latitudeError == null && longitudeError == null) {
            _goToPointEvent.trySend(GeoPoint(state.latitude.value.toDouble(), state.longitude.value.toDouble()))
            hideGoToPointDialog()
        } else {
            _uiState.update {
                it.copy(
                    goToPointState = it.goToPointState.copy(
                        latitude = it.goToPointState.latitude.copy(errorMessageRes = latitudeError),
                        longitude = it.goToPointState.longitude.copy(errorMessageRes = longitudeError)
                    )
                )
            }
        }
    }

    // ---- Add to favorites dialog ----

    /**
     * Makes the "Add to favorites" dialog visible, pre-filling the latitude and longitude fields
     * from the currently placed spoof marker (if any) so the user only needs to supply a name.
     */
    fun showAddToFavoritesDialog() {
        val marker = _uiState.value.lastClickedLocation
        _uiState.update {
            it.copy(
                isAddToFavoritesDialogVisible = true,
                addToFavoritesState = if (marker != null) {
                    it.addToFavoritesState.copy(
                        latitude = InputFieldState(value = marker.latitude.toString()),
                        longitude = InputFieldState(value = marker.longitude.toString())
                    )
                } else {
                    it.addToFavoritesState
                }
            )
        }
    }

    /**
     * Dismisses the "Add to favorites" dialog and resets its input state so it is clean the next
     * time it is opened.
     */
    fun hideAddToFavoritesDialog() {
        _uiState.update {
            it.copy(isAddToFavoritesDialogVisible = false, addToFavoritesState = FavoritesInputState())
        }
    }

    /**
     * Updates the name field of the "Add to favorites" dialog with inline live validation — the
     * field is marked as an error immediately if the value is blank.
     *
     * @param value The raw string typed by the user.
     */
    fun onFavoriteNameChange(value: String) {
        val error = if (value.isBlank()) R.string.validation_name_required else null
        _uiState.update {
            it.copy(
                addToFavoritesState = it.addToFavoritesState.copy(
                    name = it.addToFavoritesState.name.copy(value = value, errorMessageRes = error)
                )
            )
        }
    }

    /**
     * Updates the optional description field of the "Add to favorites" dialog. No validation is
     * applied — description is always optional and may be left blank.
     *
     * @param value The raw string typed by the user.
     */
    fun onFavoriteDescriptionChange(value: String) {
        _uiState.update {
            it.copy(
                addToFavoritesState = it.addToFavoritesState.copy(
                    description = it.addToFavoritesState.description.copy(value = value)
                )
            )
        }
    }

    /**
     * Updates the latitude field of the "Add to favorites" dialog with inline live validation.
     *
     * @param value The raw string typed by the user.
     */
    fun onFavoriteLatitudeChange(value: String) {
        val error = validateInput(value, LATITUDE_RANGE, R.string.validation_latitude_range)
        _uiState.update {
            it.copy(
                addToFavoritesState = it.addToFavoritesState.copy(
                    latitude = it.addToFavoritesState.latitude.copy(value = value, errorMessageRes = error)
                )
            )
        }
    }

    /**
     * Updates the longitude field of the "Add to favorites" dialog with inline live validation.
     *
     * @param value The raw string typed by the user.
     */
    fun onFavoriteLongitudeChange(value: String) {
        val error = validateInput(value, LONGITUDE_RANGE, R.string.validation_longitude_range)
        _uiState.update {
            it.copy(
                addToFavoritesState = it.addToFavoritesState.copy(
                    longitude = it.addToFavoritesState.longitude.copy(value = value, errorMessageRes = error)
                )
            )
        }
    }

    /**
     * Validates the "Add to favorites" inputs. On success, persists the favorite and dismisses the
     * dialog; on failure, updates the input fields with validation errors and keeps the dialog open.
     */
    fun confirmAddFavorite() {
        val state = _uiState.value.addToFavoritesState
        val nameError = if (state.name.value.isBlank()) R.string.validation_name_required else null
        val latitudeError = validateInput(state.latitude.value, LATITUDE_RANGE, R.string.validation_latitude_range)
        val longitudeError = validateInput(state.longitude.value, LONGITUDE_RANGE, R.string.validation_longitude_range)

        if (nameError == null && latitudeError == null && longitudeError == null) {
            val favorite = FavoriteLocation(
                name = state.name.value,
                latitude = state.latitude.value.toDouble(),
                longitude = state.longitude.value.toDouble(),
                description = state.description.value.trim(),
            )
            viewModelScope.launch {
                preferencesRepository.addFavorite(favorite)
            }
            hideAddToFavoritesDialog()
            _navigateToFavoritesEvent.trySend(Unit)
        } else {
            _uiState.update {
                it.copy(
                    addToFavoritesState = it.addToFavoritesState.copy(
                        name = it.addToFavoritesState.name.copy(errorMessageRes = nameError),
                        latitude = it.addToFavoritesState.latitude.copy(errorMessageRes = latitudeError),
                        longitude = it.addToFavoritesState.longitude.copy(errorMessageRes = longitudeError)
                    )
                )
            }
        }
    }

    // ---- Add to route dialog ----

    /**
     * Remembers the name of the route that was last created or selected, so that
     * [showAddToRouteDialog] can pre-select it when the user adds another waypoint.
     */
    private var lastUsedRouteName: String? = null

    /** Makes the "Add to route" dialog visible and loads the list of available route names. */
    fun showAddToRouteDialog() {
        val routeNames = preferencesRepository.getRoutes().map { it.name }
        val preselected = if (lastUsedRouteName in routeNames) {
            lastUsedRouteName!!
        } else {
            routeNames.firstOrNull() ?: ""
        }
        _uiState.update {
            it.copy(
                isAddToRouteDialogVisible = true,
                availableRouteNames = routeNames,
                selectedRouteName = preselected,
                newRouteNameInput = "",
            )
        }
    }

    /** Dismisses the "Add to route" dialog. */
    fun hideAddToRouteDialog() {
        _uiState.update { it.copy(isAddToRouteDialogVisible = false) }
    }

    /**
     * Updates the selected route name in the dialog.
     *
     * @param name The selected route name.
     */
    fun onRouteSelectionChange(name: String) {
        lastUsedRouteName = name
        _uiState.update { it.copy(selectedRouteName = name, newRouteNameInput = "") }
    }

    /**
     * Updates the new route name input field.
     *
     * @param value The raw string typed by the user.
     */
    fun onNewRouteNameChange(value: String) {
        _uiState.update { it.copy(newRouteNameInput = value, selectedRouteName = "") }
    }

    /**
     * Adds the current marker to an existing route or creates a new route.
     * On success the dialog is dismissed.
     */
    fun confirmAddToRoute() {
        val state = _uiState.value
        val location = state.lastClickedLocation ?: return
        val routeName = state.selectedRouteName.ifBlank { state.newRouteNameInput.ifBlank { return } }

        viewModelScope.launch {
            val routes = preferencesRepository.getRoutes().toMutableList()
            val existingRoute = routes.find { it.name == routeName }

            if (existingRoute != null) {
                val newWaypoint = RouteWaypoint(
                    name = "Waypoint ${existingRoute.waypoints.size + 1}",
                    latitude = location.latitude,
                    longitude = location.longitude,
                    order = existingRoute.waypoints.size,
                )
                val updatedRoute = existingRoute.copy(
                    waypoints = existingRoute.waypoints + newWaypoint,
                )
                preferencesRepository.updateRoute(existingRoute, updatedRoute)
            } else {
                val newRoute = Route(
                    name = routeName,
                    waypoints = listOf(
                        RouteWaypoint(
                            name = "Waypoint 1",
                            latitude = location.latitude,
                            longitude = location.longitude,
                            order = 0,
                        ),
                    ),
                )
                preferencesRepository.addRoute(newRoute)
            }

            lastUsedRouteName = routeName
            hideAddToRouteDialog()
        }
    }

    /**
     * Parses [input] as a `Double` and checks whether it falls within [range].
     *
     * @param input Raw text from a dialog field.
     * @param range The valid coordinate range (e.g. `−90..90` for latitude).
     * @param errorMessageRes String resource to return when the value is invalid.
     * @return `null` when the input is valid, or [errorMessageRes] when it is not.
     */
    private fun validateInput(
        input: String, range: ClosedRange<Double>, @StringRes errorMessageRes: Int
    ): Int? {
        val value = input.toDoubleOrNull()
        return if (value == null || value !in range) errorMessageRes else null
    }
}
