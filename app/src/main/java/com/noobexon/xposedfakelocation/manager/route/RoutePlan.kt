package com.noobexon.xposedfakelocation.manager.route

import com.noobexon.xposedfakelocation.data.GeoMath
import com.noobexon.xposedfakelocation.data.ROUTE_MAX_DWELL_SECONDS
import com.noobexon.xposedfakelocation.data.ROUTE_MAX_VARIATION_PERCENT
import com.noobexon.xposedfakelocation.data.ROUTE_MIN_POINTS
import com.noobexon.xposedfakelocation.data.model.JoystickSettings
import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.data.model.RouteSegment
import com.noobexon.xposedfakelocation.data.model.effectiveSpeed
import com.noobexon.xposedfakelocation.data.model.resolveMps
import com.noobexon.xposedfakelocation.data.model.segmentSettings
import com.noobexon.xposedfakelocation.data.model.travelSegments

/** One straight stretch of a [RoutePlan], in travel direction. Road segments become many legs. */
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
            RoutePlan(route.travelSegments().flatMap { legs(route, it, settings) }, route.loop)

        /** One leg for a straight segment; one per road piece when a usable road path is stored. */
        private fun legs(route: Route, segment: RouteSegment, settings: JoystickSettings): List<Leg> {
            val from = segment.from
            val to = segment.to
            val overrides = route.segmentSettings(from.id, to.id)
            val speed = route.effectiveSpeed(from.id, to.id).resolveMps(settings)
            val variation = overrides.variationPercent.coerceIn(0, ROUTE_MAX_VARIATION_PERCENT) / 100.0
            val dwell = overrides.dwellSeconds.coerceIn(0, ROUTE_MAX_DWELL_SECONDS).toDouble()
            val coordinates = route.roadCoordinates(from, to)?.takeIf { it.size >= 2 }
                ?: listOf(from.latitude to from.longitude, to.latitude to to.longitude)
            val lastPiece = coordinates.size - 2
            return coordinates.zipWithNext().mapIndexed { i, (start, end) ->
                Leg(
                    startLatitude = start.first,
                    startLongitude = start.second,
                    endLatitude = end.first,
                    endLongitude = end.second,
                    distanceM = GeoMath.distanceMeters(start.first, start.second, end.first, end.second),
                    bearingDegrees = GeoMath.initialBearing(start.first, start.second, end.first, end.second).toFloat(),
                    speedMps = speed,
                    variation = variation,
                    dwellSeconds = if (i == lastPiece) dwell else 0.0,
                    endPointNumber = segment.toNumber,
                )
            }
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
