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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.manager.ui.navigation.Screen
import compose.icons.LineAwesomeIcons
import compose.icons.lineawesomeicons.RouteSolid
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Stateful route overview screen.
 *
 * Collects [RoutesViewModel.routes] state and forwards delete and
 * navigation events to the appropriate callbacks.
 *
 * @param navController For navigation to the detail screen and back.
 * @param routesViewModel Injected by [viewModel]; can be overridden in tests.
 */
@Composable
fun RoutesScreen(
    navController: NavController,
    routesViewModel: RoutesViewModel = viewModel(),
) {
    val routes by routesViewModel.routes.collectAsStateWithLifecycle()

    RoutesContent(
        routes = routes,
        onRouteClick = { route ->
            navController.navigate("route_detail/${route.name}") { launchSingleTop = true }
        },
        onDelete = { route -> routesViewModel.removeRoute(route) },
        onNavigateUp = { navController.navigateUp() },
    )
}

/**
 * Stateless layout for the route overview screen.
 *
 * @param routes The currently stored routes.
 * @param onRouteClick Called when a route card is tapped.
 * @param onDelete Called after delete confirmation dialog is accepted.
 * @param onNavigateUp Called when the back arrow is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutesContent(
    routes: List<Route>,
    onRouteClick: (Route) -> Unit,
    onDelete: (Route) -> Unit,
    onNavigateUp: () -> Unit,
) {
    var deletePending by remember { mutableStateOf<Route?>(null) }

    // Delete confirmation dialog
    deletePending?.let { route ->
        AlertDialog(
            onDismissRequest = { deletePending = null },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = { Text(stringResource(R.string.routes_delete_title)) },
            text = { Text(stringResource(R.string.routes_delete_message, route.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(route)
                    deletePending = null
                }) {
                    Text(
                        text = stringResource(R.string.favorites_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deletePending = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_routes)) },
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
        if (routes.isEmpty()) {
            RoutesEmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 32.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = routes,
                    key = { it.name },
                ) { route ->
                    RouteItem(
                        route = route,
                        onClick = { onRouteClick(route) },
                        onDeleteClick = { deletePending = route },
                    )
                }
            }
        }
    }
}

/**
 * Empty state when no routes exist yet.
 */
@Composable
private fun RoutesEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = LineAwesomeIcons.RouteSolid,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.routes_empty),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.routes_empty_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A single route card in the overview list.
 *
 * @param route The route to display.
 * @param onClick Called when the card is tapped.
 * @param onDeleteClick Called when the delete button is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteItem(
    route: Route,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = LineAwesomeIcons.RouteSolid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = route.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.route_waypoints_count, route.waypoints.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = dateFormat.format(Date(route.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.cd_delete_named_item, route.name),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
