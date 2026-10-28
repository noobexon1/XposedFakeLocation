package com.noobexon.xposedfakelocation.manager.ui.map.route

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.ROUTE_MAX_DWELL_SECONDS
import com.noobexon.xposedfakelocation.data.ROUTE_MAX_VARIATION_PERCENT
import com.noobexon.xposedfakelocation.data.model.SegmentSettings
import kotlin.math.roundToInt

private const val VARIATION_STEP_PERCENT = 5

/** Per-segment speed, wait and variation; every change applies immediately. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentSheet(
    fromNumber: Int,
    toNumber: Int,
    settings: SegmentSettings,
    onChange: (SegmentSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp),
        ) {
            Text(stringResource(R.string.route_segment_title, fromNumber, toNumber), style = MaterialTheme.typography.titleMedium)
            SpeedChoicePicker(choice = settings.speed, allowDefault = true, onChange = { onChange(settings.copy(speed = it)) })
            var waitText by remember { mutableStateOf(settings.dwellSeconds.toString()) }
            val wait = waitText.toIntOrNull()
            OutlinedTextField(
                value = waitText,
                onValueChange = {
                    waitText = it
                    it.toIntOrNull()?.takeIf { s -> s in 0..ROUTE_MAX_DWELL_SECONDS }?.let { s -> onChange(settings.copy(dwellSeconds = s)) }
                },
                label = { Text(stringResource(R.string.route_wait_label)) },
                isError = wait == null || wait !in 0..ROUTE_MAX_DWELL_SECONDS,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.route_variation_label, settings.variationPercent))
            Slider(
                value = settings.variationPercent.toFloat(),
                onValueChange = {
                    val stepped = (it / VARIATION_STEP_PERCENT).roundToInt() * VARIATION_STEP_PERCENT
                    if (stepped != settings.variationPercent) onChange(settings.copy(variationPercent = stepped))
                },
                valueRange = 0f..ROUTE_MAX_VARIATION_PERCENT.toFloat(),
                steps = ROUTE_MAX_VARIATION_PERCENT / VARIATION_STEP_PERCENT - 1,
            )
        }
    }
}

/** Actions for one tapped point. Each action also closes the sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointActionsSheet(
    number: Int,
    count: Int,
    onDelete: () -> Unit,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onSetNumber: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.route_point_label, number),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            ActionRow(Icons.Default.ArrowUpward, R.string.route_move_earlier, enabled = number > 1, onClick = onMoveEarlier)
            ActionRow(Icons.Default.ArrowDownward, R.string.route_move_later, enabled = number < count, onClick = onMoveLater)
            ActionRow(Icons.Default.FormatListNumbered, R.string.route_set_number, enabled = count > 1, onClick = onSetNumber)
            ActionRow(Icons.Outlined.Delete, R.string.route_delete_point, enabled = true, onClick = onDelete)
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: Int, enabled: Boolean, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.38f
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)) },
        headlineContent = { Text(stringResource(label), color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)) },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}
