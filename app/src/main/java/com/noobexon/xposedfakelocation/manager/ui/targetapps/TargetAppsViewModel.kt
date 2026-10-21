package com.noobexon.xposedfakelocation.manager.ui.targetapps

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noobexon.xposedfakelocation.data.MANAGER_APP_PACKAGE_NAME
import com.noobexon.xposedfakelocation.data.repository.PreferencesRepository
import com.noobexon.xposedfakelocation.manager.App
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Snapshot of a single installable app as it should be rendered in the target-app list.
 *
 * Every field is immutable; the list is rebuilt by [TargetAppsViewModel.recompute] whenever
 * selection or filter state changes.
 *
 * @property label Human-readable app name returned by [android.content.pm.ApplicationInfo.loadLabel].
 * @property packageName Unique app identifier (e.g. `com.example.app`).
 * @property isSystemApp `true` when the APK carries [android.content.pm.ApplicationInfo.FLAG_SYSTEM].
 * @property isSelected `true` when the package is in the active LSPosed scope.
 * @property isPending `true` while a [XposedService.requestScope] callback is awaited.
 * @property isRelaunching `true` while a root force-stop + cold-start is in progress.
 */
@Immutable
data class TargetAppItem(
    val label: String,
    val packageName: String,
    val isSystemApp: Boolean = false,
    val isSelected: Boolean = false,
    val isPending: Boolean = false,
    val isRelaunching: Boolean = false
)

/**
 * Complete UI state for the target-apps screen, produced by [TargetAppsViewModel] and consumed
 * by [TargetAppsContent].
 *
 * [apps] is the canonical, ordered list of all installed launcher apps. [filteredApps] is a
 * derived view computed by [TargetAppsViewModel.recompute] and is the only list the UI renders.
 * The three `*Packages` sets are the single source of truth for selection and in-progress states;
 * the corresponding flags on each [TargetAppItem] in [filteredApps] are derived from them.
 *
 * @property apps All installed launcher apps in the current display order. Selected apps are
 *   sorted first after an initial load or a manual refresh.
 * @property selectedPackages Package names currently in the LSPosed scope.
 * @property pendingPackages Package names for which a [XposedService.requestScope] is in flight.
 * @property relaunchingPackages Package names undergoing a root force-stop + cold-start.
 * @property filteredApps Derived list shown in the UI; reflects [searchQuery], [showUserApps], and
 *   [showSystemApps], with per-item flags mapped from the three package sets.
 * @property searchQuery Current text entered in the inline search field.
 * @property showUserApps Whether user-installed apps are included in [filteredApps].
 * @property showSystemApps Whether system apps are included in [filteredApps].
 * @property isLoading `true` during the initial app-list fetch; shows a full-screen spinner.
 * @property isRefreshing `true` during a user-triggered pull-to-refresh; drives the
 *   pull-to-refresh indicator in the UI.
 * @property isModuleActive `true` when the XposedFakeLocation Xposed service is connected.
 */
@Immutable
data class TargetAppsUiState(
    val apps: List<TargetAppItem> = emptyList(),
    val selectedPackages: Set<String> = emptySet(),
    val pendingPackages: Set<String> = emptySet(),
    val relaunchingPackages: Set<String> = emptySet(),
    val filteredApps: List<TargetAppItem> = emptyList(),
    val searchQuery: String = "",
    val showUserApps: Boolean = true,
    val showSystemApps: Boolean = true,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isModuleActive: Boolean = true
)

/** One-shot messages surfaced to the UI as snackbars. */
sealed interface TargetAppsEvent {
    /** Emitted when the user tries to toggle an app but the Xposed service is not connected. */
    data object ModuleNotActive : TargetAppsEvent

    /**
     * Emitted when a [XposedService.requestScope] or [XposedService.removeScope] call fails.
     * @property message Error detail from the service exception.
     */
    data class ScopeRequestFailed(val message: String) : TargetAppsEvent

    /**
     * Emitted after a successful root force-stop + cold-start of a target app.
     * @property appLabel Human-readable name of the relaunched app.
     */
    data class Relaunched(val appLabel: String) : TargetAppsEvent

    /**
     * Emitted when the root force-stop or cold-start fails.
     * @property appLabel Human-readable name of the app that could not be relaunched.
     */
    data class RelaunchFailed(val appLabel: String) : TargetAppsEvent

    /** Emitted when a relaunch is attempted but root access is not available. */
    data object RootRequired : TargetAppsEvent
}

/**
 * Returns a copy of this list sorted so that packages in [selected] appear first,
 * with both groups sorted alphabetically (case-insensitive) within themselves.
 */
private fun List<TargetAppItem>.sortedBySelection(selected: Set<String>): List<TargetAppItem> =
    sortedWith(
        compareByDescending<TargetAppItem> { selected.contains(it.packageName) }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
    )

/**
 * The LSPosed scope is the source of truth for which apps the module is injected into.
 * Tagging an app issues a [XposedService.requestScope] (asynchronous, may need approval);
 * the checkbox only turns on once the request is approved. Untagging calls
 * [XposedService.removeScope] immediately. The scope is mirrored into the [target_apps]
 * remote pref so the selection is also available to features that hook system framework
 * processes (where a target may be selected without itself being in scope).
 */
class TargetAppsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferencesRepository = PreferencesRepository(application)
    private val packageManager = application.packageManager

    private val _uiState = MutableStateFlow(TargetAppsUiState())
    val uiState: StateFlow<TargetAppsUiState> = _uiState.asStateFlow()

    private val _events = Channel<TargetAppsEvent>(Channel.BUFFERED)
    val events: Flow<TargetAppsEvent> = _events.receiveAsFlow()

    init {
        loadInstalledApps()
        observeService()
    }

    /** Updates [TargetAppsUiState.searchQuery] and recomputes [TargetAppsUiState.filteredApps]. */
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query).recompute() }
    }

    /** Sets whether user-installed apps appear in [TargetAppsUiState.filteredApps]. */
    fun setShowUserApps(show: Boolean) {
        _uiState.update { it.copy(showUserApps = show).recompute() }
    }

    /** Sets whether system apps appear in [TargetAppsUiState.filteredApps]. */
    fun setShowSystemApps(show: Boolean) {
        _uiState.update { it.copy(showSystemApps = show).recompute() }
    }

    /**
     * Adds or removes [packageName] from the LSPosed scope, depending on its current selection
     * state. No-ops if a scope request for [packageName] is already pending. Emits
     * [TargetAppsEvent.ModuleNotActive] if the Xposed service is not connected.
     */
    fun toggleApp(packageName: String) {
        if (_uiState.value.pendingPackages.contains(packageName)) return

        val service = App.service
        if (service == null) {
            _events.trySend(TargetAppsEvent.ModuleNotActive)
            return
        }

        if (_uiState.value.selectedPackages.contains(packageName)) {
            removeFromScope(service, packageName)
        } else {
            addToScope(service, packageName)
        }
    }

    /**
     * Force-stops the target app via root so the module reloads on next launch, then cold-starts
     * it again. Requires the user to grant root (su); failures (e.g. denied prompt) are reported.
     */
    fun relaunchApp(packageName: String) {
        if (_uiState.value.relaunchingPackages.contains(packageName)) return
        val label = _uiState.value.apps.firstOrNull { it.packageName == packageName }?.label ?: packageName

        setRelaunching(packageName, true)
        viewModelScope.launch {
            // Probing su both verifies root and triggers the manager's grant prompt if it was
            // never requested before. A denied prompt / missing su binary returns false.
            val hasRoot = withContext(Dispatchers.IO) { hasRootAccess() }
            if (!hasRoot) {
                setRelaunching(packageName, false)
                _events.trySend(TargetAppsEvent.RootRequired)
                return@launch
            }

            val killed = withContext(Dispatchers.IO) { runAsRoot("am force-stop $packageName") }
            if (!killed) {
                setRelaunching(packageName, false)
                _events.trySend(TargetAppsEvent.RelaunchFailed(label))
                return@launch
            }

            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setRelaunching(packageName, false)
            if (launchIntent != null) {
                getApplication<Application>().startActivity(launchIntent)
                _events.trySend(TargetAppsEvent.Relaunched(label))
            } else {
                _events.trySend(TargetAppsEvent.RelaunchFailed(label))
            }
        }
    }

    /** Verifies (and, on first use, requests) root by running `su -c id` and checking for uid=0. */
    private fun hasRootAccess(): Boolean = try {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor() == 0 && output.contains("uid=0")
    } catch (e: Exception) {
        false
    }

    /** Executes [command] via `su -c` and returns `true` if the process exits with code 0. */
    private fun runAsRoot(command: String): Boolean = try {
        Runtime.getRuntime().exec(arrayOf("su", "-c", command)).waitFor() == 0
    } catch (e: Exception) {
        false
    }

    /**
     * Issues a [XposedService.requestScope] for [packageName] and marks it as pending.
     *
     * On approval, the pending flag is cleared and [refreshScope] re-syncs the selection.
     * On failure, the pending flag is cleared and a [TargetAppsEvent.ScopeRequestFailed] is emitted.
     */
    private fun addToScope(service: XposedService, packageName: String) {
        setPending(packageName, true)

        val callback = object : XposedService.OnScopeEventListener {
            override fun onScopeRequestApproved(approved: List<String>) {
                viewModelScope.launch {
                    setPending(packageName, false)
                    refreshScope()
                }
            }

            override fun onScopeRequestFailed(message: String) {
                viewModelScope.launch {
                    setPending(packageName, false)
                    _events.trySend(TargetAppsEvent.ScopeRequestFailed(message))
                }
            }
        }

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { service.requestScope(listOf(packageName), callback) }
            } catch (e: XposedService.ServiceException) {
                setPending(packageName, false)
                _events.trySend(TargetAppsEvent.ScopeRequestFailed(e.message ?: e.toString()))
            }
        }
    }

    /**
     * Calls [XposedService.removeScope] for [packageName] and re-syncs the scope on success.
     * Emits [TargetAppsEvent.ScopeRequestFailed] if the service call throws.
     */
    private fun removeFromScope(service: XposedService, packageName: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { service.removeScope(listOf(packageName)) }
            } catch (e: XposedService.ServiceException) {
                _events.trySend(TargetAppsEvent.ScopeRequestFailed(e.message ?: e.toString()))
            }
            refreshScope()
        }
    }

    /**
     * Collects [App.serviceState] for the lifetime of the ViewModel and reacts to connect /
     * disconnect events.
     *
     * On disconnect: clears all selection, pending, and relaunching state and marks the module as
     * inactive. On (re)connect: marks the module active and re-reads the LSPosed scope, sorting
     * the app list so selected apps appear first.
     */
    private fun observeService() {
        viewModelScope.launch {
            App.serviceState.collectLatest { service ->
                if (service == null) {
                    _uiState.update { state ->
                        state.copy(
                            isModuleActive = false,
                            selectedPackages = emptySet(),
                            pendingPackages = emptySet(),
                            relaunchingPackages = emptySet()
                        ).recompute()
                    }
                } else {
                    _uiState.update { it.copy(isModuleActive = true) }
                    refreshScope(sortApps = true)
                }
            }
        }
    }

    /**
     * Re-reads the live LSPosed scope and reflects it in the UI and the mirror pref.
     *
     * @param sortApps When `true`, the [TargetAppsUiState.apps] list is re-sorted so selected
     * apps appear first. Pass `true` only on initial load and manual refresh; leave `false`
     * for scope updates triggered by the user toggling an individual app so the list order
     * remains stable during interaction.
     */
    private suspend fun refreshScope(sortApps: Boolean = false) {
        val service = App.service ?: return
        val scope = withContext(Dispatchers.IO) {
            try {
                service.scope.toSet()
            } catch (e: XposedService.ServiceException) {
                _events.trySend(TargetAppsEvent.ScopeRequestFailed(e.message ?: e.toString()))
                _uiState.value.selectedPackages
            }
        }

        _uiState.update { state ->
            val nextApps = if (sortApps && state.apps.isNotEmpty()) state.apps.sortedBySelection(scope)
                           else state.apps
            state.copy(selectedPackages = scope, apps = nextApps).recompute()
        }

        preferencesRepository.saveTargetApps(scope)
    }

    /**
     * Adds or removes [packageName] from [TargetAppsUiState.pendingPackages] and recomputes
     * [TargetAppsUiState.filteredApps].
     */
    private fun setPending(packageName: String, pending: Boolean) {
        _uiState.update { state ->
            val nextPending = state.pendingPackages.toMutableSet().apply {
                if (pending) add(packageName) else remove(packageName)
            }
            state.copy(pendingPackages = nextPending).recompute()
        }
    }

    /**
     * Adds or removes [packageName] from [TargetAppsUiState.relaunchingPackages] and recomputes
     * [TargetAppsUiState.filteredApps].
     */
    private fun setRelaunching(packageName: String, relaunching: Boolean) {
        _uiState.update { state ->
            val nextRelaunching = state.relaunchingPackages.toMutableSet().apply {
                if (relaunching) add(packageName) else remove(packageName)
            }
            state.copy(relaunchingPackages = nextRelaunching).recompute()
        }
    }

    /**
     * Derives [TargetAppsUiState.filteredApps] from the current [TargetAppsUiState.apps],
     * the three package sets, [TargetAppsUiState.searchQuery], [TargetAppsUiState.showUserApps],
     * and [TargetAppsUiState.showSystemApps]. Call at the end of every [_uiState] update
     * that changes any of those seven fields.
     */
    private fun TargetAppsUiState.recompute(): TargetAppsUiState {
        val query = searchQuery.trim()
        val typeFiltered = apps.filter { if (it.isSystemApp) showSystemApps else showUserApps }
        val source = if (query.isEmpty()) typeFiltered else typeFiltered.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
        return copy(
            filteredApps = source.map { app ->
                app.copy(
                    isSelected = selectedPackages.contains(app.packageName),
                    isPending = pendingPackages.contains(app.packageName),
                    isRelaunching = relaunchingPackages.contains(app.packageName)
                )
            }
        )
    }

    /**
     * Queries the [PackageManager] for all apps that declare a launcher [Intent] and returns them
     * as an alphabetically sorted (case-insensitive) list of [TargetAppItem]s. Excludes this
     * manager app. Runs on [Dispatchers.IO].
     */
    private suspend fun fetchInstalledApps(): List<TargetAppItem> = withContext(Dispatchers.IO) {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            .asSequence()
            .map { it.activityInfo.applicationInfo }
            .filter { it.packageName != MANAGER_APP_PACKAGE_NAME }
            .distinctBy { it.packageName }
            .map {
                TargetAppItem(
                    label = it.loadLabel(packageManager).toString(),
                    packageName = it.packageName,
                    isSystemApp = (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
    }

    /**
     * Fetches the installed app list via [fetchInstalledApps] and stores it in
     * [TargetAppsUiState.apps]. Sorts by whatever [TargetAppsUiState.selectedPackages] are already
     * known so selected apps appear first; if the scope has not yet been read the order is purely
     * alphabetical and [observeService] will re-sort once the scope arrives.
     */
    private fun loadInstalledApps() {
        viewModelScope.launch {
            val fetched = fetchInstalledApps()
            _uiState.update { state ->
                // Sort by whatever selected packages are already known (may be empty if scope
                // hasn't arrived yet; refreshScope with sortApps=true will re-sort once it does).
                state.copy(apps = fetched.sortedBySelection(state.selectedPackages), isLoading = false).recompute()
            }
        }
    }

    /** Re-fetches the installed app list and re-reads the LSPosed scope. */
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                _uiState.update { state -> state.copy(apps = fetchInstalledApps()).recompute() }
                refreshScope(sortApps = true)
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }
}
