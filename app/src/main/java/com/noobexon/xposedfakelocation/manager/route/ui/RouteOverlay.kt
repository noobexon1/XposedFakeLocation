package com.noobexon.xposedfakelocation.manager.route.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.manager.joystick.displayKmh
import com.noobexon.xposedfakelocation.manager.overlay.rememberOverlayDragModifier
import com.noobexon.xposedfakelocation.manager.route.RouteFormat
import com.noobexon.xposedfakelocation.manager.route.RoutePhase
import com.noobexon.xposedfakelocation.manager.route.RouteStatus

/**
 * Music-player style controller for a playing route: an expanded card or a progress bubble.
 *
 * @param status Latest playback status; a lambda so only the parts that read it recompose per tick.
 */
@Composable
fun RouteOverlay(
    routeName: String,
    pointCount: Int,
    collapsed: Boolean,
    status: () -> RouteStatus,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Float) -> Unit,
    onCycleMultiplier: () -> Unit,
    onCollapsedChange: (Boolean) -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    if (collapsed) {
        RouteBubble(
            progress = { status().progress },
            onExpand = { onCollapsedChange(false) },
            onDrag = onDrag,
            onDragEnd = onDragEnd,
        )
        return
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.width(288.dp).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.Default.DragIndicator,
                    contentDescription = stringResource(R.string.cd_route_drag),
                    modifier = Modifier.size(32.dp).then(rememberOverlayDragModifier(onDrag, onDragEnd)),
                )
                Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(
                        text = routeName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    StatusLine(status = status, pointCount = pointCount)
                }
                IconButton(onClick = { onCollapsedChange(true) }) {
                    Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.cd_route_collapse))
                }
            }
            ProgressSeekBar(status = status, onSeek = onSeek)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                PlayPauseButton(phase = { status().phase }, onClick = onPlayPause)
                IconButton(onClick = onStop) {
                    Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.cd_route_stop))
                }
                MultiplierButton(multiplier = { status().multiplier }, onClick = onCycleMultiplier)
                Spacer(Modifier.weight(1f))
                SpeedReadout(speedMps = { status().speedMps })
            }
        }
    }
}

@Composable
private fun StatusLine(status: () -> RouteStatus, pointCount: Int) {
    val s = status()
    val text = when {
        s.phase == RoutePhase.FINISHED -> stringResource(R.string.route_status_finished)
        s.waitRemainingS > 0 -> stringResource(R.string.route_status_waiting, RouteFormat.clock(s.waitRemainingS))
        s.phase == RoutePhase.PAUSED -> stringResource(R.string.route_status_paused)
        else -> stringResource(R.string.route_status_point, s.pointNumber, pointCount)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

/** Shows the drag position while seeking and applies [onSeek] only on release. */
@Composable
private fun ProgressSeekBar(status: () -> RouteStatus, onSeek: (Float) -> Unit) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val s = status()
    Slider(
        value = dragFraction ?: s.progress,
        onValueChange = { dragFraction = it },
        onValueChangeFinished = {
            dragFraction?.let(onSeek)
            dragFraction = null
        },
        modifier = Modifier.fillMaxWidth(),
    )
    val shownMeters = dragFraction?.let { it * s.totalDistanceM } ?: s.distanceCoveredM
    Text(
        text = stringResource(R.string.route_distance_progress, RouteFormat.km(shownMeters), RouteFormat.km(s.totalDistanceM)),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PlayPauseButton(phase: () -> RoutePhase, onClick: () -> Unit) {
    val (icon, description) = when (phase()) {
        RoutePhase.RUNNING -> Icons.Default.Pause to R.string.cd_route_pause
        RoutePhase.PAUSED -> Icons.Default.PlayArrow to R.string.cd_route_play
        RoutePhase.FINISHED -> Icons.Default.Replay to R.string.cd_route_replay
    }
    FilledIconButton(onClick = onClick) {
        Icon(icon, contentDescription = stringResource(description))
    }
}

@Composable
private fun MultiplierButton(multiplier: () -> Float, onClick: () -> Unit) {
    val label = RouteFormat.multiplier(multiplier())
    val description = stringResource(R.string.cd_route_multiplier, label)
    TextButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = description }) {
        Text(stringResource(R.string.route_multiplier_label, label), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SpeedReadout(speedMps: () -> Float) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = displayKmh(speedMps()).toString(),
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.unit_kilometers_per_hour),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 3.dp),
        )
    }
}

@Composable
private fun RouteBubble(progress: () -> Float, onExpand: () -> Unit, onDrag: (Float, Float) -> Unit, onDragEnd: () -> Unit) {
    val expandDescription = stringResource(R.string.cd_route_expand)
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 6.dp,
        modifier = Modifier
            .size(56.dp)
            .then(rememberOverlayDragModifier(onDrag, onDragEnd, onTap = onExpand))
            .semantics {
                contentDescription = expandDescription
                role = Role.Button
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxSize().padding(4.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
            )
            Icon(Icons.Default.Route, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}
