package com.noobexon.xposedfakelocation.manager.route

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.manager.App

/**
 * Route mode's play/stop button. Stops a playing route, or spoofing that was started some other
 * way; otherwise starts the draft, asking for the (optional) overlay and notification permissions.
 *
 * @param blockedReason why the route can't start, or `null` when it can.
 * @param missingRoadSegments road segments without a road path; asks before playing them straight.
 */
@Composable
fun RoutePlayButton(
    @StringRes blockedReason: Int?,
    routePlaying: Boolean,
    otherSpoofingActive: Boolean,
    onStopSpoofing: () -> Unit,
    missingRoadSegments: Int = 0,
) {
    val context = LocalContext.current
    var showOverlayRationale by remember { mutableStateOf(false) }
    var showMissingRoads by remember { mutableStateOf(false) }
    val moduleInactive = stringResource(R.string.system_hooks_module_inactive)
    val blockedMessage = blockedReason?.let { stringResource(it) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { RoutePlaybackService.start(context) }

    fun startWithNotificationPermission() {
        val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            RoutePlaybackService.start(context)
        }
    }

    val overlaySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { startWithNotificationPermission() }

    fun startAfterChecks() {
        if (!Settings.canDrawOverlays(context)) showOverlayRationale = true else startWithNotificationPermission()
    }

    val stopping = routePlaying || otherSpoofingActive
    val active = stopping || blockedReason == null
    SmallFloatingActionButton(
        onClick = {
            when {
                routePlaying -> RoutePlaybackService.stop(context)
                otherSpoofingActive -> onStopSpoofing()
                blockedMessage != null -> Toast.makeText(context, blockedMessage, Toast.LENGTH_SHORT).show()
                App.service == null -> Toast.makeText(context, moduleInactive, Toast.LENGTH_SHORT).show()
                missingRoadSegments > 0 -> showMissingRoads = true
                else -> startAfterChecks()
            }
        },
        containerColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        contentColor = if (active) contentColorFor(MaterialTheme.colorScheme.primary) else MaterialTheme.colorScheme.onSurfaceVariant,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = if (active) 4.dp else 0.dp),
    ) {
        Icon(
            imageVector = if (stopping) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = stringResource(if (stopping) R.string.cd_route_stop else R.string.cd_route_play),
        )
    }

    if (showMissingRoads) {
        AlertDialog(
            onDismissRequest = { showMissingRoads = false },
            title = { Text(stringResource(R.string.route_missing_roads_title)) },
            text = { Text(stringResource(R.string.route_missing_roads_message, missingRoadSegments)) },
            confirmButton = {
                TextButton(onClick = {
                    showMissingRoads = false
                    startAfterChecks()
                }) { Text(stringResource(R.string.route_play_anyway)) }
            },
            dismissButton = {
                TextButton(onClick = { showMissingRoads = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showOverlayRationale) {
        AlertDialog(
            onDismissRequest = { showOverlayRationale = false },
            title = { Text(stringResource(R.string.joystick_overlay_permission_title)) },
            text = { Text(stringResource(R.string.route_overlay_permission_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showOverlayRationale = false
                    overlaySettingsLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    )
                }) { Text(stringResource(R.string.permissions_open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showOverlayRationale = false
                    startWithNotificationPermission()
                }) { Text(stringResource(R.string.route_continue_without_overlay)) }
            },
        )
    }
}
