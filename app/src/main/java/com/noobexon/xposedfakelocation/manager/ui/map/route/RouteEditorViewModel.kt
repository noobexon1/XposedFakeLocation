package com.noobexon.xposedfakelocation.manager.ui.map.route

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noobexon.xposedfakelocation.data.model.MapMode
import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.data.model.RouteDraft
import com.noobexon.xposedfakelocation.data.model.SegmentSettings
import com.noobexon.xposedfakelocation.data.model.SpeedChoice
import com.noobexon.xposedfakelocation.data.repository.PreferencesRepository
import com.noobexon.xposedfakelocation.manager.joystick.JoystickRunningState
import com.noobexon.xposedfakelocation.manager.route.RouteEditor
import com.noobexon.xposedfakelocation.manager.route.RoutePlaybackState
import com.noobexon.xposedfakelocation.manager.route.RoutePlaybackStatus
import com.noobexon.xposedfakelocation.manager.route.RouteSummary
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.osmdroid.util.GeoPoint
import java.util.UUID

/** Which route sheet or dialog is open on the map. */
@Immutable
data class RouteEditorUiState(
    val pointActionsId: String? = null,
    val setNumberPointId: String? = null,
    val segment: Pair<String, String>? = null,
    val showDefaultSpeedDialog: Boolean = false,
    val showSaveDialog: Boolean = false,
    val showClearDialog: Boolean = false,
    /** A favorite route waiting for "Replace current route?" confirmation. */
    val pendingLoad: Route? = null,
)

/**
 * Owns the map's mode and route draft. Only this ViewModel writes the draft, so it keeps the
 * current value in memory and persists every edit.
 */
class RouteEditorViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PreferencesRepository(application)
    private fun newId() = UUID.randomUUID().toString()

    private val _mapMode = MutableStateFlow(repository.getMapMode())
    val mapMode: StateFlow<MapMode> = _mapMode.asStateFlow()

    private val _draft = MutableStateFlow(repository.getRouteDraft())
    val draft: StateFlow<RouteDraft> = _draft.asStateFlow()

    val savedRoutes: StateFlow<List<Route>> =
        repository.getRoutesFlow().stateIn(viewModelScope, SharingStarted.Eagerly, repository.getRoutes())

    private val joystickSettings =
        repository.getJoystickSettingsFlow().stateIn(viewModelScope, SharingStarted.Eagerly, repository.getJoystickSettings())

    val summary: StateFlow<RouteSummary> = combine(_draft, joystickSettings) { d, s -> RouteSummary.of(d.route, s) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RouteSummary.of(_draft.value.route, joystickSettings.value))

    val playback: StateFlow<RoutePlaybackStatus?> = RoutePlaybackState.current
    val joystickRunning: StateFlow<Boolean> = JoystickRunningState.running

    private val _ui = MutableStateFlow(RouteEditorUiState())
    val ui: StateFlow<RouteEditorUiState> = _ui.asStateFlow()

    private val _fitRouteEvent = Channel<Unit>(Channel.BUFFERED)
    /** Asks the map to frame the whole draft, e.g. after loading a favorite. */
    val fitRouteEvent: Flow<Unit> = _fitRouteEvent.receiveAsFlow()

    private var openRoutesTabRequested = false

    fun setMapMode(mode: MapMode) {
        _mapMode.value = mode
        repository.saveMapMode(mode)
    }

    private fun edit(transform: (Route) -> Route) {
        val current = _draft.value
        val updated = current.copy(route = transform(current.route), dirty = true)
        _draft.value = updated
        repository.saveRouteDraft(updated)
    }

    fun addPoint(point: GeoPoint) = edit { RouteEditor.append(it, point.latitude, point.longitude, newId()) }
    fun movePoint(id: String, point: GeoPoint) = edit { RouteEditor.move(it, id, point.latitude, point.longitude) }
    fun deletePoint(id: String) = edit { RouteEditor.delete(it, id) }
    fun moveEarlier(id: String) = edit { RouteEditor.moveEarlier(it, id) }
    fun moveLater(id: String) = edit { RouteEditor.moveLater(it, id) }
    fun setOrder(ids: List<String>) = edit { RouteEditor.setOrder(it, ids) }
    fun updateSegment(aId: String, bId: String, settings: SegmentSettings) = edit { RouteEditor.setSegment(it, aId, bId, settings) }
    fun setDefaultSpeed(choice: SpeedChoice) = edit { it.copy(defaultSpeed = choice) }
    fun setLoop(loop: Boolean) = edit { it.copy(loop = loop) }
    fun setReversed(reversed: Boolean) = edit { it.copy(reversed = reversed) }

    fun setPointNumber(id: String, number: Int) {
        edit { RouteEditor.moveTo(it, id, number) }
        showSetNumberDialog(null)
    }

    fun showPointActions(id: String?) = _ui.update { it.copy(pointActionsId = id) }
    fun showSetNumberDialog(id: String?) = _ui.update { it.copy(setNumberPointId = id, pointActionsId = null) }
    fun showSegment(segment: Pair<String, String>?) = _ui.update { it.copy(segment = segment) }
    fun showDefaultSpeedDialog(show: Boolean) = _ui.update { it.copy(showDefaultSpeedDialog = show) }
    fun showSaveDialog(show: Boolean) = _ui.update { it.copy(showSaveDialog = show) }
    fun showClearDialog(show: Boolean) = _ui.update { it.copy(showClearDialog = show) }

    fun clearRoute() {
        replaceDraft(RouteDraft(Route(id = newId())))
        showClearDialog(false)
    }

    /** Saves the draft to favorites; [asNew] (or a draft not loaded from a favorite) gets a fresh id. */
    fun saveRoute(name: String, asNew: Boolean) {
        val current = _draft.value
        val id = current.sourceRouteId.takeUnless { asNew } ?: newId()
        val saved = RouteEditor.pruneSegments(current.route).copy(id = id, name = name.trim())
        repository.saveRoute(saved)
        replaceDraft(RouteDraft(route = saved, sourceRouteId = id, dirty = false))
        showSaveDialog(false)
    }

    /** Loads a favorite, asking first when that would discard unsaved edits. */
    fun requestLoadRoute(route: Route) {
        val current = _draft.value
        val wouldLoseEdits = current.dirty && current.route.points.isNotEmpty() && current.sourceRouteId != route.id
        if (wouldLoseEdits) _ui.update { it.copy(pendingLoad = route) } else loadRoute(route)
    }

    fun confirmLoad() {
        _ui.value.pendingLoad?.let(::loadRoute)
    }

    fun cancelLoad() = _ui.update { it.copy(pendingLoad = null) }

    fun requestOpenRoutesTab() {
        openRoutesTabRequested = true
    }

    /** Whether the Favorites screen should open on its Routes tab; consumed once. */
    fun consumeOpenRoutesTabRequest(): Boolean {
        val requested = openRoutesTabRequested
        openRoutesTabRequested = false
        return requested
    }

    private fun loadRoute(route: Route) {
        replaceDraft(RouteDraft(route = route, sourceRouteId = route.id, dirty = false))
        // Mode switches are locked while anything plays.
        if (!repository.getIsPlaying() && playback.value == null) setMapMode(MapMode.ROUTE)
        _ui.update { it.copy(pendingLoad = null) }
        _fitRouteEvent.trySend(Unit)
    }

    private fun replaceDraft(draft: RouteDraft) {
        _draft.value = draft
        repository.saveRouteDraft(draft)
    }
}
