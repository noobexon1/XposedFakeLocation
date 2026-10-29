package com.noobexon.xposedfakelocation.manager.ui.map.route

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.ROUTE_MIN_POINTS
import com.noobexon.xposedfakelocation.data.model.SpeedChoice
import com.noobexon.xposedfakelocation.data.model.effectiveSpeed
import com.noobexon.xposedfakelocation.data.model.followsRoads
import com.noobexon.xposedfakelocation.data.model.roadProfile
import com.noobexon.xposedfakelocation.data.model.segmentKey
import com.noobexon.xposedfakelocation.data.model.segmentSettings
import com.noobexon.xposedfakelocation.data.model.travelSegments
import com.noobexon.xposedfakelocation.manager.route.RoutePlayButton
import com.noobexon.xposedfakelocation.manager.route.missingRoadSegmentCount
import com.noobexon.xposedfakelocation.manager.route.roadStatus

/** Route mode's bottom panel plus every route sheet and dialog, wired to [viewModel]. */
@Composable
fun BoxScope.RouteModeUi(
    viewModel: RouteEditorViewModel,
    isPlaying: Boolean,
    onStopSpoofing: () -> Unit,
    onLoadFromFavorites: () -> Unit,
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val joystickRunning by viewModel.joystickRunning.collectAsStateWithLifecycle()
    val savedRoutes by viewModel.savedRoutes.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val roadFailures by viewModel.roadFailures.collectAsStateWithLifecycle()
    val route = draft.route
    val editable = !isPlaying && playback == null
    val blockedReason = when {
        route.points.size < ROUTE_MIN_POINTS -> R.string.route_needs_two_points
        joystickRunning -> R.string.route_joystick_running
        else -> null
    }

    RoutePanel(
        route = route,
        summary = summary,
        editable = editable,
        playback = playback,
        playButton = {
            RoutePlayButton(
                blockedReason = blockedReason,
                routePlaying = playback != null,
                otherSpoofingActive = isPlaying && playback == null,
                onStopSpoofing = onStopSpoofing,
                missingRoadSegments = remember(route) { route.missingRoadSegmentCount() },
            )
        },
        onDefaultSpeedClick = { viewModel.showDefaultSpeedDialog(true) },
        onLoopChange = viewModel::setLoop,
        onReversedChange = viewModel::setReversed,
        onFollowRoadsChange = viewModel::setFollowRoads,
        onOrderChange = viewModel::setOrder,
        onDeletePoint = viewModel::deletePoint,
        onSave = { viewModel.showSaveDialog(true) },
        onLoad = onLoadFromFavorites,
        onClear = { viewModel.showClearDialog(true) },
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
    )

    ui.pointActionsId?.takeIf { editable }?.let { id ->
        val index = route.points.indexOfFirst { it.id == id }
        if (index >= 0) {
            val close = { viewModel.showPointActions(null) }
            PointActionsSheet(
                number = index + 1,
                count = route.points.size,
                onDelete = { viewModel.deletePoint(id); close() },
                onMoveEarlier = { viewModel.moveEarlier(id); close() },
                onMoveLater = { viewModel.moveLater(id); close() },
                onSetNumber = { viewModel.showSetNumberDialog(id) },
                onDismiss = close,
            )
        }
    }

    ui.setNumberPointId?.let { id ->
        val index = route.points.indexOfFirst { it.id == id }
        if (index >= 0) {
            SetNumberDialog(
                count = route.points.size,
                current = index + 1,
                onConfirm = { viewModel.setPointNumber(id, it) },
                onDismiss = { viewModel.showSetNumberDialog(null) },
            )
        }
    }

    ui.segment?.takeIf { editable }?.let { (a, b) ->
        val segment = route.travelSegments().firstOrNull { segmentKey(it.from.id, it.to.id) == segmentKey(a, b) }
        if (segment != null) {
            SegmentSheet(
                fromNumber = segment.fromNumber,
                toNumber = segment.toNumber,
                settings = route.segmentSettings(a, b),
                roadStatus = route.roadStatus(segment.from, segment.to, roadFailures),
                profile = route.effectiveSpeed(a, b)
                    .takeIf { it is SpeedChoice.Custom && route.followsRoads(a, b) }
                    ?.roadProfile(),
                onChange = { viewModel.updateSegment(a, b, it) },
                onProfileChange = { viewModel.setSegmentProfile(a, b, it) },
                onRetry = { viewModel.retryRoad(a, b) },
                onDismiss = { viewModel.showSegment(null) },
            )
        }
    }

    if (ui.showDefaultSpeedDialog) {
        DefaultSpeedDialog(
            choice = route.defaultSpeed,
            followRoads = route.followRoads,
            onChange = viewModel::setDefaultSpeed,
            onDismiss = { viewModel.showDefaultSpeedDialog(false) },
        )
    }

    if (ui.showSaveDialog) {
        val sourceRoute = draft.sourceRouteId?.let { id -> savedRoutes.firstOrNull { it.id == id } }
        SaveRouteDialog(
            initialName = sourceRoute?.name ?: route.name,
            canUpdate = sourceRoute != null,
            onSave = viewModel::saveRoute,
            onDismiss = { viewModel.showSaveDialog(false) },
        )
    }

    if (ui.showClearDialog) {
        ConfirmDialog(
            title = R.string.route_clear,
            message = R.string.route_clear_message,
            confirmLabel = R.string.route_clear,
            onConfirm = viewModel::clearRoute,
            onDismiss = { viewModel.showClearDialog(false) },
        )
    }
}

/** "Replace current route?" for a favorite picked while the draft has unsaved edits. */
@Composable
fun RouteLoadConfirmation(viewModel: RouteEditorViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    if (ui.pendingLoad != null) {
        ConfirmDialog(
            title = R.string.route_replace_title,
            message = R.string.route_replace_message,
            confirmLabel = R.string.route_replace_confirm,
            onConfirm = viewModel::confirmLoad,
            onDismiss = viewModel::cancelLoad,
        )
    }
}
