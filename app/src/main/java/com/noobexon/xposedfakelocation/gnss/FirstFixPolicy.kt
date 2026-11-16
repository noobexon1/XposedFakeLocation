package com.noobexon.xposedfakelocation.gnss

import kotlin.random.Random

/**
 * The first-fix session of the GNSS engine, shared by every status callback in the process. Each engine
 * start fixes a random 1-4 s after the start, or when the real first fix is forwarded; an engine that was
 * already running when first seen counts as fixed.
 */
class FirstFixPolicy(private val random: Random) {
    private enum class State { UNKNOWN, STARTED, FIXED, STOPPED }

    private var state = State.UNKNOWN
    private var startedAtMillis = 0L
    private var ttffMillis = 0

    /** Several callbacks receive the same start, so a running session is kept. */
    @Synchronized
    fun onStarted(nowMillis: Long) {
        if (state == State.STARTED || state == State.FIXED) return
        state = State.STARTED
        startedAtMillis = nowMillis
        ttffMillis = randomTtff()
    }

    @Synchronized
    fun onStopped() {
        state = State.STOPPED
    }

    /**
     * A real first fix was forwarded, so spoofing starting later in this session doesn't fix again. A `null`
     * TTFF (legacy event) keeps the one drawn at start.
     */
    @Synchronized
    fun onRealFirstFix(ttffMillis: Int?) {
        if (ttffMillis != null) {
            this.ttffMillis = ttffMillis
        } else if (state == State.UNKNOWN || state == State.STOPPED) {
            this.ttffMillis = randomTtff()
        }
        state = State.FIXED
    }

    /**
     * Called for a status at [nowMillis]: the session's TTFF once it has its first fix, else `null`. A status
     * while UNKNOWN means the engine runs without a start seen, so it is fixed at once; likewise while STOPPED
     * unless [recoverStopped] is false (NMEA, which can trail the stop on its own binder).
     */
    @Synchronized
    fun fixedTtff(nowMillis: Long, recoverStopped: Boolean = true): Int? {
        when (state) {
            State.UNKNOWN -> fixNow()
            State.STOPPED -> if (recoverStopped) fixNow()
            State.STARTED -> if (nowMillis - startedAtMillis >= ttffMillis) {
                state = State.FIXED
            }
            State.FIXED -> Unit
        }
        return if (state == State.FIXED) ttffMillis else null
    }

    private fun fixNow() {
        ttffMillis = randomTtff()
        state = State.FIXED
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
