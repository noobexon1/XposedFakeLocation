package com.noobexon.xposedfakelocation.manager.ui.routes

import android.content.Context
import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.noobexon.xposedfakelocation.data.model.RouteWaypoint
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
fun RouteMapView(
    waypoints: List<RouteWaypoint>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapView = remember { createMapView(context) }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.overlays.clear()
            mapView.onPause()
            mapView.onDetach()
        }
    }

    LaunchedEffect(waypoints) {
        if (waypoints.isEmpty()) return@LaunchedEffect
        while (mapView.width <= 0 || mapView.height <= 0) {
            withFrameNanos { }
        }
        drawRoute(context, mapView, waypoints)
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
    )
}

private fun createMapView(context: Context): MapView {
    return MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setBuiltInZoomControls(false)
        setMultiTouchControls(true)
    }
}

private fun drawRoute(context: Context, mapView: MapView, waypoints: List<RouteWaypoint>) {
    mapView.overlays.clear()

    val geoPoints = waypoints.map { GeoPoint(it.latitude, it.longitude) }

    if (geoPoints.isNotEmpty()) {
        val polyline = Polyline().apply {
            setPoints(geoPoints)
            outlinePaint.apply {
                color = Color.rgb(33, 150, 243)
                strokeWidth = 6f
                isAntiAlias = true
            }
        }
        mapView.overlays.add(polyline)

        waypoints.forEachIndexed { index, wp ->
            val icon = createNumberedMarkerDrawable(context, index + 1)
            val marker = Marker(mapView).apply {
                position = GeoPoint(wp.latitude, wp.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "${index + 1}: ${wp.name}"
                snippet = "%.5f, %.5f".format(wp.latitude, wp.longitude)
                setIcon(icon)
                setInfoWindow(null)
            }
            mapView.overlays.add(marker)
        }

        if (geoPoints.size >= 2) {
            val box = BoundingBox.fromGeoPoints(geoPoints)
            mapView.post { mapView.zoomToBoundingBox(box.increaseByScale(1.2f), false, 0) }
        } else {
            mapView.controller.setZoom(15.0)
            mapView.controller.setCenter(geoPoints[0])
        }
    }

    mapView.invalidate()
}
