package com.noobexon.xposedfakelocation.manager.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.model.MapRenderer
import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.manager.route.RoadFailure
import com.noobexon.xposedfakelocation.manager.ui.map.maplibre.MapLibreHost
import com.noobexon.xposedfakelocation.manager.ui.map.maplibre.VisibleArea
import com.noobexon.xposedfakelocation.manager.ui.map.osmdroid.OsmdroidMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.osmdroid.util.GeoPoint

/**
 * Shows the map with the chosen [renderer]. Both renderers take the same state and callbacks, so
 * the rest of the screen doesn't depend on which one is shown.
 *
 * The composable is intentionally stateless: it accepts individual primitive/lambda parameters
 * rather than the full [MapViewModel], so that only affected subtrees recompose when a single
 * field changes.
 *
 * @param renderer Which library draws the map.
 * @param styleUrl Custom MapLibre style URL; blank means OpenFreeMap. Ignored by osmdroid.
 * @param darkTheme Whether the app shows its dark theme; picks the OpenFreeMap style.
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
 * @param onClickedLocationChange Callback to update [MapViewModel] when the user taps the map or
 *   the "Go to point" event resolves.
 * @param onUserLocationChange Callback to update [MapViewModel] when a real device location is
 *   detected.
 * @param onMapZoomChange Callback to persist the zoom level; called on dispose with the final zoom.
 * @param onLoadingFinished Callback to clear the loading state once the camera is positioned.
 * @param onInitialLocationResolved Callback to set [MapUiState.hasResolvedInitialLocation].
 * @param routeMode When `true`, taps edit the route and the location marker is hidden unless
 *   spoofing is on (then it shows the spoofed, possibly moving, position).
 * @param route The route to draw in Route mode.
 * @param routeEditable Whether route markers can be tapped and dragged (false while playing).
 * @param roadFailures Road paths that failed to fetch; their segments show a warning badge.
 * @param fitRouteEvent One-shot request to frame the whole [route].
 * @param onRouteTap Called with the tapped map location in Route mode (adds a point).
 * @param onRoutePointTap Called with the id of a tapped route point.
 * @param onRoutePointMoved Called with a point's id and new location when a drag ends.
 * @param onRouteSegmentTap Called with the from/to point ids of a tapped segment.
 * @param cameraMoveEvent One-shot camera moves that leave the marker alone (e.g. a search pick
 *   while spoofing).
 * @param onMapCenterChange Called with the map center after panning or zooming settles.
 * @param onVisibleAreaChange MapLibre only: the visible area after the camera settles, for offline downloads.
 * @param onUseOsmdroid MapLibre only: switches the setting back to osmdroid from an error card.
 */
@Composable
fun MapViewContainer(
    renderer: MapRenderer,
    styleUrl: String,
    darkTheme: Boolean,
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
    routeMode: Boolean = false,
    route: Route? = null,
    routeEditable: Boolean = false,
    roadFailures: Map<String, RoadFailure> = emptyMap(),
    fitRouteEvent: Flow<Unit> = emptyFlow(),
    onRouteTap: (GeoPoint) -> Unit = {},
    onRoutePointTap: (String) -> Unit = {},
    onRoutePointMoved: (String, GeoPoint) -> Unit = { _, _ -> },
    onRouteSegmentTap: (String, String) -> Unit = { _, _ -> },
    cameraMoveEvent: Flow<GeoPoint> = emptyFlow(),
    onMapCenterChange: (GeoPoint) -> Unit = {},
    onVisibleAreaChange: (VisibleArea) -> Unit = {},
    onUseOsmdroid: () -> Unit = {},
) {
    when (renderer) {
        MapRenderer.OSMDROID -> OsmdroidMap(
            isLoading = isLoading,
            lastClickedLocation = lastClickedLocation,
            userLocation = userLocation,
            isPlaying = isPlaying,
            mapZoom = mapZoom,
            hasResolvedInitialLocation = hasResolvedInitialLocation,
            goToPointEvent = goToPointEvent,
            centerMapEvent = centerMapEvent,
            onClickedLocationChange = onClickedLocationChange,
            onUserLocationChange = onUserLocationChange,
            onMapZoomChange = onMapZoomChange,
            onLoadingFinished = onLoadingFinished,
            onInitialLocationResolved = onInitialLocationResolved,
            routeMode = routeMode,
            route = route,
            routeEditable = routeEditable,
            roadFailures = roadFailures,
            fitRouteEvent = fitRouteEvent,
            onRouteTap = onRouteTap,
            onRoutePointTap = onRoutePointTap,
            onRoutePointMoved = onRoutePointMoved,
            onRouteSegmentTap = onRouteSegmentTap,
            cameraMoveEvent = cameraMoveEvent,
            onMapCenterChange = onMapCenterChange,
        )
        MapRenderer.MAPLIBRE -> MapLibreHost(
            styleUrl = resolveStyleUrl(styleUrl, darkTheme),
            isLoading = isLoading,
            lastClickedLocation = lastClickedLocation,
            userLocation = userLocation,
            isPlaying = isPlaying,
            mapZoom = mapZoom,
            hasResolvedInitialLocation = hasResolvedInitialLocation,
            goToPointEvent = goToPointEvent,
            centerMapEvent = centerMapEvent,
            onClickedLocationChange = onClickedLocationChange,
            onUserLocationChange = onUserLocationChange,
            onMapZoomChange = onMapZoomChange,
            onLoadingFinished = onLoadingFinished,
            onInitialLocationResolved = onInitialLocationResolved,
            routeMode = routeMode,
            route = route,
            routeEditable = routeEditable,
            roadFailures = roadFailures,
            fitRouteEvent = fitRouteEvent,
            onRouteTap = onRouteTap,
            onRoutePointTap = onRoutePointTap,
            onRoutePointMoved = onRoutePointMoved,
            onRouteSegmentTap = onRouteSegmentTap,
            cameraMoveEvent = cameraMoveEvent,
            onMapCenterChange = onMapCenterChange,
            onVisibleAreaChange = onVisibleAreaChange,
            onUseOsmdroid = onUseOsmdroid,
        )
    }
}

/**
 * Shown instead of (osmdroid) or over (MapLibre) the map while [MapUiState.isLoading] is `true`.
 * Displays a centred [CircularProgressIndicator] with a descriptive label.
 */
@Composable
internal fun LoadingSpinner(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
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
