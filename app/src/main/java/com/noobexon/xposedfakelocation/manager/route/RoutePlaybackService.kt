package com.noobexon.xposedfakelocation.manager.route

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
import android.widget.Toast
import androidx.annotation.StringRes
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
import com.noobexon.xposedfakelocation.data.ROUTE_MIN_POINTS
import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.data.repository.PreferencesRepository
import com.noobexon.xposedfakelocation.manager.App
import com.noobexon.xposedfakelocation.manager.MainActivity
import com.noobexon.xposedfakelocation.manager.joystick.JoystickRunningState
import com.noobexon.xposedfakelocation.manager.localization.LocaleController
import com.noobexon.xposedfakelocation.manager.overlay.OverlayWindowHost
import com.noobexon.xposedfakelocation.manager.route.ui.RouteOverlay
import com.noobexon.xposedfakelocation.manager.ui.theme.ThemeOption
import com.noobexon.xposedfakelocation.manager.ui.theme.XposedFakeLocationTheme
import com.noobexon.xposedfakelocation.manager.ui.theme.isDark
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Foreground service playing one route: owns the [RouteSession], its notification and the
 * optional overlay. Ends on Stop, when spoofing is turned off, or when the marker is cleared or
 * changed by anything other than this session.
 */
class RoutePlaybackService : LifecycleService(), SavedStateRegistryOwner {

    companion object {
        private const val TAG = "RoutePlaybackService"
        private const val ACTION_START = "com.noobexon.xposedfakelocation.route.START"
        private const val ACTION_PAUSE = "com.noobexon.xposedfakelocation.route.PAUSE"
        private const val ACTION_RESUME = "com.noobexon.xposedfakelocation.route.RESUME"
        private const val ACTION_STOP = "com.noobexon.xposedfakelocation.route.STOP"
        private const val EXTRA_ROUTE_ID = "route_id"
        private const val CHANNEL_ID = "route_playback"
        private const val NOTIFICATION_ID = 4202
        private const val OVERLAY_DEFAULT_Y_FRACTION = 0.15f

        /** Plays the saved route [routeId], or the map's draft when `null`. */
        fun start(context: Context, routeId: String? = null) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RoutePlaybackService::class.java).setAction(ACTION_START).putExtra(EXTRA_ROUTE_ID, routeId),
            )
        }

        fun stop(context: Context) {
            if (RoutePlaybackState.current.value == null) return
            context.startService(Intent(context, RoutePlaybackService::class.java).setAction(ACTION_STOP))
        }
    }

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private lateinit var repository: PreferencesRepository
    private var session: RouteSession? = null
    private var window: OverlayWindowHost? = null
    private val collapsed = MutableStateFlow(false)
    private var lastNotified: Pair<RoutePhase, Int>? = null

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
        val active = session
        when (intent?.action) {
            ACTION_STOP -> endSession(userStop = true)
            ACTION_PAUSE -> active?.pause() ?: stopSelfCompletely()
            ACTION_RESUME -> active?.play() ?: stopSelfCompletely()
            else -> if (active == null) beginSession(intent?.getStringExtra(EXTRA_ROUTE_ID)) else collapsed.value = false
        }
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        window?.clampToScreen()
    }

    override fun onDestroy() {
        releaseSession()
        super.onDestroy()
    }

    private fun beginSession(routeId: String?) {
        val route = routeId?.let(repository::getRoute) ?: repository.getRouteDraft().route
        val problem = when {
            App.service == null -> R.string.system_hooks_module_inactive
            route.points.size < ROUTE_MIN_POINTS -> R.string.route_needs_two_points
            JoystickRunningState.running.value -> R.string.route_joystick_running
            else -> null
        }
        if (problem != null) {
            toast(problem)
            stopSelfCompletely()
            return
        }

        val plan = RoutePlan.from(route, repository.getJoystickSettings())
        val newSession = RouteSession(
            scope = lifecycleScope,
            plan = plan,
            publish = { frame -> repository.saveJoystickFrame(frame.latitude, frame.longitude, frame.state) },
            monotonicClock = SystemClock::elapsedRealtime,
        )
        session = newSession

        lifecycleScope.launch {
            repository.saveLastClickedLocation(plan.startLatitude, plan.startLongitude)
            repository.saveIsPlaying(true)
            if (session !== newSession) return@launch
            newSession.start()
            observeStatus(newSession, route)
            if (Settings.canDrawOverlays(this@RoutePlaybackService)) showOverlay(newSession, route)
            observeStopConditions(newSession)
        }
    }

    private fun displayName(route: Route): String = route.name.ifBlank { getString(R.string.route_unnamed) }

    private fun observeStatus(active: RouteSession, route: Route) {
        lifecycleScope.launch {
            active.status.collect { status ->
                RoutePlaybackState.publish(RoutePlaybackStatus(route.id, displayName(route), route.points.size, status))
                val key = status.phase to (status.progress * 100).toInt()
                if (key != lastNotified) {
                    lastNotified = key
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
                }
            }
        }
    }

    private fun showOverlay(active: RouteSession, route: Route) {
        val themeOptionFlow = repository.getThemeOptionFlow().map { ThemeOption.fromTag(it) }
        val name = displayName(route)
        window = OverlayWindowHost(
            context = this,
            lifecycleOwner = this,
            savedStateRegistryOwner = this,
            initialPosition = repository.getRouteOverlayPosition(),
            onPositionCommitted = repository::saveRouteOverlayPosition,
            defaultYFraction = OVERLAY_DEFAULT_Y_FRACTION,
        ) {
            val themeOption by themeOptionFlow.collectAsState(initial = ThemeOption.SYSTEM)
            XposedFakeLocationTheme(darkTheme = themeOption.isDark()) {
                val isCollapsed by collapsed.collectAsState()
                val status by active.status.collectAsState()
                RouteOverlay(
                    routeName = name,
                    pointCount = route.points.size,
                    collapsed = isCollapsed,
                    status = { status },
                    onPlayPause = { if (active.status.value.phase == RoutePhase.RUNNING) active.pause() else active.play() },
                    onStop = { endSession(userStop = true) },
                    onSeek = active::seek,
                    onCycleMultiplier = active::cycleMultiplier,
                    onCollapsedChange = { collapsed.value = it },
                    onDrag = { dx, dy -> window?.moveBy(dx, dy) },
                    onDragEnd = { window?.commitPosition() },
                )
            }
        }
        // The overlay permission can be revoked between the check and addView; playback goes on without it.
        runCatching { window?.show() }.onFailure { error ->
            Log.w(TAG, "Could not show the route overlay; continuing with notification controls", error)
            window?.remove()
            window = null
        }
    }

    private fun observeStopConditions(active: RouteSession) {
        // Emits false as soon as the Xposed service disconnects, too.
        lifecycleScope.launch {
            repository.getIsPlayingFlow().collect { isPlaying ->
                if (!isPlaying) {
                    if (App.service == null && session != null) toast(R.string.route_service_lost)
                    endSession(userStop = false)
                }
            }
        }
        lifecycleScope.launch {
            repository.getLastClickedLocationFlow().collect { location ->
                if (location == null || !active.isOwnEcho(location.latitude, location.longitude)) {
                    endSession(userStop = false)
                }
            }
        }
    }

    /** @param userStop the user pressed Stop, so spoofing is turned off as well. */
    private fun endSession(userStop: Boolean) {
        val wasActive = session != null
        releaseSession()
        if (userStop && wasActive) {
            lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) { repository.saveIsPlaying(false) }
        }
        stopSelfCompletely()
    }

    private fun releaseSession() {
        session?.let { repository.saveJoystickState(it.stop().state) }
        session = null
        window?.remove()
        window = null
        RoutePlaybackState.publish(null)
    }

    private fun stopSelfCompletely() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun toast(@StringRes message: Int) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this, requestCode,
        Intent(this, RoutePlaybackService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun buildNotification(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.route_notification_channel), NotificationManager.IMPORTANCE_LOW)
        )
        val playback = RoutePlaybackState.current.value
        val status = playback?.status
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle(playback?.routeName ?: getString(R.string.route_notification_title))
            .setContentText(playback?.let { statusText(it.status, it.pointCount) } ?: getString(R.string.route_notification_starting))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, ((status?.progress ?: 0f) * 100).toInt(), false)
        if (status != null && status.phase != RoutePhase.FINISHED) {
            val paused = status.phase == RoutePhase.PAUSED
            builder.addAction(
                0,
                getString(if (paused) R.string.route_action_resume else R.string.route_action_pause),
                servicePendingIntent(if (paused) ACTION_RESUME else ACTION_PAUSE, requestCode = 1),
            )
        }
        builder.addAction(0, getString(R.string.route_action_stop), servicePendingIntent(ACTION_STOP, requestCode = 2))
        return builder.build()
    }

    private fun statusText(status: RouteStatus, pointCount: Int): String = when {
        status.phase == RoutePhase.FINISHED -> getString(R.string.route_status_finished)
        status.waitRemainingS > 0 -> getString(R.string.route_status_waiting, RouteFormat.clock(status.waitRemainingS))
        status.phase == RoutePhase.PAUSED -> getString(R.string.route_status_paused)
        else -> getString(R.string.route_status_point, status.pointNumber, pointCount)
    }
}
