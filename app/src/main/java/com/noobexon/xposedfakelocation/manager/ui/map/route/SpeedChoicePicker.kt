package com.noobexon.xposedfakelocation.manager.ui.map.route

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.JOYSTICK_MAX_SPEED_MPS
import com.noobexon.xposedfakelocation.data.JOYSTICK_MIN_SPEED_MPS
import com.noobexon.xposedfakelocation.data.MPS_TO_KMH
import com.noobexon.xposedfakelocation.data.model.JoystickPreset
import com.noobexon.xposedfakelocation.data.model.SpeedChoice
import com.noobexon.xposedfakelocation.manager.joystick.displayKmh
import com.noobexon.xposedfakelocation.manager.ui.settings.labelRes

private const val DEFAULT_CUSTOM_KMH = 10f

/**
 * Walk / Run / Bike / Drive / Custom chips, plus "Default" when [allowDefault]. `null` means the
 * route default. Custom speeds are entered in km/h and applied once they're in range.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeedChoicePicker(choice: SpeedChoice?, allowDefault: Boolean, onChange: (SpeedChoice?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (allowDefault) {
                FilterChip(
                    selected = choice == null,
                    onClick = { onChange(null) },
                    label = { Text(stringResource(R.string.route_speed_default)) },
                )
            }
            JoystickPreset.entries.forEach { preset ->
                FilterChip(
                    selected = choice == SpeedChoice.Preset(preset),
                    onClick = { onChange(SpeedChoice.Preset(preset)) },
                    label = { Text(stringResource(preset.labelRes)) },
                )
            }
            FilterChip(
                selected = choice is SpeedChoice.Custom,
                onClick = { if (choice !is SpeedChoice.Custom) onChange(SpeedChoice.Custom(DEFAULT_CUSTOM_KMH / MPS_TO_KMH)) },
                label = { Text(stringResource(R.string.route_speed_custom)) },
            )
        }
        if (choice is SpeedChoice.Custom) {
            var text by remember { mutableStateOf(displayKmh(choice.speedMps).toString()) }
            val range = JOYSTICK_MIN_SPEED_MPS * MPS_TO_KMH..JOYSTICK_MAX_SPEED_MPS * MPS_TO_KMH
            val parsed = text.toFloatOrNull()
            val valid = parsed != null && parsed in range
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    val kmh = it.toFloatOrNull()
                    if (kmh != null && kmh in range) onChange(SpeedChoice.Custom(kmh / MPS_TO_KMH))
                },
                label = { Text(stringResource(R.string.route_speed_custom_label)) },
                isError = !valid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
