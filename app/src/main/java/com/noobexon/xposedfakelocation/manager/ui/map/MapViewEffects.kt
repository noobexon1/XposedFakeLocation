package com.noobexon.xposedfakelocation.manager.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.DEFAULT_MAP_ZOOM
import com.noobexon.xposedfakelocation.data.LOCATION_DETECTION_DELAY_MS
import com.noobexon.xposedfakelocation.data.LOCATION_DETECTION_MAX_ATTEMPTS
import com.noobexon.xposedfakelocation.data.WORLD_MAP_ZOOM
import com.noobexon.xposedfakelocation.data.model.RouteWaypoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
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
 * - **First ever load** ([hasResolvedInitialLocation] == false): resolves an initial target — saved
 *   marker, else last-known device location, else a short GPS poll, else a world-view fallback —
 *   clearing the loading state when done and reporting completion via [onInitialLocationResolved].
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

        if (lastClickedLocation != null) {
            centerOnMarkerLocation(mapView, lastClickedLocation, mapZoom, onMapZoomChange, onLoadingFinished)
        } else {
            val lastKnown = getLastKnownDeviceLocation(context)
            if (lastKnown != null) {
                centerOnGeoPoint(mapView, lastKnown, mapZoom, onUserLocationChange, onMapZoomChange, onLoadingFinished)
            } else {
                val found = tryToFindAndCenterUserLocation(mapView, locationOverlay, mapZoom, onUserLocationChange, onMapZoomChange, onLoadingFinished)
                if (!found) {
                    centerOnDefaultLocation(mapView, onMapZoomChange, onLoadingFinished)
                }
            }
        }
        onInitialLocationResolved()
        centeredOnce.value = true
    }
}

/**
 * Restores the camera to a previously placed spoof marker during initial load.
 *
 * Uses the persisted [mapZoom] when available so the zoom level is exactly as the user left it,
 * falling back to [DEFAULT_MAP_ZOOM] for first-ever launches.
 *
 * @param mapView The map to position.
 * @param markerLocation The spoof-target coordinate to centre on.
 * @param mapZoom Persisted zoom level, or `null` if not yet set.
 * @param onMapZoomChange Callback to persist the applied zoom.
 * @param onLoadingFinished Callback to clear [MapUiState.isLoading].
 */
private suspend fun centerOnMarkerLocation(
    mapView: MapView,
    markerLocation: GeoPoint,
    mapZoom: Double?,
    onMapZoomChange: (Double) -> Unit,
    onLoadingFinished: () -> Unit
) {
    val zoom = mapZoom ?: DEFAULT_MAP_ZOOM
    mapView.controller.setZoom(zoom)
    mapView.controller.animateTo(markerLocation)
    onMapZoomChange(zoom)
    onLoadingFinished()
}

/**
 * Centres the camera on [point] and propagates the resolved location and zoom up to the ViewModel.
 * Used when a last-known device location is available instantly without polling.
 *
 * Uses the persisted [mapZoom] when available so the zoom level is exactly as the user left it,
 * falling back to [DEFAULT_MAP_ZOOM] for first-ever launches.
 *
 * @param mapView The map to centre.
 * @param point The device location to centre on.
 * @param mapZoom Persisted zoom level, or `null` if not yet set.
 * @param onUserLocationChange Callback to cache the location in [MapViewModel].
 * @param onMapZoomChange Callback to persist the applied zoom.
 * @param onLoadingFinished Callback to clear [MapUiState.isLoading].
 */
private fun centerOnGeoPoint(
    mapView: MapView,
    point: GeoPoint,
    mapZoom: Double?,
    onUserLocationChange: (GeoPoint) -> Unit,
    onMapZoomChange: (Double) -> Unit,
    onLoadingFinished: () -> Unit
) {
    val zoom = mapZoom ?: DEFAULT_MAP_ZOOM
    mapView.controller.setZoom(zoom)
    mapView.controller.setCenter(point)
    onUserLocationChange(point)
    onMapZoomChange(zoom)
    onLoadingFinished()
}

/**
 * Queries all enabled [LocationManager] providers on [Dispatchers.IO] and returns the most recent
 * last-known fix, or `null` if none is available or permission is not granted.
 *
 * Running on [Dispatchers.IO] avoids blocking the main thread; [LocationManager.getLastKnownLocation]
 * can perform disk I/O on some devices.
 *
 * @param context Application context used to access [LocationManager] and check permissions.
 * @return The freshest available [GeoPoint], or `null`.
 */
private suspend fun getLastKnownDeviceLocation(context: Context): GeoPoint? = withContext(Dispatchers.IO) {
    val granted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    if (!granted) return@withContext null

    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return@withContext null
    val providers = try {
        lm.getProviders(true)
    } catch (e: SecurityException) {
        return@withContext null
    }
    var best: Location? = null
    for (provider in providers) {
        val loc = try {
            lm.getLastKnownLocation(provider)
        } catch (e: SecurityException) {
            null
        } ?: continue
        if (best == null || loc.time > best.time) best = loc
    }
    best?.let { GeoPoint(it.latitude, it.longitude) }
}

/**
 * Polls [locationOverlay] up to [LOCATION_DETECTION_MAX_ATTEMPTS] times (with
 * [LOCATION_DETECTION_DELAY_MS] between each attempt) and centres the camera if a fix is obtained.
 *
 * This path is taken when [getLastKnownDeviceLocation] returns `null` — i.e. no cached fix exists —
 * and gives the GPS/network provider a short window to acquire one before falling back to
 * [centerOnDefaultLocation].
 *
 * Uses the persisted [mapZoom] when available so the zoom level is exactly as the user left it,
 * falling back to [DEFAULT_MAP_ZOOM] for first-ever launches.
 *
 * @param mapView The map to centre if a location is found.
 * @param locationOverlay Source of live location fixes.
 * @param mapZoom Persisted zoom level, or `null` if not yet set.
 * @param onUserLocationChange Callback to cache the location in [MapViewModel].
 * @param onMapZoomChange Callback to persist the applied zoom.
 * @param onLoadingFinished Callback to clear [MapUiState.isLoading].
 * @return `true` if a location was found and the camera was centred; `false` if the timeout elapsed.
 */
private suspend fun tryToFindAndCenterUserLocation(
    mapView: MapView,
    locationOverlay: MyLocationNewOverlay,
    mapZoom: Double?,
    onUserLocationChange: (GeoPoint) -> Unit,
    onMapZoomChange: (Double) -> Unit,
    onLoadingFinished: () -> Unit
): Boolean {
    val zoom = mapZoom ?: DEFAULT_MAP_ZOOM
    repeat(LOCATION_DETECTION_MAX_ATTEMPTS) {
        val userLocation = locationOverlay.myLocation
        if (userLocation != null) {
            onUserLocationChange(userLocation)
            mapView.controller.setZoom(zoom)
            mapView.controller.animateTo(userLocation)
            onMapZoomChange(zoom)
            onLoadingFinished()
            return true
        }
        delay(LOCATION_DETECTION_DELAY_MS)
    }
    return false
}

/**
 * Last-resort fallback: centres the camera on (0°, 0°) at [WORLD_MAP_ZOOM] when no device
 * location could be determined after [LOCATION_DETECTION_MAX_ATTEMPTS] polling attempts.
 *
 * @param mapView The map to centre.
 * @param onMapZoomChange Callback to persist the applied zoom.
 * @param onLoadingFinished Callback to clear [MapUiState.isLoading].
 */
private fun centerOnDefaultLocation(
    mapView: MapView,
    onMapZoomChange: (Double) -> Unit,
    onLoadingFinished: () -> Unit
) {
    mapView.controller.setZoom(WORLD_MAP_ZOOM)
    mapView.controller.setCenter(GeoPoint(0.0, 0.0))
    onMapZoomChange(WORLD_MAP_ZOOM)
    onLoadingFinished()
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

/**
 * Draws or removes the active route overlay (Polyline + waypoint Markers) on the map.
 *
 * When [waypoints] is non-empty, a blue Polyline connecting all waypoints is drawn and a Marker
 * is placed at each waypoint. The camera is zoomed to fit the entire route bounding box.
 * When [waypoints] is empty, the route overlay is removed.
 *
 * The overlay is keyed on the [waypoints] list so it is redrawn whenever the route changes.
 *
 * @param mapView The osmdroid map to draw on.
 * @param waypoints The waypoints of the currently playing route, or empty to clear.
 */
@Composable
internal fun HandleActiveRouteOverlay(
    mapView: MapView,
    waypoints: List<RouteWaypoint>,
) {
    LaunchedEffect(waypoints) {
        // Remove old route overlays (Polyline and route Markers only)
        val toRemove = mutableListOf<Any>()
        for (overlay in mapView.overlays) {
            if (overlay is Polyline || (overlay is Marker && overlay.relatedObject == "route_waypoint")) {
                toRemove.add(overlay)
            }
        }
        toRemove.forEach { mapView.overlays.remove(it) }

        if (waypoints.isEmpty()) {
            mapView.invalidate()
            return@LaunchedEffect
        }

        val geoPoints = waypoints.map { GeoPoint(it.latitude, it.longitude) }

        val polyline = Polyline().apply {
            setPoints(geoPoints)
            outlinePaint.apply {
                color = android.graphics.Color.rgb(33, 150, 243)
                strokeWidth = 6f
                isAntiAlias = true
            }
        }
        mapView.overlays.add(polyline)

        waypoints.forEachIndexed { index, wp ->
            val marker = Marker(mapView).apply {
                position = GeoPoint(wp.latitude, wp.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "${index + 1}: ${wp.name}"
                snippet = "%.5f, %.5f".format(wp.latitude, wp.longitude)
                relatedObject = "route_waypoint"
            }
            mapView.overlays.add(marker)
        }

        if (geoPoints.size >= 2) {
            val box = org.osmdroid.util.BoundingBox.fromGeoPoints(geoPoints)
            mapView.zoomToBoundingBox(box.increaseByScale(1.2f), true, 48)
        } else if (geoPoints.size == 1) {
            mapView.controller.setZoom(15.0)
            mapView.controller.setCenter(geoPoints[0])
        }

        mapView.invalidate()
    }
}
