package com.noobexon.xposedfakelocation.manager.joystick

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.manager.App

/**
 * Top-bar action that launches the joystick overlay, walking the user through the overlay
 * permission (required) and notification permission (optional, Android 13+) first.
 *
 * @param enabled `true` only while spoofing is on and a marker is placed.
 */
@Composable
fun JoystickButton(enabled: Boolean) {
    val context = LocalContext.current
    var showOverlayRationale by remember { mutableStateOf(false) }
    val moduleInactive = stringResource(R.string.system_hooks_module_inactive)

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { JoystickOverlayService.start(context) }

    fun startWithNotificationPermission() {
        val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            JoystickOverlayService.start(context)
        }
    }

    val overlaySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(context)) startWithNotificationPermission()
    }

    IconButton(
        enabled = enabled,
        onClick = {
            when {
                App.service == null -> Toast.makeText(context, moduleInactive, Toast.LENGTH_SHORT).show()
                !Settings.canDrawOverlays(context) -> showOverlayRationale = true
                else -> startWithNotificationPermission()
            }
        }
    ) {
        Icon(Icons.Default.SportsEsports, contentDescription = stringResource(R.string.cd_joystick_open))
    }

    if (showOverlayRationale) {
        AlertDialog(
            onDismissRequest = { showOverlayRationale = false },
            title = { Text(stringResource(R.string.joystick_overlay_permission_title)) },
            text = { Text(stringResource(R.string.joystick_overlay_permission_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showOverlayRationale = false
                    overlaySettingsLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    )
                }) { Text(stringResource(R.string.permissions_open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayRationale = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
