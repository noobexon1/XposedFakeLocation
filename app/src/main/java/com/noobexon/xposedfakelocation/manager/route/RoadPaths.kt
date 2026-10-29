package com.noobexon.xposedfakelocation.manager.route

import com.noobexon.xposedfakelocation.data.EncodedPolyline
import com.noobexon.xposedfakelocation.data.model.RoadPath
import com.noobexon.xposedfakelocation.data.model.RoadProfile
import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.data.model.RoutePoint
import com.noobexon.xposedfakelocation.data.model.directedKey
import com.noobexon.xposedfakelocation.data.model.effectiveProfile
import com.noobexon.xposedfakelocation.data.model.followsRoads
import com.noobexon.xposedfakelocation.data.model.travelSegments
import com.noobexon.xposedfakelocation.manager.geo.RoadGeometry
import com.noobexon.xposedfakelocation.manager.geo.RoadRequest
import com.noobexon.xposedfakelocation.manager.geo.RoutingException

/** A failed fetch; it applies only while the route still needs exactly [request]. */
data class RoadFailure(val request: RoadRequest, val kind: RoutingException.Kind)

/** What a road segment shows for its travel direction. */
sealed interface RoadStatus {
    data class Ready(val distanceM: Double) : RoadStatus
    data object Loading : RoadStatus
    data class Failed(val kind: RoutingException.Kind) : RoadStatus
}

/** The request for travel [from] → [to], or `null` when both are the same place. */
fun roadRequest(from: RoutePoint, to: RoutePoint, profile: RoadProfile): RoadRequest? {
    if (from.latitude == to.latitude && from.longitude == to.longitude) return null
    return RoadRequest(directedKey(from.id, to.id), profile, from.latitude, from.longitude, to.latitude, to.longitude)
}

/** The stored path for this request's inputs and the fetched [geometry]. */
fun RoadRequest.toRoadPath(geometry: RoadGeometry): RoadPath =
    RoadPath(profile, fromLatitude, fromLongitude, toLatitude, toLongitude, geometry.polyline, geometry.distanceM)

/** Whether this path was fetched for exactly the inputs of [request]. */
fun RoadPath.matches(request: RoadRequest): Boolean =
    profile == request.profile &&
        fromLatitude == request.fromLatitude && fromLongitude == request.fromLongitude &&
        toLatitude == request.toLatitude && toLongitude == request.toLongitude

/** Both directions of every road segment, regardless of [Route.reversed]. */
fun Route.roadRequests(): List<RoadRequest> = travelSegments()
    .filter { followsRoads(it.from.id, it.to.id) }
    .flatMap { segment ->
        val profile = effectiveProfile(segment.from.id, segment.to.id)
        listOfNotNull(roadRequest(segment.from, segment.to, profile), roadRequest(segment.to, segment.from, profile))
    }
    .distinctBy { it.key }

/** Requests without a stored path for their exact inputs. */
fun Route.missingRoadPaths(): List<RoadRequest> = roadRequests().filter { roadPaths[it.key]?.matches(it) != true }

/** The usable stored path for travel [from] → [to], or `null` (straight segment, missing or stale). */
fun Route.roadPathFor(from: RoutePoint, to: RoutePoint): RoadPath? {
    if (!followsRoads(from.id, to.id)) return null
    val request = roadRequest(from, to, effectiveProfile(from.id, to.id)) ?: return null
    return roadPaths[request.key]?.takeIf { it.matches(request) }
}

/** [from], the road, then [to], without consecutive duplicates; `null` without a usable path. */
fun Route.roadCoordinates(from: RoutePoint, to: RoutePoint): List<Pair<Double, Double>>? {
    val road = roadPathFor(from, to)?.let { EncodedPolyline.decode(it.polyline) } ?: return null
    val all = ArrayList<Pair<Double, Double>>(road.size + 2)
    for (point in listOf(from.latitude to from.longitude) + road + listOf(to.latitude to to.longitude)) {
        if (all.lastOrNull() != point) all += point
    }
    return all
}

/** `null` when the segment doesn't need a road path. */
fun Route.roadStatus(from: RoutePoint, to: RoutePoint, failures: Map<String, RoadFailure>): RoadStatus? {
    if (!followsRoads(from.id, to.id)) return null
    val request = roadRequest(from, to, effectiveProfile(from.id, to.id)) ?: return null
    roadPaths[request.key]?.takeIf { it.matches(request) }?.let { return RoadStatus.Ready(it.distanceM) }
    failures[request.key]?.takeIf { it.request == request }?.let { return RoadStatus.Failed(it.kind) }
    return RoadStatus.Loading
}

/** Travel segments that should follow roads but have no usable path in their travel direction. */
fun Route.missingRoadSegmentCount(): Int = travelSegments().count { segment ->
    followsRoads(segment.from.id, segment.to.id) &&
        roadRequest(segment.from, segment.to, effectiveProfile(segment.from.id, segment.to.id)) != null &&
        roadPathFor(segment.from, segment.to) == null
}
