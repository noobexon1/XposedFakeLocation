package com.noobexon.xposedfakelocation.manager.ui.map.route

import android.graphics.DashPathEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.noobexon.xposedfakelocation.data.GeoMath
import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.data.model.travelSegments
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import kotlin.coroutines.resume

/**
 * Draws [route] as numbered markers and direction-arrowed segments, rebuilding the overlays
 * whenever the route or [editable] changes. Pass `null` to remove them.
 */
@Composable
internal fun RouteOverlaysEffect(
    mapView: MapView,
    route: Route?,
    editable: Boolean,
    onPointTap: (String) -> Unit,
    onPointMoved: (String, GeoPoint) -> Unit,
    onSegmentTap: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val lineColor = MaterialTheme.colorScheme.primary.toArgb()
    val pointColor = MaterialTheme.colorScheme.primary.toArgb()
    val startColor = MaterialTheme.colorScheme.tertiary.toArgb()
    val endColor = MaterialTheme.colorScheme.error.toArgb()
    val textColor = MaterialTheme.colorScheme.onPrimary.toArgb()
    val currentOnPointTap by rememberUpdatedState(onPointTap)
    val currentOnPointMoved by rememberUpdatedState(onPointMoved)
    val currentOnSegmentTap by rememberUpdatedState(onSegmentTap)
    val overlays = remember(mapView) { mutableListOf<Overlay>() }

    DisposableEffect(mapView) {
        onDispose {
            mapView.overlays.removeAll(overlays)
            overlays.clear()
        }
    }

    LaunchedEffect(mapView, route, editable, lineColor, pointColor, startColor, endColor, textColor) {
        mapView.overlays.removeAll(overlays)
        overlays.clear()
        if (route != null && route.points.isNotEmpty()) {
            val density = context.resources.displayMetrics.density
            route.travelSegments().forEach { segment ->
                val a = segment.from
                val b = segment.to
                overlays += Polyline(mapView).apply {
                    setPoints(listOf(GeoPoint(a.latitude, a.longitude), GeoPoint(b.latitude, b.longitude)))
                    outlinePaint.color = lineColor
                    outlinePaint.strokeWidth = 4 * density
                    if (segment.closing) outlinePaint.pathEffect = DashPathEffect(floatArrayOf(12 * density, 8 * density), 0f)
                    setOnClickListener { _, _, _ ->
                        if (editable) currentOnSegmentTap(a.id, b.id)
                        true
                    }
                }
                val distance = GeoMath.distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
                if (distance > 0.0) {
                    val bearing = GeoMath.initialBearing(a.latitude, a.longitude, b.latitude, b.longitude)
                    val (midLat, midLon) = GeoMath.destination(a.latitude, a.longitude, bearing, distance / 2)
                    overlays += Marker(mapView).apply {
                        position = GeoPoint(midLat, midLon)
                        icon = RouteMarkerIcons.arrow(context, bearing.toFloat(), lineColor)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        setInfoWindow(null)
                        setOnMarkerClickListener { _, _ ->
                            if (editable) currentOnSegmentTap(a.id, b.id)
                            true
                        }
                    }
                }
            }
            val last = route.points.size
            val startNumber = if (route.reversed) last else 1
            val endNumber = if (route.loop) -1 else if (route.reversed) 1 else last
            route.points.forEachIndexed { index, point ->
                val number = index + 1
                val fill = when (number) {
                    startNumber -> startColor
                    endNumber -> endColor
                    else -> pointColor
                }
                overlays += Marker(mapView).apply {
                    position = GeoPoint(point.latitude, point.longitude)
                    icon = RouteMarkerIcons.numbered(context, number, fill, textColor)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setInfoWindow(null)
                    isDraggable = editable
                    setOnMarkerClickListener { _, _ ->
                        if (editable) currentOnPointTap(point.id)
                        true
                    }
                    setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                        override fun onMarkerDrag(marker: Marker) = Unit
                        override fun onMarkerDragStart(marker: Marker) = Unit
                        override fun onMarkerDragEnd(marker: Marker) = currentOnPointMoved(point.id, marker.position)
                    })
                }
            }
            // Directly above the tap overlay: taps reach the route first, while the position and
            // location markers (added later) still draw on top.
            val tapOverlayIndex = mapView.overlays.indexOfFirst { it is MapEventsOverlay }
            mapView.overlays.addAll(if (tapOverlayIndex >= 0) tapOverlayIndex + 1 else mapView.overlays.size, overlays)
        }
        mapView.invalidate()
    }
}

/** Frames the whole route when [fitRouteEvent] fires (after the map has laid out and restored its camera). */
@Composable
internal fun HandleFitRouteEvent(mapView: MapView, route: Route?, fitRouteEvent: Flow<Unit>) {
    val currentRoute by rememberUpdatedState(route)
    LaunchedEffect(mapView, fitRouteEvent) {
        fitRouteEvent.collect {
            mapView.awaitFirstLayout()
            delay(FIT_DELAY_MS)
            val points = currentRoute?.points.orEmpty().map { GeoPoint(it.latitude, it.longitude) }
            when {
                points.size == 1 -> mapView.controller.animateTo(points.first())
                points.size > 1 -> mapView.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(points).increaseByScale(1.4f), true)
            }
        }
    }
}

// Lets CenterMapOnUserLocation restore the camera first, so this fit isn't overwritten.
private const val FIT_DELAY_MS = 400L

private suspend fun MapView.awaitFirstLayout() {
    // osmdroid ignores new first-layout listeners once its own layout flag is set.
    if (isLayoutOccurred) return
    suspendCancellableCoroutine<Unit> { continuation ->
        val listener = MapView.OnFirstLayoutListener { _, _, _, _, _ ->
            if (continuation.isActive) continuation.resume(Unit)
        }
        addOnFirstLayoutListener(listener)
        continuation.invokeOnCancellation { removeOnFirstLayoutListener(listener) }
    }
}
