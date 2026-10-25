package com.noobexon.xposedfakelocation.manager.route

import com.noobexon.xposedfakelocation.data.GeoMath
import kotlin.random.Random

/** Playback lifecycle of a route. */
enum class RoutePhase { RUNNING, PAUSED, FINISHED }

/** Where playback is along a [RoutePlan]. While [waitRemainingS] > 0 it holds at the end of [legIndex]. */
data class PlayerState(
    val legIndex: Int = 0,
    val distanceIntoLegM: Double = 0.0,
    val waitRemainingS: Double = 0.0,
    val variationFactor: Double = 1.0,
    val variationTarget: Double = 1.0,
    val secondsToNextTarget: Double = 0.0,
    val phase: RoutePhase = RoutePhase.RUNNING,
)

/** The state after a step plus what to publish to target apps. */
data class RouteStep(
    val state: PlayerState,
    val latitude: Double,
    val longitude: Double,
    val bearingDegrees: Float,
    val speedMps: Float,
)

/** Pure playback maths over a [RoutePlan]. */
object RoutePlayer {
    private const val MAX_VARIATION_RATE_PER_S = 0.1
    private const val MIN_TARGET_INTERVAL_S = 3.0
    private const val MAX_TARGET_INTERVAL_S = 6.0

    /** Advances [state] by [dtSeconds] of wall time, scaled (moves and waits alike) by [multiplier]. */
    fun step(plan: RoutePlan, state: PlayerState, dtSeconds: Double, multiplier: Float, random: Random): RouteStep {
        if (state.phase != RoutePhase.RUNNING || dtSeconds <= 0.0) return snapshot(plan, state, multiplier)

        var s = withVariation(state, plan.legs[state.legIndex].variation, dtSeconds, random)
        var budget = dtSeconds * multiplier
        // Zero-length legs without waits consume no time; the cap stops a degenerate loop from spinning.
        var guard = plan.legs.size * 2 + 2
        while (budget > 0.0 && s.phase == RoutePhase.RUNNING && guard-- > 0) {
            val leg = plan.legs[s.legIndex]
            if (s.waitRemainingS > 0.0) {
                val used = minOf(budget, s.waitRemainingS)
                budget -= used
                s = s.copy(waitRemainingS = s.waitRemainingS - used)
                if (s.waitRemainingS <= 0.0) s = advance(plan, s.copy(waitRemainingS = 0.0))
                continue
            }
            val speed = leg.speedMps * s.variationFactor
            val needed = (leg.distanceM - s.distanceIntoLegM) / speed
            if (needed > budget) {
                s = s.copy(distanceIntoLegM = s.distanceIntoLegM + speed * budget)
                budget = 0.0
            } else {
                budget -= needed
                s = s.copy(distanceIntoLegM = leg.distanceM)
                s = if (leg.dwellSeconds > 0.0) s.copy(waitRemainingS = leg.dwellSeconds) else advance(plan, s)
            }
        }
        return snapshot(plan, s, multiplier)
    }

    /** Position and reported motion for [state] without advancing time. */
    fun snapshot(plan: RoutePlan, state: PlayerState, multiplier: Float): RouteStep {
        val leg = plan.legs[state.legIndex]
        val (lat, lon) = if (state.distanceIntoLegM >= leg.distanceM) {
            leg.endLatitude to leg.endLongitude
        } else {
            GeoMath.destination(leg.startLatitude, leg.startLongitude, leg.bearingDegrees.toDouble(), state.distanceIntoLegM)
        }
        val moving = state.phase == RoutePhase.RUNNING && state.waitRemainingS <= 0.0
        val speed = if (moving) (leg.speedMps * state.variationFactor * multiplier).toFloat() else 0f
        return RouteStep(state, lat, lon, leg.bearingDegrees, speed)
    }

    /** Jumps to [fraction] of the total distance, cancelling any wait. A finished route sought back becomes paused. */
    fun seek(plan: RoutePlan, state: PlayerState, fraction: Float): PlayerState {
        val clamped = fraction.coerceIn(0f, 1f)
        val target = clamped * plan.totalDistanceM
        val index = plan.legs.indices.firstOrNull { target < plan.legStartM(it) + plan.legs[it].distanceM }
            ?: plan.legs.lastIndex
        val into = (target - plan.legStartM(index)).coerceIn(0.0, plan.legs[index].distanceM)
        val phase = if (state.phase == RoutePhase.FINISHED && clamped < 1f) RoutePhase.PAUSED else state.phase
        return state.copy(legIndex = index, distanceIntoLegM = into, waitRemainingS = 0.0, phase = phase)
    }

    /** Metres from the start of the current lap to [state]'s position. */
    fun distanceCovered(plan: RoutePlan, state: PlayerState): Double =
        (plan.legStartM(state.legIndex) + state.distanceIntoLegM).coerceAtMost(plan.totalDistanceM)

    /** Fraction of the current lap's distance covered, 0..1. */
    fun progress(plan: RoutePlan, state: PlayerState): Float {
        if (plan.totalDistanceM <= 0.0) return if (state.phase == RoutePhase.FINISHED) 1f else 0f
        return (distanceCovered(plan, state) / plan.totalDistanceM).toFloat().coerceIn(0f, 1f)
    }

    private fun advance(plan: RoutePlan, s: PlayerState): PlayerState = when {
        s.legIndex < plan.legs.lastIndex -> s.copy(legIndex = s.legIndex + 1, distanceIntoLegM = 0.0)
        plan.loop -> s.copy(legIndex = 0, distanceIntoLegM = 0.0)
        else -> s.copy(phase = RoutePhase.FINISHED)
    }

    private fun withVariation(s: PlayerState, variation: Double, dt: Double, random: Random): PlayerState {
        if (variation <= 0.0) return s.copy(variationFactor = 1.0, variationTarget = 1.0, secondsToNextTarget = 0.0)
        val range = (1.0 - variation)..(1.0 + variation)
        var target = s.variationTarget
        var timer = s.secondsToNextTarget - dt
        if (timer <= 0.0 || target !in range) {
            target = 1.0 + (random.nextDouble() * 2.0 - 1.0) * variation
            timer = MIN_TARGET_INTERVAL_S + random.nextDouble() * (MAX_TARGET_INTERVAL_S - MIN_TARGET_INTERVAL_S)
        }
        val maxDelta = MAX_VARIATION_RATE_PER_S * dt
        val factor = (s.variationFactor + (target - s.variationFactor).coerceIn(-maxDelta, maxDelta))
            .coerceIn(range.start, range.endInclusive)
        return s.copy(variationFactor = factor, variationTarget = target, secondsToNextTarget = timer)
    }
}
