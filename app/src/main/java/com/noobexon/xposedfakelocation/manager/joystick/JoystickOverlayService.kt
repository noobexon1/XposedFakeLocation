package com.noobexon.xposedfakelocation.manager.joystick

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.noobexon.xposedfakelocation.R
import com.noobexon.xposedfakelocation.data.repository.PreferencesRepository
import com.noobexon.xposedfakelocation.manager.App
import com.noobexon.xposedfakelocation.manager.MainActivity
import com.noobexon.xposedfakelocation.manager.joystick.ui.JoystickOverlay
import com.noobexon.xposedfakelocation.manager.localization.LocaleController
import com.noobexon.xposedfakelocation.manager.ui.theme.ThemeOption
import com.noobexon.xposedfakelocation.manager.ui.theme.XposedFakeLocationTheme
import com.noobexon.xposedfakelocation.manager.ui.theme.isDark
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Foreground service owning the joystick overlay and its [JoystickSession].
 *
 * Runs only while spoofing is on and a marker exists; ends itself when either stops being true,
 * when the overlay's close button or the notification's Stop action is used, or on destroy.
 */
class JoystickOverlayService : LifecycleService(), SavedStateRegistryOwner {

    companion object {
        private const val TAG = "JoystickOverlayService"
        private const val ACTION_START = "com.noobexon.xposedfakelocation.joystick.START"
        private const val ACTION_STOP = "com.noobexon.xposedfakelocation.joystick.STOP"
        private const val CHANNEL_ID = "joystick_overlay"
        private const val NOTIFICATION_ID = 4201

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, JoystickOverlayService::class.java).setAction(ACTION_START)
            )
        }
    }

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private lateinit var repository: PreferencesRepository
    private var session: JoystickSession? = null
    private var window: JoystickOverlayWindow? = null
    private val collapsed = MutableStateFlow(false)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleController.attachBaseContext(newBase))
    }

    override fun onCreate() {
        savedStateRegistryController.performRestore(null)
        super.onCreate()
        repository = PreferencesRepository(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startInForeground()
        when {
            intent?.action == ACTION_STOP -> endSession(keepPosition = true)
            session == null -> beginSession()
            else -> collapsed.value = false
        }
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        window?.clampToScreen()
    }

    override fun onDestroy() {
        releaseSession(keepPosition = true)
        super.onDestroy()
    }

    private fun beginSession() {
        val start = repository.getLastClickedLocation()
        if (App.service == null || start == null || !repository.getIsPlaying() || !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Joystick preconditions not met; stopping")
            stopSelfCompletely()
            return
        }

        val settings = repository.getJoystickSettingsFlow()
            .stateIn(lifecycleScope, SharingStarted.Eagerly, repository.getJoystickSettings())
        val themeOptionFlow = repository.getThemeOptionFlow().map { ThemeOption.fromTag(it) }
        val newSession = JoystickSession(
            scope = lifecycleScope,
            startLatitude = start.latitude,
            startLongitude = start.longitude,
            settings = settings,
            publish = { frame -> repository.saveJoystickFrame(frame.latitude, frame.longitude, frame.state) },
            monotonicClock = SystemClock::elapsedRealtime,
        )
        session = newSession

        window = JoystickOverlayWindow(
            context = this,
            lifecycleOwner = this,
            savedStateRegistryOwner = this,
            initialPosition = repository.getJoystickOverlayPosition(),
            onPositionCommitted = repository::saveJoystickOverlayPosition,
        ) {
            val themeOption by themeOptionFlow.collectAsState(initial = ThemeOption.SYSTEM)
            XposedFakeLocationTheme(darkTheme = themeOption.isDark()) {
                val currentSettings by settings.collectAsState()
                val isCollapsed by collapsed.collectAsState()
                JoystickOverlay(
                    settings = currentSettings,
                    collapsed = isCollapsed,
                    onInput = newSession::setInput,
                    onRelease = { newSession.setInput(0f, 0f) },
                    onModeChange = repository::saveJoystickMode,
                    onPresetChange = repository::saveJoystickSelectedPreset,
                    onCollapsedChange = { collapsed.value = it },
                    onClose = { endSession(keepPosition = true) },
                    onDrag = { dx, dy -> window?.moveBy(dx, dy) },
                    onDragEnd = { window?.commitPosition() },
                )
            }
        }.also { it.show() }

        newSession.start()
        observeStopConditions()
    }

    private fun observeStopConditions() {
        // Emits false as soon as the Xposed service disconnects, too.
        lifecycleScope.launch {
            repository.getIsPlayingFlow().collect { isPlaying ->
                if (!isPlaying) endSession(keepPosition = true)
            }
        }
        lifecycleScope.launch {
            repository.getLastClickedLocationFlow().collect { location ->
                if (location == null) {
                    endSession(keepPosition = false)
                } else {
                    session?.onExternalMarker(location.latitude, location.longitude)
                }
            }
        }
    }

    /**
     * @param keepPosition `false` when the marker was cleared: publishing the final position would
     *   put the marker back. [releaseSession] also drops it when the marker is already gone.
     */
    private fun endSession(keepPosition: Boolean) {
        releaseSession(keepPosition)
        stopSelfCompletely()
    }

    private fun releaseSession(keepPosition: Boolean) {
        session?.let { active ->
            val last = active.stop()
            // Decide from the current marker, not from which stop event arrived first.
            if (keepPosition && repository.getLastClickedLocation() != null) {
                repository.saveJoystickFrame(last.latitude, last.longitude, last.state)
            } else {
                repository.saveJoystickState(last.state)
            }
        }
        session = null
        window?.remove()
        window = null
    }

    private fun stopSelfCompletely() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.joystick_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, JoystickOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(getString(R.string.joystick_notification_title))
            .setContentText(getString(R.string.joystick_notification_text))
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.joystick_notification_stop), stopIntent)
            .setOngoing(true)
            .build()
    }
}
