package com.noobexon.xposedfakelocation.manager.route

import com.noobexon.xposedfakelocation.data.GeoMath
import com.noobexon.xposedfakelocation.data.ROUTE_MAX_DWELL_SECONDS
import com.noobexon.xposedfakelocation.data.ROUTE_MAX_VARIATION_PERCENT
import com.noobexon.xposedfakelocation.data.ROUTE_MIN_POINTS
import com.noobexon.xposedfakelocation.data.model.JoystickSettings
import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.data.model.RouteSegment
import com.noobexon.xposedfakelocation.data.model.resolveMps
import com.noobexon.xposedfakelocation.data.model.segmentSettings
import com.noobexon.xposedfakelocation.data.model.travelSegments

/** One straight stretch of a [RoutePlan], in travel direction. */
data class Leg(
    val startLatitude: Double,
    val startLongitude: Double,
    val endLatitude: Double,
    val endLongitude: Double,
    val distanceM: Double,
    val bearingDegrees: Float,
    val speedMps: Float,
    /** Speed jitter as a fraction, 0.0..0.5. */
    val variation: Double,
    val dwellSeconds: Double,
    /** 1-based displayed number of the point this leg arrives at. */
    val endPointNumber: Int,
)

/** A route resolved for playback: legs in travel order with speeds fixed from the current settings. */
class RoutePlan(val legs: List<Leg>, val loop: Boolean) {
    init {
        require(legs.isNotEmpty()) { "A route plan needs at least one leg" }
    }

    private val legStarts = DoubleArray(legs.size).also { starts ->
        var acc = 0.0
        legs.forEachIndexed { i, leg ->
            starts[i] = acc
            acc += leg.distanceM
        }
    }

    val totalDistanceM: Double = legs.sumOf { it.distanceM }

    /** Base speeds plus waits; ignores variation and the playback multiplier. */
    val estimatedDurationS: Double = legs.sumOf { it.distanceM / it.speedMps + it.dwellSeconds }

    val startLatitude: Double get() = legs.first().startLatitude
    val startLongitude: Double get() = legs.first().startLongitude

    /** Distance from the route start to the start of leg [index]. */
    fun legStartM(index: Int): Double = legStarts[index]

    companion object {
        fun from(route: Route, settings: JoystickSettings): RoutePlan =
            RoutePlan(route.travelSegments().map { leg(route, it, settings) }, route.loop)

        private fun leg(route: Route, segment: RouteSegment, settings: JoystickSettings): Leg {
            val from = segment.from
            val to = segment.to
            val overrides = route.segmentSettings(from.id, to.id)
            return Leg(
                startLatitude = from.latitude,
                startLongitude = from.longitude,
                endLatitude = to.latitude,
                endLongitude = to.longitude,
                distanceM = GeoMath.distanceMeters(from.latitude, from.longitude, to.latitude, to.longitude),
                bearingDegrees = GeoMath.initialBearing(from.latitude, from.longitude, to.latitude, to.longitude).toFloat(),
                speedMps = (overrides.speed ?: route.defaultSpeed).resolveMps(settings),
                variation = overrides.variationPercent.coerceIn(0, ROUTE_MAX_VARIATION_PERCENT) / 100.0,
                dwellSeconds = overrides.dwellSeconds.coerceIn(0, ROUTE_MAX_DWELL_SECONDS).toDouble(),
                endPointNumber = segment.toNumber,
            )
        }
    }
}

/** What the route panel and favorites show about a route. */
data class RouteSummary(val pointCount: Int, val distanceM: Double, val durationS: Double) {
    companion object {
        fun of(route: Route, settings: JoystickSettings): RouteSummary {
            if (route.points.size < ROUTE_MIN_POINTS) return RouteSummary(route.points.size, 0.0, 0.0)
            val plan = RoutePlan.from(route, settings)
            return RouteSummary(route.points.size, plan.totalDistanceM, plan.estimatedDurationS)
        }
    }
}
