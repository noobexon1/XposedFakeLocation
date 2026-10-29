package com.noobexon.xposedfakelocation.manager.ui.map.maplibre

import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noobexon.xposedfakelocation.R
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

private const val DOWNLOAD_POLL_MS = 1_000L

/** Lists the stored offline areas with their size, date, source and status; resume or delete them. */
@Composable
internal fun OfflineAreasDialog(onDismiss: () -> Unit, viewModel: OfflineAreasViewModel = activityOfflineAreasViewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<OfflineArea?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.refresh()
        while (true) {
            delay(DOWNLOAD_POLL_MS)
            if (viewModel.state.value.areas.any { it.status is OfflineAreaStatus.Downloading }) viewModel.refresh()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val text = when (event) {
                is OfflineAreasEvent.Finished -> context.getString(R.string.offline_download_done, event.name)
                is OfflineAreasEvent.Failed -> context.getString(R.string.offline_download_failed, event.message)
            }
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setting_offline_areas_title)) },
        text = {
            when {
                !state.available -> Text(stringResource(R.string.offline_areas_unavailable))
                !state.loaded -> CircularProgressIndicator()
                state.areas.isEmpty() -> Text(stringResource(R.string.offline_areas_empty))
                else -> Column {
                    Text(
                        text = stringResource(
                            R.string.offline_areas_total,
                            Formatter.formatShortFileSize(context, state.areas.sumOf { it.sizeBytes })
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(state.areas, key = { it.key }) { area ->
                            OfflineAreaRow(
                                area = area,
                                onResume = { viewModel.resume(area.key) },
                                onDelete = { pendingDelete = area },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        },
    )

    pendingDelete?.let { area ->
        val name = area.name ?: stringResource(R.string.offline_area_unknown)
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.offline_area_delete_title)) },
            text = { Text(stringResource(R.string.offline_area_delete_message, name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(area.key)
                    pendingDelete = null
                }) { Text(stringResource(R.string.favorites_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun OfflineAreaRow(area: OfflineArea, onResume: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val name = area.name ?: stringResource(R.string.offline_area_unknown)
    val details = listOfNotNull(
        Formatter.formatShortFileSize(context, area.sizeBytes),
        area.createdAt?.let { DateFormat.getDateInstance(DateFormat.SHORT).format(Date(it)) },
        area.source,
    ).joinToString(" · ")
    val status = when (val s = area.status) {
        OfflineAreaStatus.Complete -> stringResource(R.string.offline_area_complete)
        is OfflineAreaStatus.Downloading -> stringResource(R.string.offline_area_downloading, s.percent)
        OfflineAreaStatus.Incomplete -> stringResource(R.string.offline_area_incomplete)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = if (area.status == OfflineAreaStatus.Incomplete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (area.status == OfflineAreaStatus.Incomplete) {
            TextButton(onClick = onResume) { Text(stringResource(R.string.offline_area_resume)) }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete_named_item, name))
        }
    }
}
