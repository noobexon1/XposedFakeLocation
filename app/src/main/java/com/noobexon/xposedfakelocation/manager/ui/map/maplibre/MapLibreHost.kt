package com.noobexon.xposedfakelocation.manager.ui.map.maplibre

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import android.view.Gravity
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.manager.ui.map.LoadingSpinner
import com.noobexon.xposedfakelocation.manager.ui.map.MapZoom
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.job
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.osmdroid.util.GeoPoint
import kotlin.math.roundToInt

private const val TAG = "MapLibreHost"

/**
 * The MapLibre map: a [MapView] in [AndroidView] with the same features as the osmdroid map.
 * Shows a card instead of the map when MapLibre can't run here, and a card over the map when the
 * style fails to load. [styleUrl] is the resolved style (see `resolveStyleUrl`).
 */
@Composable
internal fun MapLibreHost(
    styleUrl: String,
    isLoading: Boolean,
    lastClickedLocation: GeoPoint?,
    userLocation: GeoPoint?,
    mapZoom: Double?,
    hasResolvedInitialLocation: Boolean,
    goToPointEvent: Flow<GeoPoint>,
    centerMapEvent: Flow<Unit>,
    cameraMoveEvent: Flow<GeoPoint>,
    onClickedLocationChange: (GeoPoint?) -> Unit,
    onUserLocationChange: (GeoPoint) -> Unit,
    onMapZoomChange: (Double) -> Unit,
    onLoadingFinished: () -> Unit,
    onInitialLocationResolved: () -> Unit,
    onMapCenterChange: (GeoPoint) -> Unit,
    onVisibleAreaChange: (VisibleArea) -> Unit,
    onUseOsmdroid: () -> Unit,
) {
    val context = LocalContext.current
    val mapView = remember { createMapView(context) }
    if (mapView == null) {
        MapLibreMessage(R.string.maplibre_unavailable_title, R.string.maplibre_unavailable_message, onRetry = null, onUseOsmdroid = onUseOsmdroid)
        return
    }
    val density = LocalDensity.current.density
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var styleFailed by remember { mutableStateOf(false) }
    var styleAttempt by remember { mutableIntStateOf(0) }
    var positioned by remember { mutableStateOf(false) }
    val currentOnMapZoomChange by rememberUpdatedState(onMapZoomChange)

    MapViewLifecycle(mapView) {
        map?.let { currentOnMapZoomChange(MapZoom.fromMapLibre(it.cameraPosition.zoom)) }
    }
    DisposableEffect(mapView) {
        var disposed = false
        mapView.getMapAsync { ready ->
            if (!disposed) {
                configureMap(ready, density)
                map = ready
            }
        }
        val onFail = MapView.OnDidFailLoadingMapListener { styleFailed = true }
        mapView.addOnDidFailLoadingMapListener(onFail)
        onDispose {
            disposed = true
            mapView.removeOnDidFailLoadingMapListener(onFail)
        }
    }
    LaunchedEffect(map, styleUrl, styleAttempt) {
        val ready = map ?: return@LaunchedEffect
        style = null
        styleFailed = false
        val job = coroutineContext.job
        ready.setStyle(Style.Builder().fromUri(styleUrl)) { loaded -> if (job.isActive) style = loaded }
        awaitCancellation()
    }

    val currentMap = map
    if (currentMap != null) {
        ActivateBlueDot(currentMap, style)
        HandleMapLibreCameraEvents(currentMap, goToPointEvent, centerMapEvent, cameraMoveEvent, onClickedLocationChange)
        PositionInitialMapLibreCamera(
            map = currentMap,
            positioned = positioned,
            lastClickedLocation = lastClickedLocation,
            userLocation = userLocation,
            mapZoom = mapZoom,
            hasResolvedInitialLocation = hasResolvedInitialLocation,
            onUserLocationChange = onUserLocationChange,
            onMapZoomChange = onMapZoomChange,
            onLoadingFinished = onLoadingFinished,
            onInitialLocationResolved = onInitialLocationResolved,
            onPositioned = { positioned = true },
        )
        ReportMapLibreCamera(currentMap, positioned, onMapCenterChange, onVisibleAreaChange)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        if (isLoading) LoadingSpinner(Modifier.background(MaterialTheme.colorScheme.background))
        if (styleFailed) {
            MapLibreMessage(
                R.string.map_style_error_title,
                R.string.map_style_error_message,
                onRetry = { styleAttempt++ },
                onUseOsmdroid = onUseOsmdroid,
            )
        }
    }
}

/** `null` when MapLibre can't run on this device. */
private fun createMapView(context: Context): MapView? {
    if (!MapLibreSupport.ensure(context)) return null
    return try {
        MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply { onCreate(null) }
    } catch (e: RuntimeException) {
        Log.w(TAG, "Couldn't create the MapLibre map", e)
        null
    }
}

private fun configureMap(map: MapLibreMap, density: Float) {
    val margin = (8 * density).roundToInt()
    map.uiSettings.apply {
        setLogoEnabled(false)
        setAttributionEnabled(true)
        setAttributionGravity(Gravity.TOP or Gravity.START)
        setAttributionMargins(margin, margin, margin, margin)
        setCompassEnabled(true)
        setCompassMargins(margin, margin, margin, margin)
        setRotateGesturesEnabled(true)
        setTiltGesturesEnabled(true)
    }
}

/**
 * Forwards the screen lifecycle to [mapView] and destroys it when the map leaves composition.
 * [onDestroy] runs first, while the map can still be read.
 */
@Composable
private fun MapViewLifecycle(mapView: MapView, onDestroy: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnDestroy by rememberUpdatedState(onDestroy)
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        val memory = object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) = Unit
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = mapView.onLowMemory()
        }
        lifecycle.addObserver(observer)
        context.registerComponentCallbacks(memory)
        onDispose {
            context.unregisterComponentCallbacks(memory)
            lifecycle.removeObserver(observer)
            currentOnDestroy()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }
}

/** A centered card with a message, an optional Retry and "Use osmdroid". */
@Composable
private fun MapLibreMessage(
    @StringRes title: Int,
    @StringRes message: Int,
    onRetry: (() -> Unit)?,
    onUseOsmdroid: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(message), style = MaterialTheme.typography.bodyMedium)
                Row(modifier = Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onRetry != null) {
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                    }
                    TextButton(onClick = onUseOsmdroid) { Text(stringResource(R.string.map_use_osmdroid)) }
                }
            }
        }
    }
}
