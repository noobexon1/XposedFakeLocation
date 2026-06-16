package com.noobexon.xposedfakelocation.manager.ui.routes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.model.RouteWaypoint

/**
 * Stateful route detail screen.
 *
 * Loads a route by name from navigation and displays its waypoints.
 * Provides controls for playing the route and managing waypoints.
 *
 * @param navController For back navigation.
 * @param routeName The name of the route to display from navigation.
 * @param routeDetailViewModel Injected by [viewModel].
 */
@Composable
fun RouteDetailScreen(
    navController: NavController,
    routeName: String,
    routeDetailViewModel: RouteDetailViewModel = viewModel(),
) {
    val uiState by routeDetailViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(routeName) {
        routeDetailViewModel.loadRoute(routeName)
    }

    RouteDetailContent(
        uiState = uiState,
        onNavigateUp = { navController.navigateUp() },
        onStartPlaying = routeDetailViewModel::startPlaying,
        onStopPlaying = routeDetailViewModel::stopPlaying,
        onPlaybackSpeedChange = routeDetailViewModel::updatePlaybackSpeed,
        onLoopingChange = routeDetailViewModel::updateLooping,
        onAddWaypoint = { /* TODO: Dialog vom MapScreen aus */ },
        onRemoveWaypoint = routeDetailViewModel::removeWaypoint,
        onMoveWaypointUp = { index ->
            if (index > 0) routeDetailViewModel.reorderWaypoint(index, index - 1)
        },
        onMoveWaypointDown = { index ->
            if (index < uiState.waypoints.size - 1) routeDetailViewModel.reorderWaypoint(index, index + 1)
        },
    )
}

/**
 * Stateless layout for the route detail screen.
 *
 * @param uiState The current UI state of the route.
 * @param onNavigateUp Back navigation.
 * @param onStartPlaying Starts route playback.
 * @param onStopPlaying Stops route playback.
 * @param onPlaybackSpeedChange Changes playback speed.
 * @param onLoopingChange Toggles loop mode.
 * @param onAddWaypoint Adds a new waypoint.
 * @param onRemoveWaypoint Removes a waypoint.
 * @param onMoveWaypointUp Moves waypoint up.
 * @param onMoveWaypointDown Moves waypoint down.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteDetailContent(
    uiState: RouteDetailUiState,
    onNavigateUp: () -> Unit,
    onStartPlaying: () -> Unit,
    onStopPlaying: () -> Unit,
    onPlaybackSpeedChange: (Double) -> Unit,
    onLoopingChange: (Boolean) -> Unit,
    onAddWaypoint: () -> Unit,
    onRemoveWaypoint: (RouteWaypoint) -> Unit,
    onMoveWaypointUp: (Int) -> Unit,
    onMoveWaypointDown: (Int) -> Unit,
) {
    var deleteWaypointPending by remember { mutableStateOf<RouteWaypoint?>(null) }

    // Delete confirmation for waypoints
    deleteWaypointPending?.let { waypoint ->
        AlertDialog(
            onDismissRequest = { deleteWaypointPending = null },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = { Text(stringResource(R.string.route_confirm_delete_waypoint)) },
            text = { Text(waypoint.name) },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveWaypoint(waypoint)
                    deleteWaypointPending = null
                }) {
                    Text(
                        text = stringResource(R.string.favorites_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteWaypointPending = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.route_detail_title, uiState.route.name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
        ) {
            // Steuerungsbereich
            ControlSection(
                isPlaying = uiState.isPlaying,
                playbackSpeed = uiState.playbackSpeed,
                isLooping = uiState.isLooping,
                onStartPlaying = onStartPlaying,
                onStopPlaying = onStopPlaying,
                onPlaybackSpeedChange = onPlaybackSpeedChange,
                onLoopingChange = onLoopingChange,
            )

            Spacer(Modifier.height(16.dp))

            // Wegpunkt-Liste
            if (uiState.waypoints.isEmpty()) {
                Text(
                    text = stringResource(R.string.route_no_waypoints),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    itemsIndexed(
                        items = uiState.waypoints,
                        key = { index, _ -> index },
                    ) { index, waypoint ->
                        WaypointItem(
                            index = index,
                            waypoint = waypoint,
                            onDeleteClick = { deleteWaypointPending = waypoint },
                            onMoveUp = { onMoveWaypointUp(index) },
                            onMoveDown = { onMoveWaypointDown(index) },
                            canMoveUp = index > 0,
                            canMoveDown = index < uiState.waypoints.size - 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Control section with play/stop, speed slider and loop toggle.
 */
@Composable
private fun ControlSection(
    isPlaying: Boolean,
    playbackSpeed: Double,
    isLooping: Boolean,
    onStartPlaying: () -> Unit,
    onStopPlaying: () -> Unit,
    onPlaybackSpeedChange: (Double) -> Unit,
    onLoopingChange: (Boolean) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Play / Stop Button
            Button(
                onClick = if (isPlaying) onStopPlaying else onStartPlaying,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPlaying) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                ),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        if (isPlaying) R.string.route_stop else R.string.route_play
                    ),
                )
            }

            // Geschwindigkeit
            Text(
                text = stringResource(R.string.route_speed, playbackSpeed.toInt()),
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = playbackSpeed.toFloat(),
                onValueChange = { onPlaybackSpeedChange(it.toDouble()) },
                valueRange = 1f..50f,
                steps = 48,
                modifier = Modifier.fillMaxWidth(),
            )

            // Schleifen-Modus
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.route_loop),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = isLooping,
                    onCheckedChange = onLoopingChange,
                )
            }
        }
    }
}

/**
 * A single waypoint in the list with options to reorder and delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WaypointItem(
    index: Int,
    waypoint: RouteWaypoint,
    onDeleteClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = waypoint.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "%.5f, %.5f".format(waypoint.latitude, waypoint.longitude),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Move up
            IconButton(
                onClick = onMoveUp,
                enabled = canMoveUp,
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = stringResource(R.string.cd_move_up),
                    tint = if (canMoveUp) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                )
            }
            // Move down
            IconButton(
                onClick = onMoveDown,
                enabled = canMoveDown,
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = stringResource(R.string.cd_move_down),
                    tint = if (canMoveDown) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                )
            }
            // Delete
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.cd_delete_named_item, waypoint.name),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
