package com.noobexon.xposedfakelocation.manager.ui.map

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.OFFLINE_MAX_TILES
import com.noobexon.xposedfakelocation.data.model.MapMode
import com.noobexon.xposedfakelocation.data.model.MapRenderer
import com.noobexon.xposedfakelocation.manager.geo.LatLon
import com.noobexon.xposedfakelocation.manager.geo.Place
import com.noobexon.xposedfakelocation.manager.joystick.JoystickButton
import com.noobexon.xposedfakelocation.manager.ui.map.maplibre.OfflineAreaNameDialog
import com.noobexon.xposedfakelocation.manager.ui.map.maplibre.OfflineAreasEvent
import com.noobexon.xposedfakelocation.manager.ui.map.maplibre.OfflineAreasViewModel
import com.noobexon.xposedfakelocation.manager.ui.map.maplibre.OfflineDownloadCard
import com.noobexon.xposedfakelocation.manager.ui.map.maplibre.VisibleArea
import com.noobexon.xposedfakelocation.manager.ui.map.maplibre.estimateTileCount
import com.noobexon.xposedfakelocation.manager.ui.map.maplibre.offlineStyles
import com.noobexon.xposedfakelocation.manager.ui.map.maplibre.offlineZoomRange
import com.noobexon.xposedfakelocation.manager.ui.map.route.MapModeSwitch
import com.noobexon.xposedfakelocation.manager.ui.map.route.RouteEditorViewModel
import com.noobexon.xposedfakelocation.manager.ui.map.route.RouteLoadConfirmation
import com.noobexon.xposedfakelocation.manager.ui.map.route.RouteModeUi
import com.noobexon.xposedfakelocation.manager.ui.map.search.AddressSearchField
import com.noobexon.xposedfakelocation.manager.ui.map.search.AddressSearchResults
import com.noobexon.xposedfakelocation.manager.ui.map.search.AddressSearchViewModel
import com.noobexon.xposedfakelocation.manager.ui.map.search.SearchPickAction
import com.noobexon.xposedfakelocation.manager.ui.map.search.searchPickAction
import com.noobexon.xposedfakelocation.manager.ui.navigation.Screen
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.util.Locale

/**
 * Top-level Map screen composable.
 *
 * Acts as the wiring layer between [MapViewModel] and the rest of the Map UI. Responsibilities:
 * - Collects [MapViewModel.uiState] and distributes individual slices to child composables so that
 *   only the composables that actually care about a piece of state recompose when it changes.
 * - Hosts the [ModalNavigationDrawer] and manages its [drawerState], including intercepting the
 *   system back gesture when the drawer is open, closing on scrim tap (via `gesturesEnabled =
 *   drawerState.isOpen`), and re-opening it on re-entry via [MapViewModel.consumeReopenDrawerRequest].
 * - Renders the [Scaffold] with [TopAppBar] (menu, center, overflow actions) and FAB
 *   (play/stop spoofing toggle). The FAB is visually disabled when no marker has been placed.
 * - Conditionally overlays [GoToPointDialog] and [AddToFavoritesDialog] based on the corresponding
 *   `isVisible` flags in [MapUiState]; each dialog receives only the state slice it needs plus
 *   typed callbacks, keeping dialogs fully stateless.
 * - Delegates all map-view rendering and side effects to [MapViewContainer].
 *
 * @param navController Used by [DrawerContent] for in-app navigation.
 * @param mapViewModel The screen's ViewModel; injected at the [NavHost] level.
 * @param routeEditorViewModel Route mode state; shared with Favorites at the [NavHost] level.
 * @param addressSearchViewModel Address search state; turns the app bar into a search field.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    navController: NavController,
    mapViewModel: MapViewModel,
    routeEditorViewModel: RouteEditorViewModel,
    addressSearchViewModel: AddressSearchViewModel,
) {
    val context = LocalContext.current
    val uiState by mapViewModel.uiState.collectAsStateWithLifecycle()
    val isPlaying = uiState.isPlaying
    val isFabClickable = uiState.isFabClickable
    val showGoToPointDialog = uiState.isGoToPointDialogVisible
    val showAddToFavoritesDialog = uiState.isAddToFavoritesDialogVisible
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showOptionsMenu by remember { mutableStateOf(false) }
    val fakeLocationSet = stringResource(R.string.toast_fake_location_set)
    val fakeLocationUnset = stringResource(R.string.toast_unset_fake_location)
    val mapMode by routeEditorViewModel.mapMode.collectAsStateWithLifecycle()
    val draft by routeEditorViewModel.draft.collectAsStateWithLifecycle()
    val routePlaying by routeEditorViewModel.isRoutePlaying.collectAsStateWithLifecycle()
    val joystickRunning by routeEditorViewModel.joystickRunning.collectAsStateWithLifecycle()
    val roadFailures by routeEditorViewModel.roadFailures.collectAsStateWithLifecycle()
    val routeMode = mapMode == MapMode.ROUTE
    val search by addressSearchViewModel.ui.collectAsStateWithLifecycle()
    val mapRenderer by mapViewModel.mapRenderer.collectAsStateWithLifecycle()
    val mapStyleUrl by mapViewModel.mapStyleUrl.collectAsStateWithLifecycle()
    val darkMap = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val offlineViewModel: OfflineAreasViewModel = viewModel()
    val offline by offlineViewModel.state.collectAsStateWithLifecycle()
    val mapLibre = mapRenderer == MapRenderer.MAPLIBRE
    var visibleArea by remember { mutableStateOf<VisibleArea?>(null) }
    var pendingDownload by remember { mutableStateOf<VisibleArea?>(null) }
    val pixelRatio = LocalDensity.current.density
    val areaTooLarge = stringResource(R.string.offline_area_too_large)
    val keyboardController = LocalSoftwareKeyboardController.current
    val onPlacePicked: (Place) -> Unit = { place ->
        keyboardController?.hide()
        addressSearchViewModel.close()
        val point = GeoPoint(place.latitude, place.longitude)
        when (searchPickAction(place, routeMode, spoofingActive = isPlaying, routePlaying = routePlaying)) {
            is SearchPickAction.PlaceMarker -> mapViewModel.goToPoint(point)
            is SearchPickAction.AddRoutePoint -> {
                routeEditorViewModel.addPoint(point, label = place.title)
                mapViewModel.moveCamera(point)
            }
            is SearchPickAction.MoveCamera -> mapViewModel.moveCamera(point)
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    BackHandler(enabled = search.isOpen) {
        addressSearchViewModel.close()
    }

    // When returning to the map after navigating away via the drawer, restore the drawer open.
    LaunchedEffect(Unit) {
        if (mapViewModel.consumeReopenDrawerRequest()) {
            drawerState.open()
        }
    }

    // Navigate to Favorites after a favorite is successfully saved.
    LaunchedEffect(mapViewModel.navigateToFavoritesEvent) {
        mapViewModel.navigateToFavoritesEvent.collect {
            navController.navigate(Screen.Favorites.route) { launchSingleTop = true }
        }
    }

    LaunchedEffect(mapLibre) {
        if (mapLibre) {
            offlineViewModel.refresh()
        } else {
            visibleArea = null
        }
    }

    LaunchedEffect(offlineViewModel) {
        offlineViewModel.events.collect { event ->
            val message = when (event) {
                is OfflineAreasEvent.Finished -> context.getString(R.string.offline_download_done, event.name)
                is OfflineAreasEvent.Failed -> context.getString(R.string.offline_download_failed, event.message)
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    ModalNavigationDrawer(
        drawerContent = {
            DrawerContent(
                onCloseDrawer = { scope.launch { drawerState.close() } },
                onNavigate = { mapViewModel.requestReopenDrawer() },
                navController = navController
            )
        },
        scrimColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
        drawerState = drawerState,
        // Only enable gestures while the drawer is open so that tapping the scrim (outside the
        // drawer) closes it. When closed, gestures are off so map pan/zoom is not intercepted.
        gesturesEnabled = drawerState.isOpen,
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        if (search.isOpen) {
                            AddressSearchField(
                                query = search.query,
                                onQueryChange = addressSearchViewModel::onQueryChange,
                                onSearch = addressSearchViewModel::searchNow,
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    navigationIcon = {
                        if (search.isOpen) {
                            IconButton(onClick = addressSearchViewModel::close) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_collapse_search))
                            }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(imageVector = Icons.Default.Menu, contentDescription = stringResource(R.string.cd_menu))
                            }
                        }
                    },
                    actions = {
                        if (search.isOpen) {
                            IconButton(onClick = {
                                if (search.query.isEmpty()) addressSearchViewModel.close() else addressSearchViewModel.onQueryChange("")
                            }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_clear_search))
                            }
                        } else {
                            IconButton(onClick = addressSearchViewModel::open) {
                                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search_address))
                            }
                            JoystickButton(enabled = isPlaying && uiState.lastClickedLocation != null && !routePlaying)
                            IconButton(onClick = { mapViewModel.triggerCenterMapEvent() }) {
                                Icon(imageVector = Icons.Default.MyLocation, contentDescription = stringResource(R.string.cd_center))
                            }
                            IconButton(onClick = { showOptionsMenu = true }) {
                                Icon(imageVector = Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_options))
                            }
                            DropdownMenu(
                                expanded = showOptionsMenu,
                                onDismissRequest = { showOptionsMenu = false }
                            ) {
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.LocationSearching,
                                            contentDescription = stringResource(R.string.map_go_to_point)
                                        )
                                    },
                                    text = { Text(stringResource(R.string.map_go_to_point)) },
                                    onClick = {
                                        showOptionsMenu = false
                                        mapViewModel.showGoToPointDialog()
                                    }
                                )
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.FavoriteBorder,
                                            contentDescription = stringResource(R.string.map_add_to_favorites)
                                        )
                                    },
                                    text = { Text(stringResource(R.string.map_add_to_favorites)) },
                                    onClick = {
                                        showOptionsMenu = false
                                        mapViewModel.showAddToFavoritesDialog()
                                    }
                                )
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = stringResource(R.string.map_clear_location)
                                        )
                                    },
                                    text = { Text(stringResource(R.string.map_clear_location)) },
                                    onClick = {
                                        showOptionsMenu = false
                                        mapViewModel.updateClickedLocation(null)
                                    },
                                    enabled = isFabClickable
                                )
                                if (mapLibre) {
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = stringResource(R.string.map_download_area)
                                            )
                                        },
                                        text = { Text(stringResource(R.string.map_download_area)) },
                                        onClick = {
                                            showOptionsMenu = false
                                            visibleArea?.let { area ->
                                                val zoom = offlineZoomRange(area.zoom)
                                                if (estimateTileCount(area.bounds, zoom.first, zoom.last) > OFFLINE_MAX_TILES) {
                                                    Toast.makeText(context, areaTooLarge, Toast.LENGTH_LONG).show()
                                                } else {
                                                    pendingDownload = area
                                                }
                                            }
                                        },
                                        enabled = visibleArea != null && offline.download == null
                                    )
                                }
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                // In Route mode the play button lives in the route panel so it can't cover the panel.
                if (!routeMode) {
                    FloatingActionButton(
                        onClick = {
                            if (isFabClickable) {
                                val wasPlaying = uiState.isPlaying
                                mapViewModel.togglePlaying()
                                Toast.makeText(
                                    context,
                                    if (!wasPlaying) fakeLocationSet else fakeLocationUnset,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(16.dp),
                        containerColor = if (isFabClickable) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        },
                        contentColor = if (isFabClickable) {
                            contentColorFor(MaterialTheme.colorScheme.primary)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = if (isFabClickable) 6.dp else 0.dp,
                            pressedElevation = if (isFabClickable) 12.dp else 0.dp
                        )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) {
                                stringResource(R.string.cd_stop)
                            } else {
                                stringResource(R.string.cd_play)
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                MapViewContainer(
                    renderer = mapRenderer,
                    styleUrl = mapStyleUrl,
                    darkTheme = darkMap,
                    isLoading = uiState.isLoading,
                    lastClickedLocation = uiState.lastClickedLocation,
                    userLocation = uiState.userLocation,
                    isPlaying = uiState.isPlaying,
                    mapZoom = uiState.mapZoom,
                    hasResolvedInitialLocation = uiState.hasResolvedInitialLocation,
                    goToPointEvent = mapViewModel.goToPointEvent,
                    centerMapEvent = mapViewModel.centerMapEvent,
                    onClickedLocationChange = mapViewModel::updateClickedLocation,
                    onUserLocationChange = mapViewModel::updateUserLocation,
                    onMapZoomChange = mapViewModel::updateMapZoom,
                    onLoadingFinished = mapViewModel::setLoadingFinished,
                    onInitialLocationResolved = mapViewModel::markInitialLocationResolved,
                    routeMode = routeMode,
                    route = draft.route,
                    routeEditable = !isPlaying && !routePlaying,
                    roadFailures = roadFailures,
                    fitRouteEvent = routeEditorViewModel.fitRouteEvent,
                    onRouteTap = { routeEditorViewModel.addPoint(it) },
                    onRoutePointTap = routeEditorViewModel::showPointActions,
                    onRoutePointMoved = routeEditorViewModel::movePoint,
                    onRouteSegmentTap = { a, b -> routeEditorViewModel.showSegment(a to b) },
                    cameraMoveEvent = mapViewModel.cameraMoveEvent,
                    onMapCenterChange = { addressSearchViewModel.setBias(LatLon(it.latitude, it.longitude)) },
                    onUseOsmdroid = mapViewModel::useOsmdroid,
                    onVisibleAreaChange = { visibleArea = it },
                )
                MapModeSwitch(
                    mode = mapMode,
                    enabled = !isPlaying && !routePlaying && !joystickRunning,
                    onModeChange = routeEditorViewModel::setMapMode,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                )
                if (routeMode) {
                    RouteModeUi(
                        viewModel = routeEditorViewModel,
                        isPlaying = isPlaying,
                        onStopSpoofing = mapViewModel::togglePlaying,
                        onLoadFromFavorites = {
                            routeEditorViewModel.requestOpenRoutesTab()
                            navController.navigate(Screen.Favorites.route) { launchSingleTop = true }
                        },
                    )
                }
                if (search.isOpen) {
                    AddressSearchResults(
                        results = search.results,
                        onPick = onPlacePicked,
                        onRetry = addressSearchViewModel::retry,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                    )
                }
                val download = offline.download
                if (mapLibre && download != null && !search.isOpen) {
                    OfflineDownloadCard(
                        download = download,
                        onCancel = offlineViewModel::cancelDownload,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 64.dp, start = 16.dp, end = 16.dp),
                    )
                }
            }
        }

        if (showGoToPointDialog) {
            val goToPoint = uiState.goToPointState
            GoToPointDialog(
                latitude = goToPoint.latitude.value,
                longitude = goToPoint.longitude.value,
                latitudeErrorRes = goToPoint.latitude.errorMessageRes,
                longitudeErrorRes = goToPoint.longitude.errorMessageRes,
                onLatitudeChange = mapViewModel::onGoToPointLatitudeChange,
                onLongitudeChange = mapViewModel::onGoToPointLongitudeChange,
                onConfirm = mapViewModel::confirmGoToPoint,
                onDismissRequest = mapViewModel::hideGoToPointDialog,
            )
        }

        if (showAddToFavoritesDialog) {
            val favorite = uiState.addToFavoritesState
            AddToFavoritesDialog(
                name = favorite.name.value,
                description = favorite.description.value,
                latitude = favorite.latitude.value,
                longitude = favorite.longitude.value,
                nameErrorRes = favorite.name.errorMessageRes,
                latitudeErrorRes = favorite.latitude.errorMessageRes,
                longitudeErrorRes = favorite.longitude.errorMessageRes,
                onNameChange = mapViewModel::onFavoriteNameChange,
                onDescriptionChange = mapViewModel::onFavoriteDescriptionChange,
                onLatitudeChange = mapViewModel::onFavoriteLatitudeChange,
                onLongitudeChange = mapViewModel::onFavoriteLongitudeChange,
                onConfirm = mapViewModel::confirmAddFavorite,
                onDismissRequest = mapViewModel::hideAddToFavoritesDialog,
            )
        }

        pendingDownload?.let { area ->
            OfflineAreaNameDialog(
                initialName = String.format(Locale.US, "%.4f, %.4f", area.centerLatitude, area.centerLongitude),
                onConfirm = { name ->
                    offlineViewModel.startDownload(name, area, offlineStyles(mapStyleUrl), pixelRatio)
                    pendingDownload = null
                },
                onDismiss = { pendingDownload = null },
            )
        }

        RouteLoadConfirmation(routeEditorViewModel)
    }
}
