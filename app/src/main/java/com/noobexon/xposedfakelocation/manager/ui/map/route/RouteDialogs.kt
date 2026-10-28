package com.noobexon.xposedfakelocation.manager.ui.map.route

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.model.SpeedChoice

/** Asks for a name; offers Update / Save as new when the draft came from a saved route. */
@Composable
fun SaveRouteDialog(initialName: String, canUpdate: Boolean, onSave: (name: String, asNew: Boolean) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    val nameError = name.isBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.route_save_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.field_name)) },
                isError = nameError,
                supportingText = if (nameError) {
                    { Text(stringResource(R.string.validation_name_required)) }
                } else null,
                singleLine = true,
            )
        },
        confirmButton = {
            Row {
                if (canUpdate) {
                    TextButton(enabled = !nameError, onClick = { onSave(name, false) }) {
                        Text(stringResource(R.string.route_save_update))
                    }
                }
                TextButton(enabled = !nameError, onClick = { onSave(name, true) }) {
                    Text(stringResource(if (canUpdate) R.string.route_save_as_new else R.string.action_save))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun SetNumberDialog(count: Int, current: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current.toString()) }
    val number = text.toIntOrNull()?.takeIf { it in 1..count }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.route_set_number_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.route_set_number_label, count)) },
                isError = number == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(enabled = number != null, onClick = { number?.let(onConfirm) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun DefaultSpeedDialog(choice: SpeedChoice, onChange: (SpeedChoice) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.route_default_speed_title)) },
        text = { SpeedChoicePicker(choice = choice, allowDefault = false, onChange = { it?.let(onChange) }) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } },
    )
}

@Composable
fun ConfirmDialog(
    @StringRes title: Int,
    @StringRes message: Int,
    @StringRes confirmLabel: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(confirmLabel)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
