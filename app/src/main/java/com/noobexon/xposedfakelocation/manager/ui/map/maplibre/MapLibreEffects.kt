package com.noobexon.xposedfakelocation.manager.ui.map.maplibre

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.DEFAULT_MAP_ZOOM
import com.noobexon.xposedfakelocation.manager.geo.LatLon
import com.noobexon.xposedfakelocation.manager.ui.map.MapZoom
import com.noobexon.xposedfakelocation.manager.ui.map.getLastKnownDeviceLocation
import com.noobexon.xposedfakelocation.manager.ui.map.hasLocationPermission
import com.noobexon.xposedfakelocation.manager.ui.map.resolveInitialCamera
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.osmdroid.util.GeoPoint

internal fun GeoPoint.toLatLng(): LatLng = LatLng(latitude, longitude)

internal fun MapLibreMap.animateTo(point: GeoPoint) {
    animateCamera(CameraUpdateFactory.newLatLng(point.toLatLng()))
}

/** The blue dot's latest fix, or `null` before the location component has one. */
@SuppressLint("MissingPermission")
internal fun MapLibreMap.currentFix(): LatLon? {
    val component = locationComponent
    if (!component.isLocationComponentActivated || !component.isLocationComponentEnabled) return null
    return component.lastKnownLocation?.let { LatLon(it.latitude, it.longitude) }
}

/**
 * Shows the device location (blue dot) once the first style has loaded, if location permission is
 * granted. The component re-adds itself on later style loads.
 */
@SuppressLint("MissingPermission")
@Composable
internal fun ActivateBlueDot(map: MapLibreMap, style: Style?) {
    val context = LocalContext.current
    LaunchedEffect(map, style) {
        if (style == null) return@LaunchedEffect
        val component = map.locationComponent
        if (component.isLocationComponentActivated || !hasLocationPermission(context)) return@LaunchedEffect
        component.activateLocationComponent(
            LocationComponentActivationOptions.builder(context, style).useDefaultLocationEngine(true).build()
        )
        component.setLocationComponentEnabled(true)
        component.setCameraMode(CameraMode.NONE)
        component.setRenderMode(RenderMode.NORMAL)
    }
}

/**
 * Animates the camera for go-to-point (also moving the marker), search camera moves and
 * center-on-me. Collected only while the screen is started, like the osmdroid map.
 */
@Composable
internal fun HandleMapLibreCameraEvents(
    map: MapLibreMap,
    goToPointEvent: Flow<GeoPoint>,
    centerMapEvent: Flow<Unit>,
    cameraMoveEvent: Flow<GeoPoint>,
    onClickedLocationChange: (GeoPoint?) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val userLocationNotAvailable = stringResource(R.string.toast_user_location_not_available)
    val currentOnClickedLocationChange by rememberUpdatedState(onClickedLocationChange)
    LaunchedEffect(map, lifecycleOwner, goToPointEvent, centerMapEvent, cameraMoveEvent) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                goToPointEvent.collect { point ->
                    map.animateTo(point)
                    currentOnClickedLocationChange(point)
                }
            }
            launch { cameraMoveEvent.collect { map.animateTo(it) } }
            launch {
                centerMapEvent.collect {
                    val fix = map.currentFix()
                    if (fix != null) {
                        map.animateTo(GeoPoint(fix.latitude, fix.longitude))
                    } else {
                        Toast.makeText(context, userLocationNotAvailable, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

/**
 * Positions the camera once per map, with the same rules as the osmdroid map: first load uses
 * [resolveInitialCamera]; re-entry restores the saved zoom (and the last device location when
 * there is no marker; the marker effect centers on the marker).
 */
@Composable
internal fun PositionInitialMapLibreCamera(
    map: MapLibreMap,
    positioned: Boolean,
    lastClickedLocation: GeoPoint?,
    userLocation: GeoPoint?,
    mapZoom: Double?,
    hasResolvedInitialLocation: Boolean,
    onUserLocationChange: (GeoPoint) -> Unit,
    onMapZoomChange: (Double) -> Unit,
    onLoadingFinished: () -> Unit,
    onInitialLocationResolved: () -> Unit,
    onPositioned: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(map, lastClickedLocation) {
        if (positioned) return@LaunchedEffect
        if (hasResolvedInitialLocation) {
            val zoom = MapZoom.toMapLibre(mapZoom ?: DEFAULT_MAP_ZOOM)
            if (lastClickedLocation == null && userLocation != null) {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation.toLatLng(), zoom))
            } else {
                map.moveCamera(CameraUpdateFactory.zoomTo(zoom))
            }
            onPositioned()
            return@LaunchedEffect
        }
        val camera = resolveInitialCamera(
            marker = lastClickedLocation?.let { LatLon(it.latitude, it.longitude) },
            savedZoom = mapZoom,
            lastKnown = { getLastKnownDeviceLocation(context) },
            liveFix = { map.currentFix() },
        )
        val point = GeoPoint(camera.target.latitude, camera.target.longitude)
        if (camera.source.isDeviceLocation) onUserLocationChange(point)
        val update = CameraUpdateFactory.newLatLngZoom(point.toLatLng(), MapZoom.toMapLibre(camera.zoom))
        if (camera.source.animates) map.animateCamera(update) else map.moveCamera(update)
        onMapZoomChange(camera.zoom)
        onLoadingFinished()
        onInitialLocationResolved()
        onPositioned()
    }
}

/**
 * Reports the map center (for search bias) and the visible area (for offline downloads) whenever
 * the camera settles. Nothing is reported until [enabled] (the initial camera is placed).
 */
@Composable
internal fun ReportMapLibreCamera(
    map: MapLibreMap,
    enabled: Boolean,
    onMapCenterChange: (GeoPoint) -> Unit,
    onVisibleAreaChange: (VisibleArea) -> Unit,
) {
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnMapCenterChange by rememberUpdatedState(onMapCenterChange)
    val currentOnVisibleAreaChange by rememberUpdatedState(onVisibleAreaChange)
    DisposableEffect(map) {
        val listener = MapLibreMap.OnCameraIdleListener {
            if (!currentEnabled) return@OnCameraIdleListener
            val camera = map.cameraPosition
            val target = camera.target ?: return@OnCameraIdleListener
            currentOnMapCenterChange(GeoPoint(target.latitude, target.longitude))
            val bounds = map.projection.visibleRegion.latLngBounds
            currentOnVisibleAreaChange(
                VisibleArea(
                    bounds = AreaBounds(bounds.latitudeNorth, bounds.longitudeEast, bounds.latitudeSouth, bounds.longitudeWest),
                    centerLatitude = target.latitude,
                    centerLongitude = target.longitude,
                    zoom = camera.zoom,
                )
            )
        }
        map.addOnCameraIdleListener(listener)
        onDispose { map.removeOnCameraIdleListener(listener) }
    }
}
