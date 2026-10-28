package com.noobexon.xposedfakelocation.manager.ui.favorites

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.model.FavoriteLocation
import com.noobexon.xposedfakelocation.data.model.Route


/** Which list the Favorites screen shows. */
enum class FavoritesTab { LOCATIONS, ROUTES }

/**
 * Stateful Favorites screen composable.
 *
 * Collects [FavoritesViewModel.favorites] and wires the delete/edit/select callbacks before
 * delegating all layout to the stateless [FavoritesContent]. Navigation is handled here:
 * selecting a favorite calls [onFavoriteSelected] (which updates the map marker in [NavGraph])
 * and then pops the back stack.
 *
 * @param navController Used to navigate back after a selection or the back-arrow tap.
 * @param onFavoriteSelected Called with the chosen [FavoriteLocation] when the user taps a card;
 *   the caller is expected to apply it as the active spoof target.
 * @param onRouteSelected Called with a tapped saved route; the caller loads it into the map.
 * @param initialTab Tab shown first (Routes when opened from the route panel).
 * @param favoritesViewModel Injected by [viewModel]; can be overridden in tests.
 */
@Composable
fun FavoritesScreen(
    navController: NavController,
    onFavoriteSelected: (FavoriteLocation) -> Unit,
    onRouteSelected: (Route) -> Unit,
    initialTab: FavoritesTab = FavoritesTab.LOCATIONS,
    favoritesViewModel: FavoritesViewModel = viewModel()
) {
    val favorites by favoritesViewModel.favorites.collectAsStateWithLifecycle()
    val routes by favoritesViewModel.routes.collectAsStateWithLifecycle()

    FavoritesContent(
        favorites = favorites,
        routes = routes,
        initialTab = initialTab,
        onFavoriteClick = { favorite ->
            onFavoriteSelected(favorite)
            navController.navigateUp()
        },
        onRouteClick = { route ->
            onRouteSelected(route)
            navController.navigateUp()
        },
        onDelete = { favorite -> favoritesViewModel.removeFavorite(favorite) },
        onEdit = { old, new -> favoritesViewModel.updateFavorite(old, new) },
        onDeleteRoute = favoritesViewModel::deleteRoute,
        onRenameRoute = favoritesViewModel::renameRoute,
        onNavigateUp = { navController.navigateUp() },
    )
}

/**
 * Stateless layout for the Favorites screen.
 *
 * Manages two pieces of ephemeral dialog state: [deletePending] (the entry awaiting confirmation
 * before deletion) and [editPending] (the entry currently open in the edit dialog). Both are
 * local [remember] state because they are transient UI interactions that don't need to survive
 * configuration changes or process death.
 *
 * Shows a [TabRow] with a Locations tab and a Routes tab; the selected tab survives configuration
 * changes. The Locations tab renders either [FavoritesEmptyState] or a [LazyColumn] of
 * [FavoriteItem] cards depending on whether [favorites] is empty. The Routes tab delegates to
 * [RoutesList].
 *
 * @param favorites The current list of saved favorites, observed from [FavoritesViewModel].
 * @param routes The current list of saved routes, observed from [FavoritesViewModel].
 * @param initialTab Tab selected on first composition.
 * @param onFavoriteClick Called when the user taps a card to select it as the spoof target.
 * @param onRouteClick Called when the user taps a route card to load it into the map.
 * @param onDelete Called with the entry to remove after the user confirms the delete dialog.
 * @param onEdit Called with the original and updated entry after the user saves the edit dialog.
 * @param onDeleteRoute Called with the route to remove after the user confirms the delete dialog.
 * @param onRenameRoute Called with the route and its new name after the user saves the rename dialog.
 * @param onNavigateUp Called when the back-arrow in the [TopAppBar] is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoritesContent(
    favorites: List<FavoriteLocation>,
    routes: List<Route>,
    initialTab: FavoritesTab,
    onFavoriteClick: (FavoriteLocation) -> Unit,
    onRouteClick: (Route) -> Unit,
    onDelete: (FavoriteLocation) -> Unit,
    onEdit: (old: FavoriteLocation, new: FavoriteLocation) -> Unit,
    onDeleteRoute: (Route) -> Unit,
    onRenameRoute: (Route, String) -> Unit,
    onNavigateUp: () -> Unit,
) {
    var deletePending by remember { mutableStateOf<FavoriteLocation?>(null) }
    var editPending by remember { mutableStateOf<FavoriteLocation?>(null) }
    var tab by rememberSaveable { mutableStateOf(initialTab) }

    deletePending?.let { favorite ->
        DeleteConfirmationDialog(
            favoriteName = favorite.name,
            onConfirm = {
                onDelete(favorite)
                deletePending = null
            },
            onDismiss = { deletePending = null },
        )
    }

    editPending?.let { favorite ->
        EditFavoriteDialog(
            favorite = favorite,
            onSave = { updated ->
                onEdit(favorite, updated)
                editPending = null
            },
            onDismiss = { editPending = null },
        )
    }

    Scaffold(
        topBar = { FavoritesTopAppBar(onNavigateUp) }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            TabRow(selectedTabIndex = tab.ordinal) {
                Tab(
                    selected = tab == FavoritesTab.LOCATIONS,
                    onClick = { tab = FavoritesTab.LOCATIONS },
                    text = { Text(stringResource(R.string.favorites_tab_locations)) },
                )
                Tab(
                    selected = tab == FavoritesTab.ROUTES,
                    onClick = { tab = FavoritesTab.ROUTES },
                    text = { Text(stringResource(R.string.favorites_tab_routes)) },
                )
            }
            when (tab) {
                FavoritesTab.LOCATIONS -> if (favorites.isEmpty()) {
                    FavoritesEmptyState(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = favorites,
                            key = { "${it.name}_${it.latitude}_${it.longitude}" }
                        ) { favorite ->
                            FavoriteItem(
                                favorite = favorite,
                                onClick = { onFavoriteClick(favorite) },
                                onEditClick = { editPending = favorite },
                                onDeleteClick = { deletePending = favorite },
                            )
                        }
                    }
                }
                FavoritesTab.ROUTES -> RoutesList(
                    routes = routes,
                    onRouteClick = onRouteClick,
                    onRename = onRenameRoute,
                    onDelete = onDeleteRoute,
                )
            }
        }
    }
}

/**
 * Top app bar for the Favorites screen with the screen title and a back-navigation icon.
 *
 * @param onNavigateUp Called when the back-arrow button is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoritesTopAppBar(onNavigateUp: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.screen_favorites)) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
        ),
        navigationIcon = {
            IconButton(onClick = onNavigateUp) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back)
                )
            }
        }
    )
}

/**
 * Full-screen empty state shown when the favorites list is empty. Displays a centred icon,
 * a title, and a short hint directing the user to save a location from the map.
 *
 * @param modifier Applied to the root [Column]; caller is expected to pass
 *   `fillMaxSize + padding` so the content is centred within the available area.
 */
@Composable
private fun FavoritesEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.FavoriteBorder,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.favorites_empty),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.favorites_empty_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * A single [ElevatedCard] row representing one saved favorite location.
 *
 * Displays a map-pin leading icon, the entry's name, an optional description (omitted when
 * blank), and formatted coordinates. Trailing action buttons open the edit dialog and the delete
 * confirmation dialog respectively.
 *
 * @param favorite The favorite entry to display.
 * @param onClick Called when the card body is tapped (selects the location as the spoof target).
 * @param onEditClick Called when the edit icon button is tapped.
 * @param onDeleteClick Called when the delete icon button is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoriteItem(
    favorite: FavoriteLocation,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = favorite.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (favorite.description.isNotBlank()) {
                    Text(
                        text = favorite.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = stringResource(
                        R.string.coordinates_lat_lon,
                        "%.5f".format(favorite.latitude),
                        "%.5f".format(favorite.longitude)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onEditClick) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.cd_edit_named_item, favorite.name),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.cd_delete_named_item, favorite.name),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Stateful dialog for editing an existing favorite location.
 *
 * Unlike the map-screen dialogs which delegate state to [FavoritesViewModel], this dialog owns
 * its own draft state via [remember]. This is intentional: the edit draft is transient UI state
 * that does not need to survive configuration changes or process death, and adding ViewModel
 * state for it would be unnecessary complexity.
 *
 * All four fields perform live validation:
 * - **Name**: required; error shown immediately when the field is cleared.
 * - **Description**: optional; no validation.
 * - **Latitude**: must parse as `Double` in `−90..90`; error shown on every keystroke.
 * - **Longitude**: must parse as `Double` in `−180..180`; error shown on every keystroke.
 *
 * A final validation pass runs on "Save" to catch the initial state (e.g. completely invalid
 * pre-filled coordinates) before calling [onSave].
 *
 * @param favorite The entry to edit; used to seed the initial field values.
 * @param onSave Called with the updated [FavoriteLocation] when all fields are valid and the
 *   user taps "Save". The caller is responsible for persisting the change.
 * @param onDismiss Called when the dialog is dismissed without saving.
 */
@Composable
private fun EditFavoriteDialog(
    favorite: FavoriteLocation,
    onSave: (FavoriteLocation) -> Unit,
    onDismiss: () -> Unit,
) {
    val latRange = -90.0..90.0
    val lonRange = -180.0..180.0

    var name by remember { mutableStateOf(favorite.name) }
    var description by remember { mutableStateOf(favorite.description) }
    var latitude by remember { mutableStateOf(favorite.latitude.toString()) }
    var longitude by remember { mutableStateOf(favorite.longitude.toString()) }

    var nameError by remember { mutableStateOf(false) }
    var latitudeError by remember { mutableStateOf(false) }
    var longitudeError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.favorites_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = it.isBlank()
                    },
                    label = { Text(stringResource(R.string.field_name)) },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text(stringResource(R.string.validation_name_required)) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.field_description)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = latitude,
                    onValueChange = {
                        latitude = it
                        latitudeError = it.toDoubleOrNull()?.let { v -> v !in latRange } != false
                    },
                    label = { Text(stringResource(R.string.field_latitude)) },
                    isError = latitudeError,
                    supportingText = if (latitudeError) {
                        { Text(stringResource(R.string.validation_latitude_range)) }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = longitude,
                    onValueChange = {
                        longitude = it
                        longitudeError = it.toDoubleOrNull()?.let { v -> v !in lonRange } != false
                    },
                    label = { Text(stringResource(R.string.field_longitude)) },
                    isError = longitudeError,
                    supportingText = if (longitudeError) {
                        { Text(stringResource(R.string.validation_longitude_range)) }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val latVal = latitude.toDoubleOrNull()
                    val lonVal = longitude.toDoubleOrNull()
                    nameError = name.isBlank()
                    latitudeError = latVal == null || latVal !in latRange
                    longitudeError = lonVal == null || lonVal !in lonRange
                    if (!nameError && !latitudeError && !longitudeError) {
                        onSave(
                            favorite.copy(
                                name = name.trim(),
                                description = description.trim(),
                                latitude = latVal!!,
                                longitude = lonVal!!,
                            )
                        )
                    }
                }
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/**
 * Confirmation dialog shown before permanently deleting a favorite entry.
 *
 * Displays the entry's [favoriteName] in the message body so the user can verify they are
 * deleting the correct item. The confirm button is tinted error-red to signal a destructive action.
 *
 * @param favoriteName The name of the entry being deleted, shown in the dialog message.
 * @param onConfirm Called when the user confirms deletion.
 * @param onDismiss Called when the user cancels or dismisses the dialog.
 */
@Composable
internal fun DeleteConfirmationDialog(
    favoriteName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text(stringResource(R.string.favorites_delete_title)) },
        text = { Text(stringResource(R.string.favorites_delete_message, favoriteName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.favorites_delete_confirm),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
