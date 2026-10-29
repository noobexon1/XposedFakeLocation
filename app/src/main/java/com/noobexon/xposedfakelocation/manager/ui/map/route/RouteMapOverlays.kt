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
import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.manager.route.RoadFailure
import com.noobexon.xposedfakelocation.manager.ui.map.PointRole
import com.noobexon.xposedfakelocation.manager.ui.map.SegmentMarker
import com.noobexon.xposedfakelocation.manager.ui.map.routeDrawing
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
 * Draws [route] as numbered markers and direction-arrowed segments (along their road path when
 * one is stored), rebuilding the overlays whenever the route, [editable] or [roadFailures] change.
 * Pass `null` to remove them.
 */
@Composable
internal fun RouteOverlaysEffect(
    mapView: MapView,
    route: Route?,
    editable: Boolean,
    roadFailures: Map<String, RoadFailure>,
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
    val warningColor = MaterialTheme.colorScheme.error.toArgb()
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

    LaunchedEffect(mapView, route, editable, roadFailures, lineColor, pointColor, startColor, endColor, textColor, warningColor) {
        mapView.overlays.removeAll(overlays)
        overlays.clear()
        if (route != null && route.points.isNotEmpty()) {
            val density = context.resources.displayMetrics.density
            val drawing = routeDrawing(route, roadFailures)
            drawing.segments.forEach { segment ->
                overlays += Polyline(mapView).apply {
                    setPoints(segment.path.map { GeoPoint(it.first, it.second) })
                    outlinePaint.color = if (segment.loading) (lineColor and 0x00FFFFFF) or LOADING_ALPHA else lineColor
                    outlinePaint.strokeWidth = 4 * density
                    if (segment.dashed) outlinePaint.pathEffect = DashPathEffect(floatArrayOf(12 * density, 8 * density), 0f)
                    setOnClickListener { _, _, _ ->
                        if (editable) currentOnSegmentTap(segment.fromId, segment.toId)
                        true
                    }
                }
                segment.marker?.let { marker ->
                    overlays += Marker(mapView).apply {
                        position = GeoPoint(marker.latitude, marker.longitude)
                        icon = when (marker) {
                            is SegmentMarker.Warning -> RouteMarkerIcons.warning(context, warningColor)
                            is SegmentMarker.Arrow -> RouteMarkerIcons.arrow(context, marker.bearingDegrees.toFloat(), lineColor)
                        }
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        setInfoWindow(null)
                        setOnMarkerClickListener { _, _ ->
                            if (editable) currentOnSegmentTap(segment.fromId, segment.toId)
                            true
                        }
                    }
                }
            }
            drawing.points.forEach { point ->
                val fill = when (point.role) {
                    PointRole.START -> startColor
                    PointRole.END -> endColor
                    PointRole.NORMAL -> pointColor
                }
                overlays += Marker(mapView).apply {
                    position = GeoPoint(point.latitude, point.longitude)
                    icon = RouteMarkerIcons.numbered(context, point.number, fill, textColor)
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

// 40% alpha for a road segment whose path is still loading.
private const val LOADING_ALPHA = 0x66 shl 24

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
