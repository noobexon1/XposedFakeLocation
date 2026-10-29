package com.noobexon.xposedfakelocation.manager.ui.map

import com.noobexon.xposedfakelocation.data.ROAD_PATH_DRAW_MAX_POINTS
import com.noobexon.xposedfakelocation.data.ROAD_PATH_DRAW_TOLERANCE_M
import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.data.model.travelSegments
import com.noobexon.xposedfakelocation.manager.route.PathGeometry
import com.noobexon.xposedfakelocation.manager.route.RoadFailure
import com.noobexon.xposedfakelocation.manager.route.RoadStatus
import com.noobexon.xposedfakelocation.manager.route.roadCoordinates
import com.noobexon.xposedfakelocation.manager.route.roadStatus

/** How a route point is colored: where playback starts, where it ends, or any other point. */
enum class PointRole { START, END, NORMAL }

/** A numbered route point to draw. */
data class DrawnPoint(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val number: Int,
    val role: PointRole,
)

/** The badge at a segment's halfway point. */
sealed interface SegmentMarker {
    val latitude: Double
    val longitude: Double

    /** Points along the travel direction; [bearingDegrees] is clockwise from north. */
    data class Arrow(override val latitude: Double, override val longitude: Double, val bearingDegrees: Double) : SegmentMarker

    /** The segment's road path failed to load. */
    data class Warning(override val latitude: Double, override val longitude: Double) : SegmentMarker
}

/** A segment line in travel order; [path] holds (lat, lon) pairs. */
data class DrawnSegment(
    val fromId: String,
    val toId: String,
    val path: List<Pair<Double, Double>>,
    val loading: Boolean,
    val dashed: Boolean,
    val marker: SegmentMarker?,
)

/** Everything a map draws for a route. */
data class RouteDrawing(val segments: List<DrawnSegment>, val points: List<DrawnPoint>)

/**
 * Builds the renderer-neutral drawing of [route]. Segments follow their stored road path
 * (simplified when long), else a straight line; the loop's closing segment is dashed.
 */
fun routeDrawing(route: Route, failures: Map<String, RoadFailure>): RouteDrawing {
    val segments = route.travelSegments().map { segment ->
        val a = segment.from
        val b = segment.to
        val status = route.roadStatus(a, b, failures)
        val path = route.roadCoordinates(a, b)
            ?.let { if (it.size > ROAD_PATH_DRAW_MAX_POINTS) PathGeometry.simplify(it, ROAD_PATH_DRAW_TOLERANCE_M) else it }
            ?: listOf(a.latitude to a.longitude, b.latitude to b.longitude)
        val marker = PathGeometry.halfway(path)?.let { half ->
            if (status is RoadStatus.Failed) {
                SegmentMarker.Warning(half.latitude, half.longitude)
            } else {
                SegmentMarker.Arrow(half.latitude, half.longitude, half.bearingDegrees)
            }
        }
        DrawnSegment(a.id, b.id, path, loading = status == RoadStatus.Loading, dashed = segment.closing, marker = marker)
    }
    val last = route.points.size
    val startNumber = if (route.reversed) last else 1
    val endNumber = if (route.loop) -1 else if (route.reversed) 1 else last
    val points = route.points.mapIndexed { index, point ->
        val number = index + 1
        val role = when (number) {
            startNumber -> PointRole.START
            endNumber -> PointRole.END
            else -> PointRole.NORMAL
        }
        DrawnPoint(point.id, point.latitude, point.longitude, number, role)
    }
    return RouteDrawing(segments, points)
}
