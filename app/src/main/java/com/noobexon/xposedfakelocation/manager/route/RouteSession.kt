package com.noobexon.xposedfakelocation.manager.route

import android.util.Log
import com.noobexon.xposedfakelocation.data.model.JoystickState
import com.noobexon.xposedfakelocation.manager.joystick.JoystickFrame
import com.noobexon.xposedfakelocation.manager.joystick.JoystickWritePolicy
import com.noobexon.xposedfakelocation.manager.joystick.RecentPositions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.random.Random

/** Playback status for the overlay, notification and map screen. */
data class RouteStatus(
    val phase: RoutePhase,
    val progress: Float,
    val speedMps: Float,
    /** 1-based number of the point being travelled to (or waited at). */
    val pointNumber: Int,
    val waitRemainingS: Int,
    val distanceCoveredM: Double,
    val totalDistanceM: Double,
    val multiplier: Float,
)

/**
 * Plays a [RoutePlan] and hands frames to [publish], using the joystick's write policy.
 *
 * Not thread-safe: controls, [stop] and the tick loop must all run on the single thread backing
 * [scope] (the service's main-thread scope). Only [publish] runs on [ioDispatcher].
 */
class RouteSession(
    private val scope: CoroutineScope,
    private val plan: RoutePlan,
    private val publish: (JoystickFrame) -> Unit,
    private val monotonicClock: () -> Long,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val random: Random = Random.Default,
    private val onPublishError: (Throwable) -> Unit = { Log.w(TAG, "Failed to publish route frame", it) },
) {
    private var player = PlayerState()
    private var multiplier = 1f
    private var last: RouteStep = RoutePlayer.snapshot(plan, player, multiplier)
    private var lastTickMs: Long? = null
    private var lastWriteMs: Long? = null
    private var wasMoving = false
    private val recent = RecentPositions().apply { add(plan.startLatitude, plan.startLongitude) }
    private val frames = Channel<JoystickFrame>(Channel.CONFLATED)
    private var jobs: List<Job> = emptyList()
    private val lock = Any()
    private var stopped = false
    private val _status = MutableStateFlow(statusOf(last))

    val status: StateFlow<RouteStatus> = _status.asStateFlow()

    fun start() {
        jobs = listOf(
            scope.launch(ioDispatcher) { for (frame in frames) publishSafely(frame) },
            scope.launch {
                while (true) {
                    tick(monotonicClock())?.let { frames.trySend(it) }
                    delay(TICK_INTERVAL_MS)
                }
            },
        )
    }

    /** Resumes a paused route, or replays a finished one. */
    fun play() {
        when (player.phase) {
            RoutePhase.PAUSED -> player = player.copy(phase = RoutePhase.RUNNING)
            RoutePhase.FINISHED -> return replay()
            RoutePhase.RUNNING -> Unit
        }
        refresh()
    }

    fun pause() {
        if (player.phase == RoutePhase.RUNNING) player = player.copy(phase = RoutePhase.PAUSED)
        refresh()
    }

    fun replay() {
        player = PlayerState()
        lastWriteMs = null
        refresh()
    }

    fun seek(fraction: Float) {
        player = RoutePlayer.seek(plan, player, fraction)
        lastWriteMs = null
        refresh()
    }

    fun cycleMultiplier() {
        multiplier = MULTIPLIERS[(MULTIPLIERS.indexOf(multiplier) + 1) % MULTIPLIERS.size]
        refresh()
    }

    /** Whether a marker position is one this session published (so it isn't an external change). */
    fun isOwnEcho(latitude: Double, longitude: Double): Boolean = recent.contains(latitude, longitude)

    /**
     * Cancels the loop and returns the final inactive frame for the caller to publish; once this
     * returns no [publish] is running or can start, so that final write is guaranteed to be last.
     */
    fun stop(): JoystickFrame {
        synchronized(lock) { stopped = true }
        jobs.forEach { it.cancel() }
        frames.close()
        return frame(last, active = false)
    }

    internal fun tick(nowMs: Long): JoystickFrame? {
        val dtSeconds = lastTickMs?.let { (nowMs - it) / 1000.0 } ?: 0.0
        lastTickMs = nowMs
        last = RoutePlayer.step(plan, player, dtSeconds, multiplier, random)
        player = last.state
        _status.value = statusOf(last)

        val moving = last.speedMps > 0f
        val lastWrite = lastWriteMs
        val write = lastWrite == null || JoystickWritePolicy.shouldWrite(nowMs, lastWrite, moving, wasMoving)
        wasMoving = moving
        if (!write) return null

        lastWriteMs = nowMs
        recent.add(last.latitude, last.longitude)
        return frame(last, active = true)
    }

    private fun refresh() {
        last = RoutePlayer.snapshot(plan, player, multiplier)
        _status.value = statusOf(last)
    }

    private fun statusOf(step: RouteStep) = RouteStatus(
        phase = step.state.phase,
        progress = RoutePlayer.progress(plan, step.state),
        speedMps = step.speedMps,
        pointNumber = plan.legs[step.state.legIndex].endPointNumber,
        waitRemainingS = ceil(step.state.waitRemainingS).toInt(),
        distanceCoveredM = RoutePlayer.distanceCovered(plan, step.state),
        totalDistanceM = plan.totalDistanceM,
        multiplier = multiplier,
    )

    private fun frame(step: RouteStep, active: Boolean) = JoystickFrame(
        latitude = step.latitude,
        longitude = step.longitude,
        state = JoystickState(
            active = active,
            bearing = step.bearingDegrees,
            speed = if (active) step.speedMps else 0f,
            updatedAt = wallClock(),
        ),
    )

    /** Publishes [frame] unless stopped; a failing [publish] is reported but doesn't end the session. */
    private fun publishSafely(frame: JoystickFrame) {
        try {
            synchronized(lock) { if (!stopped) publish(frame) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onPublishError(e)
        }
    }

    private companion object {
        const val TAG = "RouteSession"
        const val TICK_INTERVAL_MS = 100L
        val MULTIPLIERS = listOf(0.5f, 1f, 2f)
    }
}
