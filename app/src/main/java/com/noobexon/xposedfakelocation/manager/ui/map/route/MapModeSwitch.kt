package com.noobexon.xposedfakelocation.manager.ui.map.route

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.model.MapMode

/** Location / Route switch floating over the map; locked while anything plays. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapModeSwitch(mode: MapMode, enabled: Boolean, onModeChange: (MapMode) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shadowElevation = 4.dp,
        modifier = modifier,
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(4.dp)) {
            MapMode.entries.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = mode == entry,
                    onClick = { onModeChange(entry) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index, MapMode.entries.size),
                    icon = {
                        Icon(
                            imageVector = if (entry == MapMode.LOCATION) Icons.Default.Place else Icons.Default.Route,
                            contentDescription = null,
                        )
                    },
                ) {
                    Text(stringResource(if (entry == MapMode.LOCATION) R.string.map_mode_location else R.string.map_mode_route))
                }
            }
        }
    }
}
