package com.noobexon.xposedfakelocation.manager.ui.map.route

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.ROUTE_MIN_POINTS
import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.manager.route.RoutePlaybackStatus
import com.noobexon.xposedfakelocation.manager.route.RouteSummary
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Bottom card for Route mode: summary, default speed, Loop/Reverse, play button and an expandable
 * reorderable point list. Read-only (with progress) while [editable] is false.
 */
@Composable
fun RoutePanel(
    route: Route,
    summary: RouteSummary,
    editable: Boolean,
    playback: RoutePlaybackStatus?,
    playButton: @Composable () -> Unit,
    onDefaultSpeedClick: () -> Unit,
    onLoopChange: (Boolean) -> Unit,
    onReversedChange: (Boolean) -> Unit,
    onOrderChange: (List<String>) -> Unit,
    onDeletePoint: (String) -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = summaryText(summary),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_route_options))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.route_save_to_favorites)) },
                            enabled = route.points.size >= ROUTE_MIN_POINTS,
                            onClick = { menuOpen = false; onSave() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.route_load_from_favorites)) },
                            enabled = editable,
                            onClick = { menuOpen = false; onLoad() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.route_clear)) },
                            enabled = editable && route.points.isNotEmpty(),
                            onClick = { menuOpen = false; onClear() },
                        )
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        contentDescription = stringResource(if (expanded) R.string.cd_route_hide_points else R.string.cd_route_show_points),
                    )
                }
                playButton()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = onDefaultSpeedClick,
                    enabled = editable,
                    label = { Text(stringResource(R.string.route_default_speed, speedChoiceText(route.defaultSpeed))) },
                )
                FilterChip(
                    selected = route.loop,
                    onClick = { onLoopChange(!route.loop) },
                    enabled = editable,
                    label = { Text(stringResource(R.string.route_loop)) },
                )
                FilterChip(
                    selected = route.reversed,
                    onClick = { onReversedChange(!route.reversed) },
                    enabled = editable,
                    label = { Text(stringResource(R.string.route_reverse)) },
                )
            }
            playback?.let { status ->
                LinearProgressIndicator(
                    progress = { status.status.progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
            AnimatedVisibility(visible = expanded) {
                PointList(route = route, editable = editable, onOrderChange = onOrderChange, onDeletePoint = onDeletePoint)
            }
        }
    }
}

@Composable
private fun summaryText(summary: RouteSummary): String = when {
    summary.pointCount == 0 -> stringResource(R.string.route_empty_hint)
    summary.pointCount < ROUTE_MIN_POINTS -> stringResource(R.string.route_needs_two_points)
    else -> stringResource(R.string.route_summary, summary.pointCount, distanceText(summary.distanceM), durationText(summary.durationS))
}

/** Reorders locally while dragging and commits the new order once, when the drag stops. */
@Composable
private fun PointList(route: Route, editable: Boolean, onOrderChange: (List<String>) -> Unit, onDeletePoint: (String) -> Unit) {
    var order by remember(route.points) { mutableStateOf(route.points) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        order = order.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
        items(order, key = { it.id }) { point ->
            ReorderableItem(reorderState, key = point.id) {
                val number = order.indexOf(point) + 1
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        enabled = editable,
                        onClick = {},
                        modifier = Modifier.draggableHandle(
                            enabled = editable,
                            onDragStopped = { onOrderChange(order.map { it.id }) },
                        ),
                    ) {
                        Icon(Icons.Default.DragHandle, contentDescription = stringResource(R.string.cd_route_drag_handle, number))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.route_point_label, number), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = stringResource(R.string.coordinates_lat_lon, "%.5f".format(point.latitude), "%.5f".format(point.longitude)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(enabled = editable, onClick = { onDeletePoint(point.id) }) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.cd_route_delete_point, number),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
