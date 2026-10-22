package com.noobexon.xposedfakelocation.manager.joystick

import com.noobexon.xposedfakelocation.data.model.JoystickSettings
import com.noobexon.xposedfakelocation.data.model.JoystickState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/** A position plus the joystick state published with it. */
data class JoystickFrame(val latitude: Double, val longitude: Double, val state: JoystickState)

/** Decides when a [JoystickSession] publishes a frame. */
internal object JoystickWritePolicy {
    const val MOVING_INTERVAL_MS = 250L
    const val HEARTBEAT_INTERVAL_MS = 2_000L

    fun shouldWrite(nowMs: Long, lastWriteMs: Long, moving: Boolean, wasMoving: Boolean): Boolean = when {
        wasMoving && !moving -> true
        moving -> nowMs - lastWriteMs >= MOVING_INTERVAL_MS
        else -> nowMs - lastWriteMs >= HEARTBEAT_INTERVAL_MS
    }
}

/**
 * Positions this session recently published, so their echo through the last-clicked-location
 * flow isn't mistaken for the user moving the marker.
 */
internal class RecentPositions(private val capacity: Int = 16) {
    private val items = ArrayDeque<Pair<Double, Double>>()

    fun add(latitude: Double, longitude: Double) {
        if (items.size == capacity) items.removeFirst()
        items.addLast(latitude to longitude)
    }

    fun contains(latitude: Double, longitude: Double): Boolean =
        items.any { (lat, lon) -> abs(lat - latitude) < EPSILON && abs(lon - longitude) < EPSILON }

    private companion object {
        const val EPSILON = 1e-9
    }
}

/**
 * Integrates joystick input into a moving position and hands frames to [publish].
 *
 * Not thread-safe: [setInput], [onExternalMarker], [stop] and the tick loop must all run on the
 * single thread backing [scope] (the service's main-thread scope). Only [publish] runs on
 * [ioDispatcher].
 */
class JoystickSession(
    private val scope: CoroutineScope,
    startLatitude: Double,
    startLongitude: Double,
    private val settings: StateFlow<JoystickSettings>,
    private val publish: (JoystickFrame) -> Unit,
    private val monotonicClock: () -> Long,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private var state = MovementState(startLatitude, startLongitude, bearingDegrees = 0f, speedMps = 0f)
    private var input = MovementInput.NONE
    private var lastTickMs: Long? = null
    private var lastWriteMs: Long? = null
    private var wasMoving = false
    private val recent = RecentPositions()
    private val frames = Channel<JoystickFrame>(Channel.CONFLATED)
    private var jobs: List<Job> = emptyList()

    val currentState: MovementState get() = state

    fun start() {
        jobs = listOf(
            scope.launch(ioDispatcher) { for (frame in frames) publish(frame) },
            scope.launch {
                while (true) {
                    tick(monotonicClock())?.let { frames.trySend(it) }
                    delay(TICK_INTERVAL_MS)
                }
            },
        )
    }

    fun setInput(x: Float, y: Float) {
        input = MovementInput(x, y)
    }

    /** Adopts a marker position set elsewhere (map tap, favorite, broadcast) unless it is our own echo. */
    fun onExternalMarker(latitude: Double, longitude: Double) {
        if (recent.contains(latitude, longitude)) return
        state = state.copy(latitude = latitude, longitude = longitude)
    }

    /** Cancels the loop and returns the final inactive frame for the caller to publish. */
    fun stop(): JoystickFrame {
        jobs.forEach { it.cancel() }
        frames.close()
        return frame(active = false)
    }

    internal fun tick(nowMs: Long): JoystickFrame? {
        val dtSeconds = lastTickMs?.let { (nowMs - it) / 1000.0 } ?: 0.0
        lastTickMs = nowMs
        state = MovementEngine.step(state, input, settings.value, dtSeconds)

        val moving = state.speedMps > 0f
        val lastWrite = lastWriteMs
        val write = lastWrite == null || JoystickWritePolicy.shouldWrite(nowMs, lastWrite, moving, wasMoving)
        wasMoving = moving
        if (!write) return null

        lastWriteMs = nowMs
        recent.add(state.latitude, state.longitude)
        return frame(active = true)
    }

    private fun frame(active: Boolean) = JoystickFrame(
        latitude = state.latitude,
        longitude = state.longitude,
        state = JoystickState(
            active = active,
            bearing = state.bearingDegrees,
            speed = if (active) state.speedMps else 0f,
            updatedAt = wallClock(),
        ),
    )

    private companion object {
        const val TICK_INTERVAL_MS = 50L
    }
}
