package com.noobexon.xposedfakelocation.manager.joystick.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.manager.joystick.displayKmh
import com.noobexon.xposedfakelocation.manager.overlay.rememberOverlayDragModifier

/**
 * Root of the floating overlay: an expanded control panel or a collapsed bubble.
 *
 * @param speedMps Current speed; a lambda so only the speed readout recomposes on every tick.
 */
@Composable
fun JoystickOverlay(
    collapsed: Boolean,
    speedMps: () -> Float,
    onInput: (x: Float, y: Float) -> Unit,
    onRelease: () -> Unit,
    onCollapsedChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    if (collapsed) {
        CollapsedBubble(onExpand = { onCollapsedChange(false) }, onDrag = onDrag, onDragEnd = onDragEnd)
    } else {
        ExpandedPanel(
            speedMps = speedMps,
            onInput = onInput,
            onRelease = onRelease,
            onCollapse = {
                onRelease()
                onCollapsedChange(true)
            },
            onClose = onClose,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
        )
    }
}

@Composable
private fun ExpandedPanel(
    speedMps: () -> Float,
    onInput: (x: Float, y: Float) -> Unit,
    onRelease: () -> Unit,
    onCollapse: () -> Unit,
    onClose: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.width(232.dp).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.Default.DragIndicator,
                    contentDescription = stringResource(R.string.cd_joystick_drag),
                    modifier = Modifier.size(32.dp).then(rememberOverlayDragModifier(onDrag, onDragEnd)),
                )
                SpeedReadout(speedMps = speedMps, modifier = Modifier.weight(1f))
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.cd_joystick_collapse))
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_joystick_close))
                }
            }
            Spacer(Modifier.height(8.dp))
            JoystickPad(onInput = onInput, onRelease = onRelease)
        }
    }
}

/** Car-style digital speedometer: whole km/h with the unit beside it. */
@Composable
private fun SpeedReadout(speedMps: () -> Float, modifier: Modifier = Modifier) {
    val kmh = displayKmh(speedMps())
    val description = stringResource(R.string.cd_joystick_speed, kmh)
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        Text(
            text = kmh.toString(),
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.alignByBaseline(),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.unit_kilometers_per_hour),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

@Composable
private fun CollapsedBubble(onExpand: () -> Unit, onDrag: (Float, Float) -> Unit, onDragEnd: () -> Unit) {
    val expandDescription = stringResource(R.string.cd_joystick_expand)
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
            Icon(Icons.Default.SportsEsports, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

/**
 * Circular stick. Emits deflection in `[-1, 1]` with positive y = up (north) while touched and
 * calls [onRelease] when the finger lifts.
 */
@Composable
fun JoystickPad(
    onInput: (x: Float, y: Float) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 160.dp,
) {
    val currentOnInput by rememberUpdatedState(onInput)
    val currentOnRelease by rememberUpdatedState(onRelease)
    var thumb by remember { mutableStateOf(Offset.Zero) }
    val baseColor = MaterialTheme.colorScheme.surfaceVariant
    val ringColor = MaterialTheme.colorScheme.outline
    val thumbColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .size(diameter)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val radius = size.width / 2f
                    val travel = radius * (1f - THUMB_RATIO)
                    val center = Offset(radius, size.height / 2f)
                    fun update(position: Offset) {
                        thumb = clampToRadius(position - center, travel)
                        currentOnInput(thumb.x / travel, -thumb.y / travel)
                    }

                    val down = awaitFirstDown()
                    update(down.position)
                    drag(down.id) { change ->
                        change.consume()
                        update(change.position)
                    }
                    thumb = Offset.Zero
                    currentOnRelease()
                }
            }
            .drawBehind {
                val radius = size.minDimension / 2f
                drawCircle(baseColor.copy(alpha = 0.85f), radius)
                drawCircle(ringColor, radius, style = Stroke(width = 2.dp.toPx()))
                drawCircle(thumbColor, radius * THUMB_RATIO, center = center + thumb)
            }
    )
}

private const val THUMB_RATIO = 0.35f

private fun clampToRadius(offset: Offset, radius: Float): Offset {
    val distance = offset.getDistance()
    return if (distance <= radius) offset else offset * (radius / distance)
}