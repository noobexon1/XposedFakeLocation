package com.noobexon.xposedfakelocation.manager.ui.map.osmdroid

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.noobexon.xposedfakelocation.data.DEFAULT_MAP_ZOOM
import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.manager.route.RoadFailure
import com.noobexon.xposedfakelocation.manager.ui.map.LoadingSpinner
import kotlinx.coroutines.flow.Flow
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * The osmdroid map: owns the [MapView] and its overlays and delegates every side effect to the
 * effect composables in `MapViewEffects.kt` and `RouteMapOverlays.kt`. Parameters are documented
 * on `MapViewContainer`.
 */
@Composable
internal fun OsmdroidMap(
    isLoading: Boolean,
    lastClickedLocation: GeoPoint?,
    userLocation: GeoPoint?,
    isPlaying: Boolean,
    mapZoom: Double?,
    hasResolvedInitialLocation: Boolean,
    goToPointEvent: Flow<GeoPoint>,
    centerMapEvent: Flow<Unit>,
    onClickedLocationChange: (GeoPoint?) -> Unit,
    onUserLocationChange: (GeoPoint) -> Unit,
    onMapZoomChange: (Double) -> Unit,
    onLoadingFinished: () -> Unit,
    onInitialLocationResolved: () -> Unit,
    routeMode: Boolean,
    route: Route?,
    routeEditable: Boolean,
    roadFailures: Map<String, RoadFailure>,
    fitRouteEvent: Flow<Unit>,
    onRouteTap: (GeoPoint) -> Unit,
    onRoutePointTap: (String) -> Unit,
    onRoutePointMoved: (String, GeoPoint) -> Unit,
    onRouteSegmentTap: (String, String) -> Unit,
    cameraMoveEvent: Flow<GeoPoint>,
    onMapCenterChange: (GeoPoint) -> Unit,
) {
    val context = LocalContext.current

    val mapView = rememberMapView(context)
    val userMarker = rememberUserMarker(mapView)
    val locationOverlay = rememberLocationOverlay(context, mapView)

    AddLocationOverlayToMap(mapView, locationOverlay)

    HandleCenterMapEvent(mapView, locationOverlay, centerMapEvent)
    HandleGoToPointEvent(mapView, goToPointEvent, onClickedLocationChange)
    HandleCameraMoveEvent(mapView, cameraMoveEvent)
    ReportMapCenter(mapView, onMapCenterChange)
    val markerLocation = if (routeMode && !isPlaying) null else lastClickedLocation
    HandleMarkerUpdates(mapView, userMarker, markerLocation)
    SetupMapClickListener(mapView, isPlaying) { point ->
        point?.let { if (routeMode) onRouteTap(it) else onClickedLocationChange(it) }
    }
    RouteOverlaysEffect(
        mapView = mapView,
        route = route.takeIf { routeMode },
        editable = routeEditable,
        roadFailures = roadFailures,
        onPointTap = onRoutePointTap,
        onPointMoved = onRoutePointMoved,
        onSegmentTap = onRouteSegmentTap,
    )
    HandleFitRouteEvent(mapView, route, fitRouteEvent)
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
    ManageMapViewLifecycle(mapView, locationOverlay, onMapZoomChange)

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
 * updates are enabled immediately; they are disabled again in [ManageMapViewLifecycle] on dispose.
 */
@Composable
private fun rememberLocationOverlay(context: Context, mapView: MapView): MyLocationNewOverlay {
    return remember {
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            enableMyLocation()
        }
    }
}

/** Embeds [mapView] into the Compose layout via [AndroidView], filling all available space. */
@Composable
private fun DisplayMapView(mapView: MapView) {
    AndroidView(
        factory = { mapView },
        modifier = Modifier.fillMaxSize()
    )
}
