package com.noobexon.xposedfakelocation.manager.route

import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.data.model.RoutePoint
import com.noobexon.xposedfakelocation.data.model.SegmentSettings
import com.noobexon.xposedfakelocation.data.model.segmentKey
import com.noobexon.xposedfakelocation.data.model.travelSegments

/**
 * Pure edits on a [Route]. Segment settings are keyed by point IDs, so they survive reorders for
 * pairs that are still neighbours; [pruneSegments] drops the rest when a route is saved.
 */
object RouteEditor {
    fun append(route: Route, latitude: Double, longitude: Double, id: String): Route =
        route.copy(points = route.points + RoutePoint(id, latitude, longitude))

    fun delete(route: Route, id: String): Route =
        route.copy(points = route.points.filterNot { it.id == id })

    fun move(route: Route, id: String, latitude: Double, longitude: Double): Route =
        route.copy(points = route.points.map { if (it.id == id) it.copy(latitude = latitude, longitude = longitude) else it })

    /** Reorders to [ids]; returns [route] unchanged unless [ids] lists exactly its points. */
    fun setOrder(route: Route, ids: List<String>): Route {
        val byId = route.points.associateBy { it.id }
        val ordered = ids.mapNotNull { byId[it] }
        if (ordered.size != route.points.size || ordered.toSet().size != ordered.size) return route
        return route.copy(points = ordered)
    }

    /** Moves the point to 1-based position [number], clamped to the route. */
    fun moveTo(route: Route, id: String, number: Int): Route {
        val from = route.points.indexOfFirst { it.id == id }
        if (from < 0) return route
        val to = (number - 1).coerceIn(0, route.points.lastIndex)
        if (from == to) return route
        val points = route.points.toMutableList()
        points.add(to, points.removeAt(from))
        return route.copy(points = points)
    }

    fun moveEarlier(route: Route, id: String): Route {
        val index = route.points.indexOfFirst { it.id == id }
        return if (index <= 0) route else moveTo(route, id, number = index)
    }

    fun moveLater(route: Route, id: String): Route {
        val index = route.points.indexOfFirst { it.id == id }
        return if (index < 0 || index == route.points.lastIndex) route else moveTo(route, id, number = index + 2)
    }

    fun setSegment(route: Route, aId: String, bId: String, settings: SegmentSettings): Route {
        val key = segmentKey(aId, bId)
        val segments = if (settings == SegmentSettings()) route.segments - key else route.segments + (key to settings)
        return route.copy(segments = segments)
    }

    fun pruneSegments(route: Route): Route {
        val live = route.travelSegments().map { segmentKey(it.from.id, it.to.id) }.toSet()
        return route.copy(segments = route.segments.filterKeys { it in live })
    }
}
