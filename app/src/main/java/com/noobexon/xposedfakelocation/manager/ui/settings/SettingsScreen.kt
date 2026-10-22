package com.noobexon.xposedfakelocation.manager.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.MPS_TO_KMH
import com.noobexon.xposedfakelocation.data.model.JoystickPreset
import com.noobexon.xposedfakelocation.manager.localization.LanguageOption
import com.noobexon.xposedfakelocation.manager.localization.LocaleController
import com.noobexon.xposedfakelocation.manager.ui.theme.ThemeOption
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Spacing and shape constants shared across all setting row composables. Centralised here so any
 * future design-token changes only require edits in one place.
 */
private object Dimensions {
    val SPACING_EXTRA_SMALL = 4.dp
    val SPACING_SMALL = 8.dp
    val SPACING_MEDIUM = 16.dp
    val SPACING_LARGE = 24.dp
    val CARD_CORNER_RADIUS = 12.dp
    val CARD_ELEVATION = 2.dp
}

/**
 * Entry point for the settings destination. Acts as a state holder: owns the [SettingsViewModel],
 * collects [SettingsViewModel.uiState] as a single snapshot, builds the declarative [SettingEntry]
 * category list, and handles one-shot [SystemHooksEvent]s with lifecycle awareness (restart dialog
 * / snackbar). All layout is delegated to the stateless [SettingsContent], keeping it previewable
 * and testable independently.
 *
 * @param navController Used to navigate back when the user taps the top-bar navigation icon.
 * @param settingsViewModel Backing ViewModel; defaults to the screen-scoped instance from
 *   [viewModel].
 */
@Composable
fun SettingsScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    /**
     * Drives the system-hooks restart dialog. `null` = dialog hidden; `true` = hooks were just
     * enabled; `false` = hooks were just disabled. Two separate message strings exist for the two
     * cases to give the user clear context.
     */
    var restartDialogEnabled by remember { mutableStateOf<Boolean?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, settingsViewModel) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                settingsViewModel.systemHooksEvents.collect { event ->
                    when (event) {
                        is SystemHooksEvent.RestartRequired -> restartDialogEnabled = event.enabled
                        is SystemHooksEvent.ModuleNotActive ->
                            snackbarHostState.showSnackbar(context.getString(R.string.system_hooks_module_inactive))
                        is SystemHooksEvent.ScopeRequestFailed ->
                            snackbarHostState.showSnackbar(context.getString(R.string.system_hooks_scope_failed, event.message))
                    }
                }
            }
            launch {
                settingsViewModel.settingsBackupEvents.collect { event ->
                    val message = when (event) {
                        SettingsBackupEvent.ExportSuccess -> context.getString(R.string.settings_export_success)
                        SettingsBackupEvent.ImportSuccess -> context.getString(R.string.settings_import_success)
                        SettingsBackupEvent.ImportPartial -> context.getString(R.string.settings_import_partial)
                        SettingsBackupEvent.InvalidFile -> context.getString(R.string.settings_import_invalid)
                        SettingsBackupEvent.ModuleNotActive -> context.getString(R.string.settings_export_module_inactive)
                        is SettingsBackupEvent.Error -> context.getString(R.string.settings_backup_error, event.message)
                    }
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
    }

    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val joystick by settingsViewModel.joystickSettings.collectAsStateWithLifecycle()

    fun joystickSpeedEntry(setting: NumericSetting, speedMps: Float, save: (Float) -> Unit) = SettingEntry.Numeric(
        setting = setting,
        enabled = true,
        onEnabledChange = {},
        value = speedMps * MPS_TO_KMH,
        onValueChange = { kmh -> save(kmh / MPS_TO_KMH) },
        onReset = { settingsViewModel.resetNumeric(setting) },
        showToggle = false,
    )

    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) settingsViewModel.exportTo(uri) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) pendingImportUri = uri }

    val exportFileName = remember {
        val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        "xposedfakelocation-settings-$date.json"
    }

    val selectedLanguage = LanguageOption.fromTag(uiState.languageTag)

    val categories: List<Pair<SettingsCategory, List<SettingEntry>>> = listOf(
        SettingsCategory.LOCATION to listOf(
            SettingEntry.Numeric(NumericSetting.RANDOMIZE_RADIUS, uiState.useRandomize, settingsViewModel::setUseRandomize, uiState.randomizeRadius, onValueChange = { settingsViewModel.setRandomizeRadius(it.toDouble()) }, onReset = { settingsViewModel.resetNumeric(NumericSetting.RANDOMIZE_RADIUS) }),
            SettingEntry.Numeric(NumericSetting.HORIZONTAL_ACCURACY, uiState.useAccuracy, settingsViewModel::setUseAccuracy, uiState.accuracy, onValueChange = { settingsViewModel.setAccuracy(it.toDouble()) }, onReset = { settingsViewModel.resetNumeric(NumericSetting.HORIZONTAL_ACCURACY) }),
            SettingEntry.Numeric(NumericSetting.VERTICAL_ACCURACY, uiState.useVerticalAccuracy, settingsViewModel::setUseVerticalAccuracy, uiState.verticalAccuracy, settingsViewModel::setVerticalAccuracy, onReset = { settingsViewModel.resetNumeric(NumericSetting.VERTICAL_ACCURACY) })
        ),
        SettingsCategory.ALTITUDE to listOf(
            SettingEntry.Numeric(NumericSetting.ALTITUDE, uiState.useAltitude, settingsViewModel::setUseAltitude, uiState.altitude, onValueChange = { settingsViewModel.setAltitude(it.toDouble()) }, onReset = { settingsViewModel.resetNumeric(NumericSetting.ALTITUDE) }),
            SettingEntry.Numeric(NumericSetting.MEAN_SEA_LEVEL, uiState.useMeanSeaLevel, settingsViewModel::setUseMeanSeaLevel, uiState.meanSeaLevel, onValueChange = { settingsViewModel.setMeanSeaLevel(it.toDouble()) }, onReset = { settingsViewModel.resetNumeric(NumericSetting.MEAN_SEA_LEVEL) }),
            SettingEntry.Numeric(NumericSetting.MEAN_SEA_LEVEL_ACCURACY, uiState.useMeanSeaLevelAccuracy, settingsViewModel::setUseMeanSeaLevelAccuracy, uiState.meanSeaLevelAccuracy, settingsViewModel::setMeanSeaLevelAccuracy, onReset = { settingsViewModel.resetNumeric(NumericSetting.MEAN_SEA_LEVEL_ACCURACY) })
        ),
        SettingsCategory.MOVEMENT to listOf(
            SettingEntry.Numeric(NumericSetting.SPEED, uiState.useSpeed, settingsViewModel::setUseSpeed, uiState.speed, settingsViewModel::setSpeed, onReset = { settingsViewModel.resetNumeric(NumericSetting.SPEED) }),
            SettingEntry.Numeric(NumericSetting.SPEED_ACCURACY, uiState.useSpeedAccuracy, settingsViewModel::setUseSpeedAccuracy, uiState.speedAccuracy, settingsViewModel::setSpeedAccuracy, onReset = { settingsViewModel.resetNumeric(NumericSetting.SPEED_ACCURACY) })
        ),
        SettingsCategory.JOYSTICK to listOf(
            joystickSpeedEntry(NumericSetting.JOYSTICK_MAX_SPEED, joystick.maxSpeedMps, settingsViewModel::setJoystickMaxSpeed),
            joystickSpeedEntry(NumericSetting.JOYSTICK_PRESET_WALK, joystick.walkSpeedMps) { settingsViewModel.setJoystickPresetSpeed(JoystickPreset.WALK, it) },
            joystickSpeedEntry(NumericSetting.JOYSTICK_PRESET_RUN, joystick.runSpeedMps) { settingsViewModel.setJoystickPresetSpeed(JoystickPreset.RUN, it) },
            joystickSpeedEntry(NumericSetting.JOYSTICK_PRESET_BIKE, joystick.bikeSpeedMps) { settingsViewModel.setJoystickPresetSpeed(JoystickPreset.BIKE, it) },
            joystickSpeedEntry(NumericSetting.JOYSTICK_PRESET_DRIVE, joystick.driveSpeedMps) { settingsViewModel.setJoystickPresetSpeed(JoystickPreset.DRIVE, it) },
        ),
        SettingsCategory.NOTIFICATIONS to listOf(
            SettingEntry.Switch(SettingKeys.HIDE_TOAST, R.string.setting_hide_toast_title, R.string.setting_hide_toast_description, uiState.hideFakeLocationToast, settingsViewModel::setHideFakeLocationToast)
        ),
        SettingsCategory.SYSTEM_HOOKS to listOf(
            SettingEntry.Switch(SettingKeys.SYSTEM_HOOKS, R.string.setting_system_hooks_title, R.string.setting_system_hooks_description, uiState.systemHooksEnabled, settingsViewModel::setEnableSystemHooks)
        ),
        SettingsCategory.WIFI_IDENTITY to buildList {
            add(SettingEntry.Switch(SettingKeys.WIFI_IDENTITY, R.string.setting_wifi_identity_title, R.string.setting_wifi_identity_description, uiState.wifiIdentityEnabled, settingsViewModel::setEnableWifiIdentity))
            if (uiState.wifiIdentityEnabled) {
                add(SettingEntry.Text(SettingKeys.WIFI_SSID, R.string.setting_wifi_ssid_title, R.string.setting_wifi_ssid_description, R.string.setting_wifi_ssid_label, uiState.wifiSsid, onValueChange = settingsViewModel::setWifiSsid, onReset = settingsViewModel::resetWifiSsid))
                add(SettingEntry.Text(SettingKeys.WIFI_BSSID, R.string.setting_wifi_bssid_title, R.string.setting_wifi_bssid_description, R.string.setting_wifi_bssid_label, uiState.wifiBssid, onValueChange = settingsViewModel::setWifiBssid, onReset = settingsViewModel::resetWifiBssid))
                add(SettingEntry.Text(SettingKeys.WIFI_RSSI, R.string.setting_wifi_rssi_title, R.string.setting_wifi_rssi_description, R.string.setting_wifi_rssi_label, uiState.wifiRssi.toString(), TextInputKind.SIGNED_NUMBER, settingsViewModel::setWifiRssi, onReset = settingsViewModel::resetWifiRssi))
            }
        },
        SettingsCategory.EXTERNAL_CONTROL to listOf(
            SettingEntry.Switch(SettingKeys.BROADCAST, R.string.setting_external_broadcast_title, R.string.setting_external_broadcast_description, uiState.enableBroadcastControl, settingsViewModel::setEnableBroadcastControl)
        ),
        SettingsCategory.APPEARANCE to listOf(
            SettingEntry.Theme(uiState.themeOption) { option ->
                settingsViewModel.setTheme(option)
            }
        ),
        SettingsCategory.LANGUAGE to listOf(
            SettingEntry.Language(selectedLanguage) { option ->
                settingsViewModel.setLanguage(option.tag)
                context.findActivity()?.recreate()
            }
        )
    )

    SettingsContent(
        categories = categories,
        snackbarHostState = snackbarHostState,
        restartDialogEnabled = restartDialogEnabled,
        onRestartDialogDismiss = { restartDialogEnabled = null },
        onBack = { navController.navigateUp() },
        onResetConfirmed = {
            settingsViewModel.resetToDefaults()
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.settings_reset_done))
            }
        },
        onExport = { exportLauncher.launch(exportFileName) },
        onImport = { importLauncher.launch(arrayOf("application/json")) }
    )

    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text(stringResource(R.string.settings_import_title)) },
            text = { Text(stringResource(R.string.settings_import_message)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingImportUri = null
                    settingsViewModel.importFrom(uri)
                }) {
                    Text(stringResource(R.string.action_import))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/**
 * Stateless settings UI. Renders a branded [TopAppBar] with an expanding inline search field and
 * an overflow "Reset" menu, then the scrollable list of [categories] grouped under [CategoryLabel]
 * headers inside [Card]s. Owns only UI-local state (search text, menu visibility, dialog flags).
 * All side-effectful actions arrive as callbacks so this composable can be previewed and tested
 * without a ViewModel.
 *
 * Tapping outside the search field or the content area collapses search. The system Back gesture
 * also collapses search before navigating up.
 *
 * @param categories Grouped entries to render, already in the desired display order.
 * @param snackbarHostState Host for transient one-line messages (errors, reset confirmation).
 * @param restartDialogEnabled Non-null value shows the reboot-required dialog; `true` uses the
 *   "hooks enabled" message, `false` uses the "hooks disabled" message, `null` hides the dialog.
 * @param onRestartDialogDismiss Invoked when the restart dialog is acknowledged or dismissed.
 * @param onBack Invoked when the user navigates back while search is not open.
 * @param onResetConfirmed Invoked when the user confirms the "reset all settings" action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    categories: List<Pair<SettingsCategory, List<SettingEntry>>>,
    snackbarHostState: SnackbarHostState,
    restartDialogEnabled: Boolean?,
    onRestartDialogDismiss: () -> Unit,
    onBack: () -> Unit,
    onResetConfirmed: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var searchActive by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = searchActive) {
        searchActive = false
        query = ""
    }

    val filtered = if (query.isBlank()) {
        categories
    } else {
        categories
            .map { (category, entries) -> category to entries.filter { entryMatches(query, searchTextOf(it, context)) } }
            .filter { it.second.isNotEmpty() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (searchActive) {
                        val focusRequester = remember { FocusRequester() }
                        LaunchedEffect(Unit) { focusRequester.requestFocus() }
                        val searchFieldDescription = stringResource(R.string.cd_search_settings)
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text(stringResource(R.string.settings_search_hint)) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                focusedTextColor = MaterialTheme.colorScheme.onPrimary,
                                unfocusedTextColor = MaterialTheme.colorScheme.onPrimary,
                                cursorColor = MaterialTheme.colorScheme.onPrimary,
                                focusedPlaceholderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .semantics { contentDescription = searchFieldDescription }
                        )
                    } else {
                        Text(stringResource(R.string.screen_settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        if (searchActive) {
                            searchActive = false
                            query = ""
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(
                                if (searchActive) R.string.cd_collapse_search else R.string.cd_navigate_back
                            )
                        )
                    }
                },
                actions = {
                    if (searchActive) {
                        IconButton(onClick = {
                            if (query.isEmpty()) {
                                searchActive = false
                            } else {
                                query = ""
                            }
                        }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_clear_search))
                        }
                    } else {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search_settings))
                        }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings_export)) },
                                onClick = {
                                    menuExpanded = false
                                    onExport()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings_import)) },
                                onClick = {
                                    menuExpanded = false
                                    onImport()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings_reset_all)) },
                                onClick = {
                                    menuExpanded = false
                                    showResetDialog = true
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    focusManager.clearFocus()
                    if (searchActive) {
                        searchActive = false
                        query = ""
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Dimensions.SPACING_MEDIUM)
                    .verticalScroll(scrollState)
            ) {
                Spacer(modifier = Modifier.height(Dimensions.SPACING_MEDIUM))

                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Dimensions.SPACING_LARGE),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.settings_search_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    filtered.forEach { (category, entries) ->
                        CategoryLabel(stringResource(category.titleRes))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Dimensions.SPACING_SMALL),
                            shape = RoundedCornerShape(Dimensions.CARD_CORNER_RADIUS),
                            elevation = CardDefaults.cardElevation(defaultElevation = Dimensions.CARD_ELEVATION)
                        ) {
                            Column(modifier = Modifier.padding(Dimensions.SPACING_SMALL)) {
                                entries.forEachIndexed { index, entry ->
                                    key(entry.key) {
                                        SettingEntryRow(entry)
                                        if (index != entries.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = Dimensions.SPACING_SMALL),
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(Dimensions.SPACING_MEDIUM))
                    }
                }

                Spacer(modifier = Modifier.height(Dimensions.SPACING_LARGE))
            }

            if (showResetDialog) {
                AlertDialog(
                    onDismissRequest = { showResetDialog = false },
                    title = { Text(stringResource(R.string.settings_reset_title)) },
                    text = { Text(stringResource(R.string.settings_reset_message)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showResetDialog = false
                            onResetConfirmed()
                        }) {
                            Text(stringResource(R.string.action_reset))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetDialog = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }

            restartDialogEnabled?.let { enabled ->
                AlertDialog(
                    onDismissRequest = onRestartDialogDismiss,
                    title = { Text(stringResource(R.string.dialog_restart_required_title)) },
                    text = {
                        Text(
                            stringResource(
                                if (enabled) R.string.dialog_restart_required_enable_message
                                else R.string.dialog_restart_required_disable_message
                            )
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = onRestartDialogDismiss) {
                            Text(stringResource(R.string.action_ok))
                        }
                    }
                )
            }
        }
    }
}

/**
 * Walks the [Context] wrapper chain until it reaches the hosting [Activity]. Required because
 * Compose's [LocalContext] returns a [ContextWrapper], not the raw [Activity], so a direct cast
 * would fail.
 *
 * @return The hosting [Activity], or `null` if this context is not attached to one.
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Builds the search corpus string for [entry]: its title and description joined with a space, with
 * the selected language's display name appended for [SettingEntry.Language] entries. Called only
 * when [query] is non-blank to avoid redundant [Context.getString] calls on every recomposition.
 *
 * @param entry The entry whose searchable text is needed.
 * @param context Used to resolve string resources outside Compose.
 * @return A single concatenated string suitable for case-insensitive substring matching.
 */
private fun searchTextOf(entry: SettingEntry, context: Context): String {
    val base = context.getString(entry.titleRes) + " " + context.getString(entry.descriptionRes)
    return if (entry is SettingEntry.Language) {
        val option = entry.selected
        val display = if (option == LanguageOption.SYSTEM) {
            context.getString(option.labelRes)
        } else {
            option.autonym ?: context.getString(option.labelRes)
        }
        "$base $display"
    } else {
        base
    }
}

/**
 * Returns `true` if [haystack] contains [query] (case-insensitive, leading/trailing whitespace
 * ignored). An empty or blank query always matches.
 *
 * @param query The user's search input.
 * @param haystack The pre-built search corpus string from [searchTextOf].
 */
private fun entryMatches(query: String, haystack: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return haystack.contains(q, ignoreCase = true)
}

/**
 * Dispatches a [SettingEntry] to its dedicated row composable. This indirection keeps the
 * rendering loop in [SettingsContent] free of `when` branches and decouples each variant's
 * composable from the list layout.
 *
 * @param entry The entry to render.
 */
@Composable
private fun SettingEntryRow(entry: SettingEntry) {
    when (entry) {
        is SettingEntry.Switch -> BooleanSettingItem(
            title = stringResource(entry.titleRes),
            description = stringResource(entry.descriptionRes),
            checked = entry.checked,
            onCheckedChange = entry.onCheckedChange
        )
        is SettingEntry.Numeric -> NumericSettingItem(
            setting = entry.setting,
            useValue = entry.enabled,
            onUseValueChange = entry.onEnabledChange,
            value = entry.value,
            onValueChange = entry.onValueChange,
            onReset = entry.onReset,
            showToggle = entry.showToggle
        )
        is SettingEntry.Text -> TextSettingItem(
            title = stringResource(entry.titleRes),
            description = stringResource(entry.descriptionRes),
            label = stringResource(entry.labelRes),
            value = entry.value,
            inputKind = entry.inputKind,
            onValueChange = entry.onValueChange,
            onReset = entry.onReset
        )
        is SettingEntry.Language -> LanguageSettingItem(
            selectedLanguage = entry.selected,
            onLanguageSelected = entry.onSelected
        )
        is SettingEntry.Theme -> ThemeSettingItem(
            selectedTheme = entry.selected,
            onThemeSelected = entry.onSelected
        )
    }
}

/**
 * Uppercase section label rendered above each settings [Card]. Uses the primary colour and bold
 * weight to visually separate categories without requiring a heavy header component.
 *
 * @param title Already-resolved (and uppercased by this composable) category name.
 */
@Composable
private fun CategoryLabel(title: String) {
    Text(
        text = title.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            start = Dimensions.SPACING_SMALL,
            top = Dimensions.SPACING_SMALL,
            bottom = Dimensions.SPACING_EXTRA_SMALL
        )
    )
}

/**
 * Reusable row header shared by all setting item composables. Lays out the [title] with a small
 * info [IconButton] that toggles [description] below it, plus a [trailing] slot at the far end of
 * the row (typically a [Switch] or a text label). Wrapping the row in [onClick] makes the entire
 * header tappable (used by the language picker row).
 *
 * @param title Localized setting name shown in [MaterialTheme.typography.titleMedium].
 * @param description Help text revealed when the info icon is tapped.
 * @param showTooltip Whether [description] is currently visible below the title row.
 * @param onToggleTooltip Invoked when the info icon is tapped to toggle [showTooltip].
 * @param onClick Optional click handler applied to the entire row. When non-null the row becomes
 *   tappable; the info [IconButton] still consumes its own clicks independently.
 * @param onReset Optional "reset this setting to default" handler. When non-null, a reset icon is
 *   rendered just before [trailing]; tapping it opens a confirmation dialog and only invokes this
 *   callback once the user confirms. It consumes its own clicks independently of [onClick].
 * @param trailing Slot composable placed at the end of the row (e.g. [SettingSwitch]).
 */
@Composable
private fun SettingHeader(
    title: String,
    description: String,
    showTooltip: Boolean,
    onToggleTooltip: () -> Unit,
    onClick: (() -> Unit)? = null,
    onReset: (() -> Unit)? = null,
    trailing: @Composable () -> Unit
) {
    val moreInfoDescription = stringResource(R.string.setting_more_info, title)
    val resetDescription = stringResource(R.string.cd_reset_setting, title)
    var showResetConfirmation by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )

                    IconButton(
                        onClick = onToggleTooltip,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = moreInfoDescription,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (onReset != null) {
                IconButton(
                    onClick = { showResetConfirmation = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Restore,
                        contentDescription = resetDescription,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(Dimensions.SPACING_SMALL))
            }

            trailing()
        }

        if (showTooltip) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimensions.SPACING_EXTRA_SMALL)
            )
        }
    }

    if (onReset != null && showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text(stringResource(R.string.setting_reset_title)) },
            text = { Text(stringResource(R.string.setting_reset_message, title)) },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirmation = false
                    onReset()
                }) {
                    Text(stringResource(R.string.action_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/**
 * Consistently styled [Switch] for setting rows. Applies the app's colour scheme and provides
 * context-aware accessibility descriptions so screen readers announce the current action ("Disable
 * X" vs "Enable X") rather than the static state.
 *
 * @param title Localized setting name, used to build the content description.
 * @param checked Current switch state.
 * @param onCheckedChange Invoked with the new [Boolean] when the switch is toggled.
 */
@Composable
private fun SettingSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val disableDescription = stringResource(R.string.setting_disable, title)
    val enableDescription = stringResource(R.string.setting_enable, title)
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.primary,
            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.semantics {
            contentDescription = if (checked) disableDescription else enableDescription
        }
    )
}

/**
 * Language picker row. Displays the setting title with an info tooltip button and the currently
 * active language name on the right. Tapping anywhere on the row opens [LanguageSelectionDialog];
 * selecting an option there applies it and dismisses the dialog.
 *
 * Activity recreation (to apply the new locale) is the caller's responsibility and is triggered
 * inside [onLanguageSelected] by [SettingsScreen].
 *
 * @param selectedLanguage Currently active [LanguageOption], shown in the collapsed row.
 * @param onLanguageSelected Invoked with the chosen [LanguageOption] after the user taps an item
 *   in the dialog.
 */
@Composable
private fun LanguageSettingItem(
    selectedLanguage: LanguageOption,
    onLanguageSelected: (LanguageOption) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var showTooltip by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimensions.SPACING_SMALL)
    ) {
        SettingHeader(
            title = stringResource(R.string.setting_language_title),
            description = stringResource(R.string.setting_language_description),
            showTooltip = showTooltip,
            onToggleTooltip = { showTooltip = !showTooltip },
            onClick = { showDialog = true },
            trailing = {
                Text(
                    text = languageDisplayName(selectedLanguage),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Dimensions.SPACING_SMALL)
                )
            }
        )
    }

    if (showDialog) {
        LanguageSelectionDialog(
            selectedLanguage = selectedLanguage,
            onLanguageSelected = {
                onLanguageSelected(it)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

/**
 * Single-choice language picker dialog. Each [LanguageOption] is displayed as a radio row with the
 * language in its own script (autonym) as the primary label; the current-UI-language name appears
 * as a subtitle when it differs from the autonym, providing clarity for unfamiliar scripts.
 * [LanguageOption.SYSTEM] shows the device's resolved system language as its subtitle.
 *
 * @param selectedLanguage Currently active option, pre-selected in the list.
 * @param onLanguageSelected Invoked with the tapped [LanguageOption]; the dialog is then dismissed
 *   by the caller ([LanguageSettingItem]).
 * @param onDismiss Invoked when the dialog is dismissed without selecting an option (Cancel button
 *   or outside tap).
 */
@Composable
private fun LanguageSelectionDialog(
    selectedLanguage: LanguageOption,
    onLanguageSelected: (LanguageOption) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setting_language_title)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                LanguageOption.entries.forEach { option ->
                    val selected = option == selectedLanguage
                    val primary: String
                    val secondary: String?
                    if (option == LanguageOption.SYSTEM) {
                        primary = stringResource(option.labelRes)
                        secondary = LocaleController.systemLanguageAutonym()
                    } else {
                        val localized = stringResource(option.labelRes)
                        primary = option.autonym ?: localized
                        secondary = localized.takeUnless { it.equals(primary, ignoreCase = true) }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.SPACING_MEDIUM),
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onLanguageSelected(option) }
                            )
                            .padding(vertical = Dimensions.SPACING_SMALL)
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = null
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = primary,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (secondary != null) {
                                Text(
                                    text = secondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/**
 * Theme picker row. Displays the setting title with an info tooltip button and the currently
 * active theme name on the right. Tapping anywhere on the row opens [ThemeSelectionDialog];
 * selecting an option there applies it immediately — no Activity recreation is required.
 *
 * @param selectedTheme Currently active [ThemeOption], shown in the collapsed row.
 * @param onThemeSelected Invoked with the chosen [ThemeOption] after the user taps an item in
 *   the dialog.
 */
@Composable
private fun ThemeSettingItem(
    selectedTheme: ThemeOption,
    onThemeSelected: (ThemeOption) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var showTooltip by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimensions.SPACING_SMALL)
    ) {
        SettingHeader(
            title = stringResource(R.string.setting_theme_title),
            description = stringResource(R.string.setting_theme_description),
            showTooltip = showTooltip,
            onToggleTooltip = { showTooltip = !showTooltip },
            onClick = { showDialog = true },
            trailing = {
                Text(
                    text = stringResource(selectedTheme.labelRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Dimensions.SPACING_SMALL)
                )
            }
        )
    }

    if (showDialog) {
        ThemeSelectionDialog(
            selectedTheme = selectedTheme,
            onThemeSelected = {
                onThemeSelected(it)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

/**
 * Single-choice theme picker dialog. Each [ThemeOption] is displayed as a radio row with its
 * localised label. Selecting a row immediately applies the theme — no restart needed.
 *
 * @param selectedTheme Currently active option, pre-selected in the list.
 * @param onThemeSelected Invoked with the tapped [ThemeOption]; the dialog is then dismissed by
 *   the caller ([ThemeSettingItem]).
 * @param onDismiss Invoked when the dialog is dismissed without selecting an option (Cancel button
 *   or outside tap).
 */
@Composable
private fun ThemeSelectionDialog(
    selectedTheme: ThemeOption,
    onThemeSelected: (ThemeOption) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setting_theme_title)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                ThemeOption.entries.forEach { option ->
                    val selected = option == selectedTheme
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.SPACING_MEDIUM),
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onThemeSelected(option) }
                            )
                            .padding(vertical = Dimensions.SPACING_SMALL)
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(
                            text = stringResource(option.labelRes),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/**
 * Returns the display name for [option] as shown in the collapsed language row. For
 * [LanguageOption.SYSTEM] this is the localised "Follow system" label; for all others the
 * autonym (native script name) is preferred, falling back to the localised label if unavailable.
 *
 * @param option The [LanguageOption] whose display name is needed.
 * @return A [String] ready for display in the settings row.
 */
@Composable
@ReadOnlyComposable
private fun languageDisplayName(option: LanguageOption): String =
    if (option == LanguageOption.SYSTEM) {
        stringResource(option.labelRes)
    } else {
        option.autonym ?: stringResource(option.labelRes)
    }

/**
 * A single on/off toggle setting row composed of a [SettingHeader] (title + info tooltip) and a
 * trailing [SettingSwitch].
 *
 * @param title Localized name of the setting.
 * @param description Help text toggled by the info icon.
 * @param checked Current enabled state of the toggle.
 * @param onCheckedChange Invoked with the new [Boolean] when the switch is toggled.
 */
@Composable
private fun BooleanSettingItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    var showTooltip by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimensions.SPACING_SMALL)
    ) {
        SettingHeader(
            title = title,
            description = description,
            showTooltip = showTooltip,
            onToggleTooltip = { showTooltip = !showTooltip },
            trailing = {
                SettingSwitch(title = title, checked = checked, onCheckedChange = onCheckedChange)
            }
        )
    }
}

/**
 * A text setting row that commits on field blur or IME Done. This mirrors the numeric row's
 * "commit when editing finishes" behaviour without adding a switch or slider.
 */
@Composable
private fun TextSettingItem(
    title: String,
    description: String,
    label: String,
    value: String,
    inputKind: TextInputKind,
    onValueChange: (String) -> Unit,
    onReset: (() -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    var showTooltip by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(value) }
    var hasFocused by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (text != value) text = value
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimensions.SPACING_SMALL)
    ) {
        SettingHeader(
            title = title,
            description = description,
            showTooltip = showTooltip,
            onToggleTooltip = { showTooltip = !showTooltip },
            onReset = onReset,
            trailing = {}
        )

        Spacer(modifier = Modifier.height(Dimensions.SPACING_SMALL))

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = when (inputKind) {
                    TextInputKind.TEXT -> KeyboardType.Text
                    TextInputKind.SIGNED_NUMBER -> KeyboardType.Text
                },
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        hasFocused = true
                    } else if (hasFocused) {
                        onValueChange(text)
                    }
                }
        )
    }
}

/**
 * A numeric setting row driven entirely by [setting]'s static metadata. Renders an enable
 * [SettingSwitch] and, when enabled, an [OutlinedTextField] for direct value entry and a
 * continuous [Slider] for drag input, with range end labels below.
 *
 * **Edit lifecycle:** changes are committed (via [onValueChange]) only when the user finishes
 * editing — on field blur, IME "Done", or slider release — never per keystroke or drag frame. This
 * prevents excessive ViewModel writes and recompositions during in-progress input.
 *
 * **Value invariant:** committed values are clamped to `[setting.min, setting.max]` and rounded to
 * [NumericSetting.decimals] fractional places so the displayed field text and the persisted value
 * always match. If the user clears the field and blurs, [NumericSetting.default] is written instead.
 *
 * **State sync:** a `LaunchedEffect(value)` updates the local [Float] mirror and field text when
 * the external [value] changes (e.g. after a reset), but only if the local state differs — this
 * avoids clobbering in-progress slider drags driven by the same source value.
 *
 * @param setting Static metadata (titles, unit, range, default, precision) for this row.
 * @param useValue Whether this setting is currently enabled (controls switch state and
 *   field/slider visibility).
 * @param onUseValueChange Invoked when the enable switch is toggled.
 * @param value Current committed value in the unit described by [NumericSetting.unitRes].
 * @param onValueChange Invoked with the final [Float] once the user finishes editing. Never called
 *   mid-keystroke or mid-drag.
 * @param onReset Optional per-row "reset to default" handler, shown while the value is visible.
 * @param showToggle When `false`, no enable switch is shown and the value controls are always visible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NumericSettingItem(
    setting: NumericSetting,
    useValue: Boolean,
    onUseValueChange: (Boolean) -> Unit,
    value: Float,
    onValueChange: (Float) -> Unit,
    onReset: (() -> Unit)? = null,
    showToggle: Boolean = true
) {
    val title = stringResource(setting.titleRes)
    val description = stringResource(setting.descriptionRes)
    val label = stringResource(setting.labelRes)
    val unit = stringResource(setting.unitRes)
    val minValue = setting.min
    val maxValue = setting.max

    val focusManager = LocalFocusManager.current
    var showTooltip by remember { mutableStateOf(false) }
    val adjustDescription = stringResource(R.string.setting_adjust_value, title)
    val showValue = useValue || !showToggle

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimensions.SPACING_SMALL)
    ) {
        SettingHeader(
            title = title,
            description = description,
            showTooltip = showTooltip,
            onToggleTooltip = { showTooltip = !showTooltip },
            // Only offer a value reset when the setting is enabled (i.e. a value is in play).
            onReset = onReset?.takeIf { showValue },
            trailing = {
                if (showToggle) {
                    SettingSwitch(title = title, checked = useValue, onCheckedChange = onUseValueChange)
                }
            }
        )

        if (showValue) {
            Spacer(modifier = Modifier.height(Dimensions.SPACING_MEDIUM))

            var sliderValue by remember { mutableFloatStateOf(value) }
            var text by remember { mutableStateOf(formatNumericValue(value, setting)) }

            LaunchedEffect(value) {
                if (sliderValue != value) {
                    sliderValue = value
                    text = formatNumericValue(value, setting)
                }
            }

            val commit: () -> Unit = {
                val parsed = text.toFloatOrNull()
                if (parsed == null) {
                    val defaultValue = setting.default
                    sliderValue = defaultValue
                    text = formatNumericValue(defaultValue, setting)
                    onValueChange(defaultValue)
                } else {
                    val committed = setting.roundValue(parsed.coerceIn(minValue, maxValue))
                    sliderValue = committed
                    text = formatNumericValue(committed, setting)
                    onValueChange(committed)
                }
            }

            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    text = input
                    input.toFloatOrNull()?.let { sliderValue = it.coerceIn(minValue, maxValue) }
                },
                label = { Text(label) },
                suffix = { Text(unit) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused) commit()
                    }
            )

            Spacer(modifier = Modifier.height(Dimensions.SPACING_SMALL))

            Slider(
                value = sliderValue,
                onValueChange = { newValue ->
                    sliderValue = newValue
                    text = formatNumericValue(newValue, setting)
                },
                onValueChangeFinished = {
                    val committed = setting.roundValue(sliderValue)
                    sliderValue = committed
                    text = formatNumericValue(committed, setting)
                    onValueChange(committed)
                },
                valueRange = minValue..maxValue,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = adjustDescription }
            )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensions.SPACING_SMALL)
            ) {
                Text(
                    text = "${minValue.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${maxValue.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Formats [value] for display in a [NumericSettingItem] field. Always uses [Locale.US] (decimal
 * point `'.'`) so [String.toFloatOrNull] can parse it back regardless of the device locale, and
 * the stored value stays consistent with the displayed text.
 *
 * @param value The [Float] to format.
 * @param setting Provides [NumericSetting.decimals] to determine the number of fractional places.
 * @return A locale-stable string representation (e.g. `"12.5"`, `"100"`).
 */
private fun formatNumericValue(value: Float, setting: NumericSetting): String =
    String.format(Locale.US, "%.${setting.decimals}f", value)
