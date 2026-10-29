package com.noobexon.xposedfakelocation.manager.ui.map.maplibre

import android.content.Context
import android.graphics.RectF
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.manager.route.RoadFailure
import com.noobexon.xposedfakelocation.manager.ui.map.DrawnSegment
import com.noobexon.xposedfakelocation.manager.ui.map.PointRole
import com.noobexon.xposedfakelocation.manager.ui.map.RouteDrawing
import com.noobexon.xposedfakelocation.manager.ui.map.SegmentMarker
import com.noobexon.xposedfakelocation.manager.ui.map.route.RouteMarkerIcons
import com.noobexon.xposedfakelocation.manager.ui.map.routeDrawing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.OnSymbolClickListener
import org.maplibre.android.plugins.annotation.OnSymbolDragListener
import org.maplibre.android.plugins.annotation.Symbol
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.switchCase
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.osmdroid.util.GeoPoint

private const val ROUTE_SOURCE = "xfl-route-lines"
private const val ROUTE_SOLID_LAYER = "xfl-route-solid"
private const val ROUTE_DASHED_LAYER = "xfl-route-dashed"
private const val PROP_FROM = "fromId"
private const val PROP_TO = "toId"
private const val PROP_ID = "id"
private const val PROP_LOADING = "loading"
private const val PROP_DASHED = "dashed"
private const val ARROW_IMAGE = "xfl-route-arrow"
private const val WARNING_IMAGE = "xfl-route-warning"
private const val PIN_IMAGE = "xfl-spoof-pin"
private const val LINE_WIDTH_DP = 4f
private const val LOADING_OPACITY = 0.4f
private const val LINE_TAP_RADIUS_DP = 12f
// Lets the initial camera settle first, so this fit isn't overwritten.
private const val FIT_DELAY_MS = 400L
private val FIT_PADDING = 48.dp

/** Route colors from the app theme. */
internal data class RouteColors(val line: Int, val point: Int, val start: Int, val end: Int, val text: Int, val warning: Int)

/**
 * The map's own layers, bottom to top: route lines, segment arrows, route points and warnings,
 * the spoof marker (the blue dot is added later and sits on top). Created once per map: the
 * symbol managers re-add themselves on every style load, and [restyle] re-adds the route lines
 * below them.
 */
internal class MapLibreLayers(mapView: MapView, map: MapLibreMap, style: Style) {
    init {
        addRouteLines(style, belowLayerId = null)
    }

    val arrows = SymbolManager(mapView, map, style).apply {
        setIconAllowOverlap(true)
        setIconIgnorePlacement(true)
        setIconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP)
    }
    val points = SymbolManager(mapView, map, style).apply {
        setIconAllowOverlap(true)
        setIconIgnorePlacement(true)
    }
    val marker = SymbolManager(mapView, map, style).apply {
        setIconAllowOverlap(true)
        setIconIgnorePlacement(true)
    }

    /** Re-adds the route lines after a style change. */
    fun restyle(style: Style) = addRouteLines(style, belowLayerId = arrows.layerId)

    fun destroy() {
        arrows.onDestroy()
        points.onDestroy()
        marker.onDestroy()
    }
}

private fun addRouteLines(style: Style, belowLayerId: String?) {
    if (style.getLayer(ROUTE_SOLID_LAYER) != null) return
    style.addSource(GeoJsonSource(ROUTE_SOURCE))
    val opacity = PropertyFactory.lineOpacity(switchCase(get(PROP_LOADING), literal(LOADING_OPACITY), literal(1f)))
    val solid = LineLayer(ROUTE_SOLID_LAYER, ROUTE_SOURCE)
        .withFilter(eq(get(PROP_DASHED), false))
        .withProperties(
            PropertyFactory.lineWidth(LINE_WIDTH_DP),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            opacity,
        )
    val dashed = LineLayer(ROUTE_DASHED_LAYER, ROUTE_SOURCE)
        .withFilter(eq(get(PROP_DASHED), true))
        .withProperties(
            PropertyFactory.lineWidth(LINE_WIDTH_DP),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineDasharray(arrayOf(3f, 2f)),
            opacity,
        )
    if (belowLayerId != null && style.getLayer(belowLayerId) != null) {
        style.addLayerBelow(solid, belowLayerId)
        style.addLayerBelow(dashed, belowLayerId)
    } else {
        style.addLayer(solid)
        style.addLayer(dashed)
    }
}

/** Creates the layers when the first style loads and re-adds the route lines on later loads. */
@Composable
internal fun rememberMapLibreLayers(mapView: MapView, map: MapLibreMap?, style: Style?): MapLibreLayers? {
    var layers by remember(map) { mutableStateOf<MapLibreLayers?>(null) }
    LaunchedEffect(map, style) {
        if (map == null || style == null) return@LaunchedEffect
        val current = layers
        if (current == null) layers = MapLibreLayers(mapView, map, style) else current.restyle(style)
    }
    DisposableEffect(map) {
        onDispose { layers?.destroy() }
    }
    return layers
}

/** Draws [route] (or nothing for `null`) whenever it, the failures, [editable] or the theme change. */
@Composable
internal fun MapLibreRouteEffect(
    layers: MapLibreLayers?,
    style: Style?,
    route: Route?,
    editable: Boolean,
    roadFailures: Map<String, RoadFailure>,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val colors = RouteColors(
        line = scheme.primary.toArgb(),
        point = scheme.primary.toArgb(),
        start = scheme.tertiary.toArgb(),
        end = scheme.error.toArgb(),
        text = scheme.onPrimary.toArgb(),
        warning = scheme.error.toArgb(),
    )
    val drawing = remember(route, roadFailures) {
        route?.takeIf { it.points.isNotEmpty() }?.let { routeDrawing(it, roadFailures) }
    }
    LaunchedEffect(layers, style, drawing, editable, colors) {
        if (layers == null || style == null || !style.isFullyLoaded) return@LaunchedEffect
        layers.showRoute(context, style, drawing, editable, colors)
    }
}

private fun MapLibreLayers.showRoute(context: Context, style: Style, drawing: RouteDrawing?, editable: Boolean, colors: RouteColors) {
    val features = drawing?.segments.orEmpty().map { segment ->
        Feature.fromGeometry(LineString.fromLngLats(segment.path.map { Point.fromLngLat(it.second, it.first) })).apply {
            addStringProperty(PROP_FROM, segment.fromId)
            addStringProperty(PROP_TO, segment.toId)
            addBooleanProperty(PROP_LOADING, segment.loading)
            addBooleanProperty(PROP_DASHED, segment.dashed)
        }
    }
    style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(features))
    listOf(ROUTE_SOLID_LAYER, ROUTE_DASHED_LAYER).forEach { id ->
        style.getLayer(id)?.setProperties(PropertyFactory.lineColor(colors.line))
    }
    style.addImage(ARROW_IMAGE, RouteMarkerIcons.arrow(context, 0f, colors.line))
    style.addImage(WARNING_IMAGE, RouteMarkerIcons.warning(context, colors.warning))
    arrows.deleteAll()
    points.deleteAll()
    if (drawing == null) return
    arrows.create(
        drawing.segments.mapNotNull { segment ->
            (segment.marker as? SegmentMarker.Arrow)?.let { arrow ->
                SymbolOptions()
                    .withLatLng(LatLng(arrow.latitude, arrow.longitude))
                    .withIconImage(ARROW_IMAGE)
                    .withIconRotate(arrow.bearingDegrees.toFloat())
                    .withData(segmentData(segment))
            }
        }
    )
    val warnings = drawing.segments.mapNotNull { segment ->
        (segment.marker as? SegmentMarker.Warning)?.let { warning ->
            SymbolOptions()
                .withLatLng(LatLng(warning.latitude, warning.longitude))
                .withIconImage(WARNING_IMAGE)
                .withData(segmentData(segment))
        }
    }
    val numbered = drawing.points.map { point ->
        val fill = when (point.role) {
            PointRole.START -> colors.start
            PointRole.END -> colors.end
            PointRole.NORMAL -> colors.point
        }
        val image = "xfl-route-point-${point.number}-$fill-${colors.text}"
        style.addImage(image, RouteMarkerIcons.numbered(context, point.number, fill, colors.text))
        SymbolOptions()
            .withLatLng(LatLng(point.latitude, point.longitude))
            .withIconImage(image)
            .withDraggable(editable)
            .withData(JsonObject().apply { addProperty(PROP_ID, point.id) })
    }
    points.create(warnings + numbered)
}

private fun segmentData(segment: DrawnSegment) = JsonObject().apply {
    addProperty(PROP_FROM, segment.fromId)
    addProperty(PROP_TO, segment.toId)
}

private fun Symbol.stringData(key: String): String? =
    (data as? JsonObject)?.get(key)?.takeIf { it.isJsonPrimitive }?.asString

private fun Symbol.segmentIds(): Pair<String, String>? {
    val from = stringData(PROP_FROM) ?: return null
    val to = stringData(PROP_TO) ?: return null
    return from to to
}

/** Shows the spoof marker at [location] (none for `null`) and moves the camera to it, like osmdroid. */
@Composable
internal fun MapLibreSpoofMarker(map: MapLibreMap?, layers: MapLibreLayers?, style: Style?, location: GeoPoint?) {
    val context = LocalContext.current
    val color = MaterialTheme.colorScheme.primary.toArgb()
    LaunchedEffect(layers, style, location, color) {
        if (layers == null || style == null || !style.isFullyLoaded) return@LaunchedEffect
        style.addImage(PIN_IMAGE, RouteMarkerIcons.pin(context, color))
        layers.marker.deleteAll()
        if (location != null) {
            layers.marker.create(
                SymbolOptions()
                    .withLatLng(location.toLatLng())
                    .withIconImage(PIN_IMAGE)
                    .withIconAnchor(Property.ICON_ANCHOR_BOTTOM)
            )
        }
    }
    LaunchedEffect(map, location) {
        if (map != null && location != null) map.animateTo(location)
    }
}

/**
 * Taps: route symbols go to their own click listeners (points and ⚠ open their sheets, arrows the
 * segment). Otherwise, in Route mode a tap within [LINE_TAP_RADIUS_DP] of a route line opens that
 * segment and any other tap adds a point; in Location mode a tap moves the marker. Map taps are
 * ignored while spoofing. Points are dragged only while [editable].
 */
@Composable
internal fun MapLibreTapHandler(
    map: MapLibreMap,
    layers: MapLibreLayers?,
    isPlaying: Boolean,
    routeMode: Boolean,
    editable: Boolean,
    onClickedLocationChange: (GeoPoint?) -> Unit,
    onRouteTap: (GeoPoint) -> Unit,
    onPointTap: (String) -> Unit,
    onPointMoved: (String, GeoPoint) -> Unit,
    onSegmentTap: (String, String) -> Unit,
) {
    val density = LocalDensity.current.density
    val currentIsPlaying by rememberUpdatedState(isPlaying)
    val currentRouteMode by rememberUpdatedState(routeMode)
    val currentEditable by rememberUpdatedState(editable)
    val currentOnClickedLocationChange by rememberUpdatedState(onClickedLocationChange)
    val currentOnRouteTap by rememberUpdatedState(onRouteTap)
    val currentOnPointTap by rememberUpdatedState(onPointTap)
    val currentOnPointMoved by rememberUpdatedState(onPointMoved)
    val currentOnSegmentTap by rememberUpdatedState(onSegmentTap)

    DisposableEffect(layers) {
        if (layers == null) return@DisposableEffect onDispose {}
        val pointClick = OnSymbolClickListener { symbol ->
            if (currentEditable) {
                val id = symbol.stringData(PROP_ID)
                if (id != null) {
                    currentOnPointTap(id)
                } else {
                    symbol.segmentIds()?.let { (from, to) -> currentOnSegmentTap(from, to) }
                }
            }
            true
        }
        val arrowClick = OnSymbolClickListener { symbol ->
            if (currentEditable) symbol.segmentIds()?.let { (from, to) -> currentOnSegmentTap(from, to) }
            true
        }
        val drag = object : OnSymbolDragListener {
            override fun onAnnotationDragStarted(annotation: Symbol) = Unit
            override fun onAnnotationDrag(annotation: Symbol) = Unit
            override fun onAnnotationDragFinished(annotation: Symbol) {
                val id = annotation.stringData(PROP_ID) ?: return
                currentOnPointMoved(id, GeoPoint(annotation.latLng.latitude, annotation.latLng.longitude))
            }
        }
        layers.points.addClickListener(pointClick)
        layers.arrows.addClickListener(arrowClick)
        layers.points.addDragListener(drag)
        onDispose {
            layers.points.removeClickListener(pointClick)
            layers.arrows.removeClickListener(arrowClick)
            layers.points.removeDragListener(drag)
        }
    }

    DisposableEffect(map, layers) {
        val listener = MapLibreMap.OnMapClickListener { latLng ->
            val screen = map.projection.toScreenLocation(latLng)
            if (layers != null && map.queryRenderedFeatures(screen, layers.arrows.layerId, layers.points.layerId).isNotEmpty()) {
                return@OnMapClickListener false
            }
            if (currentIsPlaying) return@OnMapClickListener true
            val point = GeoPoint(latLng.latitude, latLng.longitude)
            if (!currentRouteMode) {
                currentOnClickedLocationChange(point)
                return@OnMapClickListener true
            }
            val radius = LINE_TAP_RADIUS_DP * density
            val box = RectF(screen.x - radius, screen.y - radius, screen.x + radius, screen.y + radius)
            val line = map.queryRenderedFeatures(box, ROUTE_SOLID_LAYER, ROUTE_DASHED_LAYER).firstOrNull()
            if (line == null) {
                currentOnRouteTap(point)
            } else {
                val from = line.getStringProperty(PROP_FROM)
                val to = line.getStringProperty(PROP_TO)
                if (currentEditable && from != null && to != null) currentOnSegmentTap(from, to)
            }
            true
        }
        map.addOnMapClickListener(listener)
        onDispose { map.removeOnMapClickListener(listener) }
    }
}

/** Frames all route points (48 dp padding) when [fitRouteEvent] fires. */
@Composable
internal fun HandleMapLibreFitRoute(map: MapLibreMap, route: Route?, fitRouteEvent: Flow<Unit>) {
    val currentRoute by rememberUpdatedState(route)
    val padding = with(LocalDensity.current) { FIT_PADDING.roundToPx() }
    LaunchedEffect(map, fitRouteEvent) {
        fitRouteEvent.collect {
            delay(FIT_DELAY_MS)
            val points = currentRoute?.points.orEmpty().map { LatLng(it.latitude, it.longitude) }.distinct()
            when {
                points.size == 1 -> map.animateCamera(CameraUpdateFactory.newLatLng(points.first()))
                points.size > 1 -> map.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(points).build(), padding)
                )
            }
        }
    }
}
