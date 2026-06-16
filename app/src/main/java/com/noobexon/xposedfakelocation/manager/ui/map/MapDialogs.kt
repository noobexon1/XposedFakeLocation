package com.noobexon.xposedfakelocation.manager.ui.map

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.noobexon.xposedfakelocation.R
import compose.icons.LineAwesomeIcons
import compose.icons.lineawesomeicons.RouteSolid

/**
 * Stateless dialog that lets the user jump the camera (and spoof marker) to an arbitrary
 * coordinate by entering latitude and longitude manually.
 *
 * The dialog is fully controlled: it holds no state of its own. All input values and error
 * annotations come from [MapUiState.goToPointState] (via [MapScreen]), and all mutations are
 * forwarded through callbacks to [MapViewModel].
 *
 * Validation runs only when the user confirms (via [onConfirm]); inline error messages are shown
 * beneath each field when the corresponding `*ErrorRes` parameter is non-null.
 *
 * @param latitude Current text value of the latitude field.
 * @param longitude Current text value of the longitude field.
 * @param latitudeErrorRes String resource for the latitude validation error, or `null` if valid.
 * @param longitudeErrorRes String resource for the longitude validation error, or `null` if valid.
 * @param onLatitudeChange Called on every keystroke in the latitude field.
 * @param onLongitudeChange Called on every keystroke in the longitude field.
 * @param onConfirm Called when the user taps "Go"; triggers validation in [MapViewModel].
 * @param onDismissRequest Called when the dialog is dismissed (back gesture, scrim tap, or
 *   "Cancel" button).
 */
@Composable
fun GoToPointDialog(
    latitude: String,
    longitude: String,
    @StringRes latitudeErrorRes: Int?,
    @StringRes longitudeErrorRes: Int?,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.map_go_to_point)) },
        text = {
            Column {
                CoordinateInputField(
                    value = latitude,
                    onValueChange = onLatitudeChange,
                    label = stringResource(R.string.field_latitude),
                    errorRes = latitudeErrorRes,
                )
                Spacer(modifier = Modifier.height(8.dp))
                CoordinateInputField(
                    value = longitude,
                    onValueChange = onLongitudeChange,
                    label = stringResource(R.string.field_longitude),
                    errorRes = longitudeErrorRes,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_go))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/**
 * Stateless dialog that lets the user save the current spoof-target location as a named favourite.
 *
 * Like [GoToPointDialog], it is fully controlled: latitude and longitude are pre-filled from the
 * currently placed marker (done by [MapViewModel.showAddToFavoritesDialog]) so the user only
 * needs to enter a name. All values and errors flow down from [MapUiState.addToFavoritesState];
 * all mutations are forwarded via callbacks.
 *
 * The name field performs live validation (error appears as soon as the field is cleared). The
 * description field is optional and has no validation. Coordinate fields are validated only on
 * confirmation.
 *
 * @param name Current text value of the name field.
 * @param description Current text value of the optional description field.
 * @param latitude Current text value of the latitude field.
 * @param longitude Current text value of the longitude field.
 * @param nameErrorRes String resource for the name validation error, or `null` if valid.
 * @param latitudeErrorRes String resource for the latitude validation error, or `null` if valid.
 * @param longitudeErrorRes String resource for the longitude validation error, or `null` if valid.
 * @param onNameChange Called on every keystroke in the name field (with live validation).
 * @param onDescriptionChange Called on every keystroke in the description field.
 * @param onLatitudeChange Called on every keystroke in the latitude field.
 * @param onLongitudeChange Called on every keystroke in the longitude field.
 * @param onConfirm Called when the user taps "Add"; triggers full validation in [MapViewModel].
 * @param onDismissRequest Called when the dialog is dismissed.
 */
@Composable
fun AddToFavoritesDialog(
    name: String,
    description: String,
    latitude: String,
    longitude: String,
    @StringRes nameErrorRes: Int?,
    @StringRes latitudeErrorRes: Int?,
    @StringRes longitudeErrorRes: Int?,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onLatitudeChange: (String) -> Unit,
    onLongitudeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.map_add_to_favorites)) },
        text = {
            Column {
                CoordinateInputField(
                    value = name,
                    onValueChange = onNameChange,
                    label = stringResource(R.string.field_name),
                    errorRes = nameErrorRes,
                )
                Spacer(modifier = Modifier.height(8.dp))
                CoordinateInputField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    label = stringResource(R.string.field_description),
                    errorRes = null,
                )
                Spacer(modifier = Modifier.height(8.dp))
                CoordinateInputField(
                    value = latitude,
                    onValueChange = onLatitudeChange,
                    label = stringResource(R.string.field_latitude),
                    errorRes = latitudeErrorRes,
                    keyboardType = KeyboardType.Number,
                )
                Spacer(modifier = Modifier.height(8.dp))
                CoordinateInputField(
                    value = longitude,
                    onValueChange = onLongitudeChange,
                    label = stringResource(R.string.field_longitude),
                    errorRes = longitudeErrorRes,
                    keyboardType = KeyboardType.Number,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/**
 * A single labelled [OutlinedTextField] with an inline validation error message shown beneath it
 * when [errorRes] is non-null. Shared by both map dialogs to avoid duplication.
 *
 * @param value Current field text.
 * @param onValueChange Called on every keystroke.
 * @param label Floating label string displayed inside the field.
 * @param errorRes String resource for the validation error, or `null` when the field is valid.
 * @param modifier Optional modifier applied to the [OutlinedTextField].
 * @param keyboardType Keyboard type hint; defaults to [KeyboardType.Unspecified] (text keyboard)
 *   and is overridden to [KeyboardType.Number] for coordinate fields.
 */
@Composable
private fun CoordinateInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    @StringRes errorRes: Int?,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Unspecified,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = errorRes != null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth()
    )
    if (errorRes != null) {
        Text(
            text = stringResource(errorRes),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

/**
 * Dialog for adding the current marker location to a route.
 *
 * The user can select an existing route or enter a name for a new route.
 * The dialog is fully controlled and holds no local state.
 *
 * @param routeNames List of available route names.
 * @param selectedRouteName The currently selected route name.
 * @param newRouteName Input for a new route name.
 * @param onRouteSelectionChange Called when a route is selected.
 * @param onNewRouteNameChange Called when the new route name input changes.
 * @param onConfirm Called on confirmation.
 * @param onDismissRequest Called when the dialog is dismissed.
 */
@Composable
fun AddToRouteDialog(
    routeNames: List<String>,
    selectedRouteName: String,
    newRouteName: String,
    onRouteSelectionChange: (String) -> Unit,
    onNewRouteNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.map_add_to_route)) },
        text = {
            Column {
                if (routeNames.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.route_select_existing),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    // Radio-button-style selection for existing routes
                    routeNames.forEach { name ->
                        Surface(
                            onClick = { onRouteSelectionChange(name) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            shape = MaterialTheme.shapes.small,
                            color = if (name == selectedRouteName) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        ) {
                            Text(
                                text = name,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.route_or_create_new),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newRouteName,
                    onValueChange = onNewRouteNameChange,
                    label = { Text(stringResource(R.string.route_new_route_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
