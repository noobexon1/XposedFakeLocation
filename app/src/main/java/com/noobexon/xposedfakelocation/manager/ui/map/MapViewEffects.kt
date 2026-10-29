package com.noobexon.xposedfakelocation.manager.ui.map

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.DEFAULT_MAP_ZOOM
import com.noobexon.xposedfakelocation.manager.geo.LatLon
import kotlinx.coroutines.flow.Flow
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * Ensures [locationOverlay] is present in [mapView]'s overlay list.
 *
 * Guarded by a containment check so repeated recompositions never add the overlay twice. Keyed on
 * [Unit] so it runs exactly once per composition.
 *
 * @param mapView The map to add the overlay to.
 * @param locationOverlay The "blue dot" overlay that shows the user's real device location.
 */
@Composable
internal fun AddLocationOverlayToMap(
    mapView: MapView,
    locationOverlay: MyLocationNewOverlay
) {
    LaunchedEffect(Unit) {
        if (!mapView.overlays.contains(locationOverlay)) {
            mapView.overlays.add(locationOverlay)
        }
    }
}

/**
 * Collects [centerMapEvent] in a lifecycle-aware coroutine and animates the camera to the user's
 * real location on each emission.
 *
 * Collection is scoped to [Lifecycle.State.STARTED] via [repeatOnLifecycle] so that events
 * buffered while the screen is backgrounded are drained immediately on resume rather than being
 * silently dropped or processed off-screen.
 *
 * If the location overlay has no fix yet, a short Toast is shown instead of attempting an
 * animation to a null point.
 *
 * @param mapView The map whose camera is animated.
 * @param locationOverlay Source of the current device location.
 * @param centerMapEvent Hot [Flow] backed by [MapViewModel._centerMapEvent].
 */
@Composable
internal fun HandleCenterMapEvent(
    mapView: MapView,
    locationOverlay: MyLocationNewOverlay,
    centerMapEvent: Flow<Unit>
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val userLocationNotAvailable = stringResource(R.string.toast_user_location_not_available)
    LaunchedEffect(lifecycleOwner, centerMapEvent) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            centerMapEvent.collect {
                val userLocation = locationOverlay.myLocation
                if (userLocation != null) {
                    mapView.controller.animateTo(userLocation)
                } else {
                    Toast.makeText(context, userLocationNotAvailable, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

/**
 * Collects [goToPointEvent] in a lifecycle-aware coroutine, animates the camera to the target
 * coordinate, and updates the spoof marker via [onClickedLocationChange].
 *
 * Like [HandleCenterMapEvent], collection is scoped to [Lifecycle.State.STARTED] to prevent
 * navigation events from being processed while the screen is in the back stack.
 *
 * @param mapView The map whose camera is animated.
 * @param goToPointEvent Hot [Flow] backed by [MapViewModel._goToPointEvent].
 * @param onClickedLocationChange Callback that updates [MapViewModel.uiState.lastClickedLocation].
 */
@Composable
internal fun HandleGoToPointEvent(
    mapView: MapView,
    goToPointEvent: Flow<GeoPoint>,
    onClickedLocationChange: (GeoPoint?) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, goToPointEvent) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            goToPointEvent.collect { geoPoint ->
                mapView.controller.animateTo(geoPoint)
                onClickedLocationChange(geoPoint)
            }
        }
    }
}

/** Animates the camera on each [cameraMoveEvent] emission; the marker is left alone. */
@Composable
internal fun HandleCameraMoveEvent(
    mapView: MapView,
    cameraMoveEvent: Flow<GeoPoint>,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, cameraMoveEvent) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            cameraMoveEvent.collect { mapView.controller.animateTo(it) }
        }
    }
}

/**
 * Reports the map center once panning or zooming settles. Nothing is reported before the first
 * camera move, because an unlaid-out map reports (0, 0).
 */
@Composable
internal fun ReportMapCenter(
    mapView: MapView,
    onMapCenterChange: (GeoPoint) -> Unit,
) {
    val currentOnChange by rememberUpdatedState(onMapCenterChange)
    DisposableEffect(mapView) {
        fun report() {
            val center = mapView.mapCenter
            currentOnChange(GeoPoint(center.latitude, center.longitude))
        }
        val listener = DelayedMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                report()
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                report()
                return false
            }
        }, MAP_CENTER_REPORT_DELAY_MS)
        mapView.addMapListener(listener)
        onDispose { mapView.removeMapListener(listener) }
    }
}

private const val MAP_CENTER_REPORT_DELAY_MS = 500L

/**
 * Keeps the spoof-target [Marker] on the map in sync with [lastClickedLocation].
 *
 * - When [lastClickedLocation] is non-null the marker is added to [mapView]'s overlay list (if not
 *   already present), moved to the new position, and the camera is animated to it.
 * - When [lastClickedLocation] is `null` the marker is removed and the map is invalidated.
 *
 * The effect is keyed on [lastClickedLocation] so it reruns only when the marker moves.
 *
 * @param mapView The map whose overlays are updated.
 * @param userMarker The persistent spoof-target marker instance.
 * @param lastClickedLocation The current spoof target, or `null` if none.
 */
@Composable
internal fun HandleMarkerUpdates(
    mapView: MapView,
    userMarker: Marker,
    lastClickedLocation: GeoPoint?,
) {
    LaunchedEffect(lastClickedLocation) {
        if (lastClickedLocation != null) {
            // Add the marker to the map if not already added
            if (!mapView.overlays.contains(userMarker)) {
                mapView.overlays.add(userMarker)
            }
            userMarker.position = lastClickedLocation
            mapView.controller.animateTo(lastClickedLocation)
            mapView.invalidate()
        } else {
            // Remove the marker from the map if it exists
            if (mapView.overlays.contains(userMarker)) {
                mapView.overlays.remove(userMarker)
                mapView.invalidate()
            }
        }
    }
}

/**
 * Installs a [MapEventsOverlay] that translates single-tap gestures into spoof-marker placements.
 *
 * The overlay is created once and lives for the lifetime of [mapView] (keyed on it). [isPlaying]
 * and [onClickedLocationChange] are captured via [rememberUpdatedState] so that toggling spoofing
 * on/off never causes the overlay to be torn down and recreated — avoiding the resulting map
 * flicker and gesture interruption.
 *
 * Taps are ignored while [isPlaying] is `true` so the marker cannot be accidentally moved during
 * an active spoofing session.
 *
 * @param mapView The map to install the click listener on.
 * @param isPlaying Whether spoofing is currently active.
 * @param onClickedLocationChange Callback fired with the tapped [GeoPoint].
 */
@Composable
internal fun SetupMapClickListener(
    mapView: MapView,
    isPlaying: Boolean,
    onClickedLocationChange: (GeoPoint?) -> Unit
) {
    // Keep the latest values without re-creating the overlay each time spoofing toggles.
    val currentIsPlaying by rememberUpdatedState(isPlaying)
    val currentOnClickedLocationChange by rememberUpdatedState(onClickedLocationChange)
    DisposableEffect(mapView) {
        val mapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                if (!currentIsPlaying) {
                    currentOnClickedLocationChange(p)
                }
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean {
                return false
            }
        }

        val mapEventsOverlay = MapEventsOverlay(mapEventsReceiver)
        mapView.overlays.add(mapEventsOverlay)

        onDispose {
            mapView.overlays.remove(mapEventsOverlay)
        }
    }
}

/**
 * Positions the camera once per [mapView].
 *
 * - **First ever load** ([hasResolvedInitialLocation] == false): resolves an initial target in the
 *   order defined by [resolveInitialCamera], clearing the loading state when done and reporting
 *   completion via [onInitialLocationResolved].
 * - **Re-entry** ([hasResolvedInitialLocation] == true): restores the last camera instantly with no
 *   spinner and no detection. The remembered [mapZoom] is re-applied; for the no-marker case the
 *   last [userLocation] is re-centered. Marker centering is left to [HandleMarkerUpdates].
 *
 * Either way, once positioned it never re-centers again for this [mapView], so later marker changes
 * (taps, go-to-point, clear) don't reset the zoom. The effect is keyed on [lastClickedLocation] so
 * an asynchronously-loaded saved marker can supersede an in-flight device-location lookup.
 */
@Composable
internal fun CenterMapOnUserLocation(
    mapView: MapView,
    locationOverlay: MyLocationNewOverlay,
    lastClickedLocation: GeoPoint?,
    userLocation: GeoPoint?,
    mapZoom: Double?,
    hasResolvedInitialLocation: Boolean,
    onUserLocationChange: (GeoPoint) -> Unit,
    onMapZoomChange: (Double) -> Unit,
    onLoadingFinished: () -> Unit,
    onInitialLocationResolved: () -> Unit,
) {
    val context = LocalContext.current
    val centeredOnce = remember(mapView) { mutableStateOf(false) }
    LaunchedEffect(mapView, lastClickedLocation) {
        if (centeredOnce.value) return@LaunchedEffect

        if (hasResolvedInitialLocation) {
            // Re-entry: restore the last camera without re-detecting or showing a spinner.
            mapView.controller.setZoom(mapZoom ?: DEFAULT_MAP_ZOOM)
            if (lastClickedLocation == null && userLocation != null) {
                mapView.controller.setCenter(userLocation)
            }
            centeredOnce.value = true
            return@LaunchedEffect
        }

        val camera = resolveInitialCamera(
            marker = lastClickedLocation?.let { LatLon(it.latitude, it.longitude) },
            savedZoom = mapZoom,
            lastKnown = { getLastKnownDeviceLocation(context) },
            liveFix = { locationOverlay.myLocation?.let { LatLon(it.latitude, it.longitude) } },
        )
        val point = GeoPoint(camera.target.latitude, camera.target.longitude)
        if (camera.source.isDeviceLocation) onUserLocationChange(point)
        mapView.controller.setZoom(camera.zoom)
        if (camera.source.animates) mapView.controller.animateTo(point) else mapView.controller.setCenter(point)
        onMapZoomChange(camera.zoom)
        onLoadingFinished()
        onInitialLocationResolved()
        centeredOnce.value = true
    }
}

/**
 * Ties the osmdroid [MapView] and [MyLocationNewOverlay] to the Compose lifecycle.
 *
 * On entry: calls [MapView.onResume] and re-enables location updates so that the overlay starts
 * receiving GPS fixes as soon as the screen becomes active.
 *
 * On dispose (i.e. when the user navigates away):
 * 1. Captures the current zoom via [onMapZoomChange] so it can be restored on re-entry without
 *    re-running location detection or showing a spinner (see [CenterMapOnUserLocation]).
 * 2. Disables location updates to stop battery drain while the screen is off-stack.
 * 3. Clears all overlays, calls [MapView.onPause] and [MapView.onDetach] to release osmdroid
 *    resources correctly.
 *
 * Keyed on [Unit] so it runs exactly once per composition, matching the [MapView] lifetime.
 *
 * @param mapView The osmdroid map view to manage.
 * @param locationOverlay The location overlay whose updates are started/stopped.
 * @param onMapZoomChange Callback to persist the final zoom level on dispose.
 */
@Composable
internal fun ManageMapViewLifecycle(
    mapView: MapView,
    locationOverlay: MyLocationNewOverlay,
    onMapZoomChange: (Double) -> Unit
) {
    DisposableEffect(Unit) {
        mapView.onResume()
        locationOverlay.enableMyLocation()
        onDispose {
            // Capture the final zoom so it can be restored when the map screen is returned to.
            onMapZoomChange(mapView.zoomLevelDouble)
            locationOverlay.disableMyLocation()
            mapView.overlays.clear()
            mapView.onPause()
            mapView.onDetach()
        }
    }
}
