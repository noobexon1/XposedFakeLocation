package com.noobexon.xposedfakelocation.manager.ui.map.maplibre

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.OFFLINE_AREA_NAME_MAX_LENGTH

/**
 * The activity's [OfflineAreasViewModel], shared by the map and Settings so both see the same
 * download and its observers.
 */
@Composable
internal fun activityOfflineAreasViewModel(): OfflineAreasViewModel {
    val owner = LocalContext.current.findActivity() ?: checkNotNull(LocalViewModelStoreOwner.current)
    return viewModel(viewModelStoreOwner = owner)
}

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Asks for the offline area's name, prefilled with [initialName]. [suggestedName] replaces it when
 * it arrives later, unless the user has already typed. [onConfirm] gets the name trimmed.
 */
@Composable
internal fun OfflineAreaNameDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    suggestedName: String? = null,
    @StringRes titleRes: Int = R.string.offline_area_name_title,
    @StringRes confirmRes: Int = R.string.action_download,
) {
    var name by remember { mutableStateOf(initialName) }
    var edited by remember { mutableStateOf(false) }
    LaunchedEffect(suggestedName) {
        if (suggestedName != null && !edited) name = suggestedName
    }
    val valid = name.trim().length in 1..OFFLINE_AREA_NAME_MAX_LENGTH
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    edited = true
                },
                label = { Text(stringResource(R.string.offline_area_name_label)) },
                singleLine = true,
                isError = !valid,
                supportingText = if (valid) null else {
                    { Text(stringResource(R.string.offline_area_name_invalid)) }
                },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = valid) {
                Text(stringResource(confirmRes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** The running download with Cancel, which stops it and deletes the area. */
@Composable
internal fun OfflineDownloadCard(download: OfflineDownload, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.offline_download_progress, download.name, download.percent),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (download.waitingForNetwork) {
                    Text(
                        text = stringResource(R.string.offline_download_waiting),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (download.problem != null) {
                    Text(
                        text = stringResource(R.string.offline_download_problem, download.problem),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                LinearProgressIndicator(
                    progress = { download.percent / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}
