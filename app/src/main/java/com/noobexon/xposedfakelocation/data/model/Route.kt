package com.noobexon.xposedfakelocation.data.model

import com.noobexon.xposedfakelocation.data.EncodedPolyline
import com.noobexon.xposedfakelocation.data.JOYSTICK_MAX_SPEED_MPS
import com.noobexon.xposedfakelocation.data.JOYSTICK_MIN_SPEED_MPS
import com.noobexon.xposedfakelocation.data.ROUTE_MAX_DWELL_SECONDS
import com.noobexon.xposedfakelocation.data.ROUTE_MAX_VARIATION_PERCENT
import com.noobexon.xposedfakelocation.data.ROUTE_MIN_POINTS
import com.noobexon.xposedfakelocation.data.ROUTE_POINT_LABEL_MAX_LENGTH

/**
 * One route point. [id] stays the same across reorders and moves, so segment settings follow it.
 * [label] is the search result name the point was added from; moving the point clears it.
 */
data class RoutePoint(val id: String, val latitude: Double, val longitude: Double, val label: String? = null)

/** Trimmed and capped at [ROUTE_POINT_LABEL_MAX_LENGTH]; blank becomes `null`. */
fun sanitizeRouteLabel(label: String?): String? =
    label?.trim()?.take(ROUTE_POINT_LABEL_MAX_LENGTH)?.ifEmpty { null }

fun Route.withSanitizedLabels(): Route =
    copy(points = points.map { it.copy(label = sanitizeRouteLabel(it.label)) })

/** OSRM routing profile; [urlName] fills the routing server's `{profile}` placeholder. */
enum class RoadProfile(val urlName: String) { CAR("car"), BIKE("bike"), FOOT("foot") }

/** How fast a route, or one of its segments, moves. */
sealed interface SpeedChoice {
    /** Resolved from the joystick preset speeds in Settings when playback starts. */
    data class Preset(val preset: JoystickPreset) : SpeedChoice
    /** [profile] is only used on road segments; `null` means [RoadProfile.CAR]. */
    data class Custom(val speedMps: Float, val profile: RoadProfile? = null) : SpeedChoice
}

/** Per-segment overrides; the defaults mean "use the route's settings". */
data class SegmentSettings(
    /** `null` = the route's default speed. */
    val speed: SpeedChoice? = null,
    /** Wait at the end point reached in the current travel direction. */
    val dwellSeconds: Int = 0,
    /** Speed jitter of +/- this many percent. */
    val variationPercent: Int = 0,
    /** `null` = the route's [Route.followRoads]. */
    val followRoads: Boolean? = null,
)

/** A user-built route. List order of [points] is travel order (1..N). */
data class Route(
    val id: String,
    val name: String = "",
    val points: List<RoutePoint> = emptyList(),
    /** Keyed by [segmentKey]; a missing entry means [SegmentSettings] defaults. */
    val segments: Map<String, SegmentSettings> = emptyMap(),
    val defaultSpeed: SpeedChoice = SpeedChoice.Preset(JoystickPreset.WALK),
    /** Adds a closing segment from the last point back to the first. */
    val loop: Boolean = false,
    /** Plays N -> 1 without renumbering the points. */
    val reversed: Boolean = false,
    /** Route-wide default for segments. Old routes load as `false` and keep playing straight. */
    val followRoads: Boolean = false,
    /** Keyed by [directedKey]; both directions are stored for every road segment. */
    val roadPaths: Map<String, RoadPath> = emptyMap(),
)

/**
 * A fetched road path for one travel direction. The from/to coordinates and [profile] are the
 * inputs it was fetched for; it is stale once they no longer match the route.
 */
data class RoadPath(
    val profile: RoadProfile,
    val fromLatitude: Double,
    val fromLongitude: Double,
    val toLatitude: Double,
    val toLongitude: Double,
    /** OSRM polyline6 encoding of the road geometry (snapped to the road network). */
    val polyline: String,
    val distanceM: Double,
)

/** Key of [Route.roadPaths] for travel from [fromId] to [toId]. */
fun directedKey(fromId: String, toId: String): String = "$fromId>$toId"

/** The route being edited on the map. [sourceRouteId] is the saved route it came from, if any. */
data class RouteDraft(val route: Route, val sourceRouteId: String? = null, val dirty: Boolean = false)

/** Which mode the map screen edits. */
enum class MapMode { LOCATION, ROUTE }

/** A segment in travel order, with the 1-based displayed numbers of its ends. */
data class RouteSegment(
    val from: RoutePoint,
    val to: RoutePoint,
    val fromNumber: Int,
    val toNumber: Int,
    val closing: Boolean,
)

/** Direction-independent key, so a segment's settings apply in reverse too. */
fun segmentKey(a: String, b: String): String = if (a <= b) "$a|$b" else "$b|$a"

fun Route.segmentSettings(aId: String, bId: String): SegmentSettings =
    segments[segmentKey(aId, bId)] ?: SegmentSettings()

/** The segment's own speed, else the route default. */
fun Route.effectiveSpeed(aId: String, bId: String): SpeedChoice = segmentSettings(aId, bId).speed ?: defaultSpeed

/** Whether the segment follows roads: its own setting, else the route default. */
fun Route.followsRoads(aId: String, bId: String): Boolean = segmentSettings(aId, bId).followRoads ?: followRoads

/** The road profile of the segment's [effectiveSpeed]. */
fun Route.effectiveProfile(aId: String, bId: String): RoadProfile = effectiveSpeed(aId, bId).roadProfile()

/** Walk and run use foot paths, bike uses cycle paths, drive and unset Custom use car roads. */
fun SpeedChoice.roadProfile(): RoadProfile = when (this) {
    is SpeedChoice.Preset -> when (preset) {
        JoystickPreset.WALK, JoystickPreset.RUN -> RoadProfile.FOOT
        JoystickPreset.BIKE -> RoadProfile.BIKE
        JoystickPreset.DRIVE -> RoadProfile.CAR
    }
    is SpeedChoice.Custom -> profile ?: RoadProfile.CAR
}

/** Segments in travel order, honouring [Route.reversed] and [Route.loop]. Empty below two points. */
fun Route.travelSegments(): List<RouteSegment> {
    if (points.size < 2) return emptyList()
    val numbered = points.mapIndexed { index, point -> index + 1 to point }
    val ordered = if (reversed) numbered.asReversed() else numbered
    val open = ordered.zipWithNext().map { (a, b) ->
        RouteSegment(a.second, b.second, a.first, b.first, closing = false)
    }
    if (!loop) return open
    val (lastNumber, last) = ordered.last()
    val (firstNumber, first) = ordered.first()
    return open + RouteSegment(last, first, lastNumber, firstNumber, closing = true)
}

fun SpeedChoice.resolveMps(settings: JoystickSettings): Float = when (this) {
    is SpeedChoice.Preset -> settings.presetSpeedMps(preset)
    is SpeedChoice.Custom -> speedMps
}.coerceIn(JOYSTICK_MIN_SPEED_MPS, JOYSTICK_MAX_SPEED_MPS)

// Gson bypasses Kotlin null-safety, so stored routes are checked for nulls explicitly.
@Suppress("SENSELESS_COMPARISON")
fun SpeedChoice.isValid(): Boolean = when (this) {
    is SpeedChoice.Preset -> preset != null
    is SpeedChoice.Custom -> speedMps.isFinite() && speedMps in JOYSTICK_MIN_SPEED_MPS..JOYSTICK_MAX_SPEED_MPS
}

fun SegmentSettings.isValid(): Boolean =
    (speed?.isValid() ?: true) &&
        dwellSeconds in 0..ROUTE_MAX_DWELL_SECONDS &&
        variationPercent in 0..ROUTE_MAX_VARIATION_PERCENT

@Suppress("SENSELESS_COMPARISON")
fun Route.isValid(requireName: Boolean = true, minPoints: Int = ROUTE_MIN_POINTS): Boolean {
    if (id == null || id.isBlank() || name == null || points == null || segments == null || defaultSpeed == null) return false
    if (requireName && name.isBlank()) return false
    if (points.size < minPoints) return false
    val pointsOk = points.all {
        it != null && it.id != null &&
            it.latitude.isFinite() && it.longitude.isFinite() &&
            it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0
    }
    return pointsOk && defaultSpeed.isValid() && segments.values.all { it != null && it.isValid() }
}

/** Finite in-range coordinates and distance, and a polyline that decodes to at least two points. */
@Suppress("SENSELESS_COMPARISON")
fun RoadPath.isValid(): Boolean {
    if (profile == null || polyline == null) return false
    val latitudesOk = listOf(fromLatitude, toLatitude).all { it.isFinite() && it in -90.0..90.0 }
    val longitudesOk = listOf(fromLongitude, toLongitude).all { it.isFinite() && it in -180.0..180.0 }
    val distanceOk = distanceM.isFinite() && distanceM >= 0.0
    return latitudesOk && longitudesOk && distanceOk && (EncodedPolyline.decode(polyline)?.size ?: 0) >= 2
}

/** Drops unusable stored paths (bad key, data or polyline); never rejects the route itself. */
@Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
fun Route.sanitizeRoadPaths(): Route {
    val ids = points.map { it.id }.toSet()
    val stored: Map<String, RoadPath> = roadPaths ?: emptyMap()
    val kept = stored.filter { (key, path) ->
        val ends = key.split('>')
        ends.size == 2 && ends[0] in ids && ends[1] in ids && path != null && path.isValid()
    }
    return copy(roadPaths = kept)
}
