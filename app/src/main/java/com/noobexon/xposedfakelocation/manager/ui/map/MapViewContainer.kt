package com.noobexon.xposedfakelocation.manager.ui.map

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.DEFAULT_MAP_ZOOM
import com.noobexon.xposedfakelocation.data.model.RouteWaypoint
import kotlinx.coroutines.flow.Flow
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * Hosts the osmdroid [MapView] inside a Compose layout and coordinates all map-related side
 * effects.
 *
 * This composable owns the [MapView] instance (via [rememberMapView]) and the associated osmdroid
 * overlays ([rememberUserMarker], [rememberLocationOverlay]), keeping them alive across
 * recompositions. It delegates every side effect — overlay management, event collection, marker
 * updates, click listening, initial camera positioning, and lifecycle handling — to the
 * `internal` effect composables in [MapViewEffects].
 *
 * The composable is intentionally stateless: it accepts individual primitive/lambda parameters
 * rather than the full [MapViewModel], so that only affected subtrees recompose when a single
 * field changes.
 *
 * @param isLoading When `true`, hides the map and shows [LoadingSpinner] instead. Becomes `false`
 *   once [MapViewEffects.CenterMapOnUserLocation] finishes the initial camera placement.
 * @param lastClickedLocation Current spoof target, or `null`. Passed to [HandleMarkerUpdates].
 * @param userLocation Cached real device location used by [CenterMapOnUserLocation] for re-entry
 *   restoration and by [HandleCenterMapEvent] for the "center on me" action.
 * @param isPlaying Whether spoofing is active. When `true`, map taps are ignored so the marker
 *   cannot be accidentally moved.
 * @param mapZoom Last persisted zoom level; used to restore the camera on re-entry.
 * @param hasResolvedInitialLocation `true` after the one-time initial camera positioning has
 *   completed. On re-entry the camera is restored instantly instead of re-detecting location.
 * @param goToPointEvent One-shot [Flow] from [MapViewModel]; animates the camera to the given
 *   coordinate and places the marker there.
 * @param centerMapEvent One-shot [Flow] from [MapViewModel]; animates the camera to the user's
 *   real device location.
 * @param activeRouteWaypoints Waypoints of the currently playing route — drawn as a Polyline with
 *   Markers on the map. Empty when no route is active.
 * @param onClickedLocationChange Callback to update [MapViewModel] when the user taps the map or
 *   the "Go to point" event resolves.
 * @param onUserLocationChange Callback to update [MapViewModel] when a real device location is
 *   detected.
 * @param onMapZoomChange Callback to persist the zoom level; called on dispose with the final zoom.
 * @param onLoadingFinished Callback to clear the loading state once the camera is positioned.
 * @param onInitialLocationResolved Callback to set [MapUiState.hasResolvedInitialLocation].
 */
@Composable
fun MapViewContainer(
    isLoading: Boolean,
    lastClickedLocation: GeoPoint?,
    userLocation: GeoPoint?,
    isPlaying: Boolean,
    mapZoom: Double?,
    hasResolvedInitialLocation: Boolean,
    activeRouteWaypoints: List<RouteWaypoint>,
    goToPointEvent: Flow<GeoPoint>,
    centerMapEvent: Flow<Unit>,
    onClickedLocationChange: (GeoPoint?) -> Unit,
    onUserLocationChange: (GeoPoint) -> Unit,
    onMapZoomChange: (Double) -> Unit,
    onLoadingFinished: () -> Unit,
    onInitialLocationResolved: () -> Unit,
) {
    val context = LocalContext.current

    // Remember MapView and overlays
    val mapView = rememberMapView(context)
    val userMarker = rememberUserMarker(mapView)
    val locationOverlay = rememberLocationOverlay(context, mapView)

    // Add the location overlay to the map
    AddLocationOverlayToMap(mapView, locationOverlay)

    // Handle map events and updates
    HandleCenterMapEvent(mapView, locationOverlay, centerMapEvent)
    HandleGoToPointEvent(mapView, goToPointEvent, onClickedLocationChange)
    HandleMarkerUpdates(mapView, userMarker, lastClickedLocation)
    SetupMapClickListener(mapView, isPlaying, onClickedLocationChange)
    CenterMapOnUserLocation(
        mapView = mapView,
        locationOverlay = locationOverlay,
        lastClickedLocation = lastClickedLocation,
        userLocation = userLocation,
        mapZoom = mapZoom,
        hasResolvedInitialLocation = hasResolvedInitialLocation,
        onUserLocationChange = onUserLocationChange,
        onMapZoomChange = onMapZoomChange,
        onLoadingFinished = onLoadingFinished,
        onInitialLocationResolved = onInitialLocationResolved,
    )
    HandleActiveRouteOverlay(mapView, activeRouteWaypoints)
    ManageMapViewLifecycle(mapView, locationOverlay, onMapZoomChange)

    // Display loading spinner or MapView
    if (isLoading) {
        LoadingSpinner()
    } else {
        DisplayMapView(mapView)
    }
}

/**
 * Creates and remembers a [MapView] configured with MAPNIK tiles, multi-touch zoom controls,
 * and the default zoom level. The instance is stable for the lifetime of the composition.
 */
@Composable
private fun rememberMapView(context: Context): MapView {
    return remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setBuiltInZoomControls(false)
            setMultiTouchControls(true)
            controller.setZoom(DEFAULT_MAP_ZOOM)
        }
    }
}

/**
 * Creates and remembers the [Marker] used to show the user's chosen spoof-target on the map.
 * Anchored at the centre-bottom so the marker pin tip points to the exact tap location.
 */
@Composable
private fun rememberUserMarker(mapView: MapView): Marker {
    return remember {
        Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
    }
}

/**
 * Creates and remembers a [MyLocationNewOverlay] backed by a [GpsMyLocationProvider]. Location
 * updates are enabled immediately; they are disabled again in
 * [MapViewEffects.ManageMapViewLifecycle] on dispose.
 */
@Composable
private fun rememberLocationOverlay(context: Context, mapView: MapView): MyLocationNewOverlay {
    return remember {
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            enableMyLocation()
        }
    }
}

/**
 * Shown in place of the map while [MapUiState.isLoading] is `true`. Displays a centred
 * [CircularProgressIndicator] with a descriptive label.
 */
@Composable
private fun LoadingSpinner() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.map_updating),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Embeds [mapView] into the Compose layout via [AndroidView], filling all available space.
 * Only rendered when [MapUiState.isLoading] is `false`.
 */
@Composable
private fun DisplayMapView(mapView: MapView) {
    AndroidView(
        factory = { mapView },
        modifier = Modifier.fillMaxSize()
    )
}
