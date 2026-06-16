package com.noobexon.xposedfakelocation.manager.ui.map

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.noobexon.xposedfakelocation.data.model.RouteWaypoint
import org.osmdroid.util.GeoPoint

/**
 * Represents a single text input's value together with an optional validation error.
 *
 * @property value The current text content of the field.
 * @property errorMessageRes A string resource for the validation error, or `null` when valid.
 */
@Immutable
data class InputFieldState(val value: String = "", @StringRes val errorMessageRes: Int? = null)

/**
 * Input state for the "Go to point" dialog (latitude + longitude fields).
 */
@Immutable
data class GoToPointInputState(
    val latitude: InputFieldState = InputFieldState(),
    val longitude: InputFieldState = InputFieldState()
)

/**
 * Input state for the "Add to favorites" dialog (name + optional description + latitude + longitude fields).
 */
@Immutable
data class FavoritesInputState(
    val name: InputFieldState = InputFieldState(),
    val description: InputFieldState = InputFieldState(),
    val latitude: InputFieldState = InputFieldState(),
    val longitude: InputFieldState = InputFieldState(),
)

/**
 * The complete UI state for the Map screen.
 *
 * @property isPlaying Whether location spoofing is currently active.
 * @property lastClickedLocation The currently selected spoof location (marker), or `null` if none.
 * @property userLocation The user's detected real location, used for centering.
 * @property isLoading Whether the map is still resolving an initial camera position.
 * @property mapZoom The last known map zoom level, or `null` before the map has settled.
 * @property isGoToPointDialogVisible Whether the "Go to point" dialog is shown.
 * @property addToFavoritesState Input/validation state for the "Add to favorites" dialog.
 * @property isAddToFavoritesDialogVisible Whether the "Add to favorites" dialog is shown.
 * @property goToPointState Input/validation state for the "Go to point" dialog.
 * @property hasResolvedInitialLocation Whether the map has completed its one-time initial camera
 *   positioning. Survives navigation so that re-entering the screen restores the last camera
 *   position instead of re-running location detection.
 * @property isAddToRouteDialogVisible Whether the "Add to route" dialog is shown.
 * @property availableRouteNames List of names of all saved routes for selection.
 * @property selectedRouteName The currently selected route name in the dialog.
 * @property newRouteNameInput Input for creating a new route from the add-to-route dialog.
 * @property activeRouteWaypoints Waypoints of the currently playing route, empty when none.
 */
@Immutable
data class MapUiState(
    val isPlaying: Boolean = false,
    val lastClickedLocation: GeoPoint? = null,
    val userLocation: GeoPoint? = null,
    val isLoading: Boolean = true,
    val mapZoom: Double? = null,
    val isGoToPointDialogVisible: Boolean = false,
    val addToFavoritesState: FavoritesInputState = FavoritesInputState(),
    val isAddToFavoritesDialogVisible: Boolean = false,
    val goToPointState: GoToPointInputState = GoToPointInputState(),
    val hasResolvedInitialLocation: Boolean = false,
    val isAddToRouteDialogVisible: Boolean = false,
    val availableRouteNames: List<String> = emptyList(),
    val selectedRouteName: String = "",
    val newRouteNameInput: String = "",
    val activeRouteWaypoints: List<RouteWaypoint> = emptyList(),
) {
    /** `true` when the FAB should be interactive, i.e. a spoof target has been placed on the map. */
    val isFabClickable: Boolean
        get() = lastClickedLocation != null
}
