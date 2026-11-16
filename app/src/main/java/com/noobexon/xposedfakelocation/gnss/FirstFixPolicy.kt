package com.noobexon.xposedfakelocation.gnss

import kotlin.random.Random

/**
 * Synthesizes one first-fix event per engine start, a random 1-4 s after the start. A callback registered
 * while the engine was already running gets its first fix with the first status.
 */
class FirstFixPolicy(private val random: Random) {
    private enum class State { UNKNOWN, STARTED, FIXED, STOPPED }

    private var state = State.UNKNOWN
    private var startedAtMillis = 0L
    private var ttffMillis = 0

    @Synchronized
    fun onStarted(nowMillis: Long) {
        state = State.STARTED
        startedAtMillis = nowMillis
        ttffMillis = randomTtff()
    }

    @Synchronized
    fun onStopped() {
        state = State.STOPPED
    }

    /** Called before delivering a fake status: the TTFF to report through `onFirstFix` now, or `null`. */
    @Synchronized
    fun firstFixDue(nowMillis: Long): Int? = when (state) {
        State.UNKNOWN -> {
            ttffMillis = randomTtff()
            state = State.FIXED
            ttffMillis
        }
        State.STARTED -> if (nowMillis - startedAtMillis >= ttffMillis) {
            state = State.FIXED
            ttffMillis
        } else {
            null
        }
        State.FIXED, State.STOPPED -> null
    }

    /** TTFF of the current session; 0 before its first fix. */
    val timeToFirstFixMillis: Int
        @Synchronized get() = if (state == State.FIXED) ttffMillis else 0

    private fun randomTtff(): Int = random.nextInt(MIN_TTFF_MILLIS, MAX_TTFF_MILLIS + 1)

    companion object {
        private const val MIN_TTFF_MILLIS = 1000
        private const val MAX_TTFF_MILLIS = 4000

        /** The real first fix belongs to the real sky, so it is dropped while spoofing. */
        fun deliversRealFirstFix(spoofing: Boolean): Boolean = !spoofing
    }
}
