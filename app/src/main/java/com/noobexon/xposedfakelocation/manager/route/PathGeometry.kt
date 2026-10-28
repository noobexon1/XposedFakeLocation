package com.noobexon.xposedfakelocation.manager.route

import com.noobexon.xposedfakelocation.data.GeoMath
import kotlin.math.cos
import kotlin.math.hypot

/** A point on a path with the bearing of the piece it lies on. */
data class Halfway(val latitude: Double, val longitude: Double, val bearingDegrees: Double)

/** Geometry on (latitude, longitude) paths. */
object PathGeometry {
    private const val METERS_PER_DEGREE = 111_320.0

    /** Douglas–Peucker on a local flat projection; keeps both ends. For drawing only. */
    fun simplify(points: List<Pair<Double, Double>>, toleranceM: Double): List<Pair<Double, Double>> {
        if (points.size < 3) return points
        val lonScale = METERS_PER_DEGREE * cos(Math.toRadians(points[0].first))
        val xs = DoubleArray(points.size) { points[it].second * lonScale }
        val ys = DoubleArray(points.size) { points[it].first * METERS_PER_DEGREE }
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.lastIndex] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to points.lastIndex)
        while (stack.isNotEmpty()) {
            val (start, end) = stack.removeLast()
            var farthest = -1
            var maxDistance = 0.0
            for (i in start + 1 until end) {
                val d = distanceToSegment(xs[i], ys[i], xs[start], ys[start], xs[end], ys[end])
                if (d > maxDistance) {
                    maxDistance = d
                    farthest = i
                }
            }
            if (farthest >= 0 && maxDistance > toleranceM) {
                keep[farthest] = true
                stack.addLast(start to farthest)
                stack.addLast(farthest to end)
            }
        }
        return points.filterIndexed { i, _ -> keep[i] }
    }

    /** The point at half of the path's length, or `null` below two points or with zero length. */
    fun halfway(points: List<Pair<Double, Double>>): Halfway? {
        if (points.size < 2) return null
        val lengths = points.zipWithNext { a, b -> GeoMath.distanceMeters(a.first, a.second, b.first, b.second) }
        val total = lengths.sum()
        if (total <= 0.0) return null
        var remaining = total / 2
        var lastPiece = 0
        lengths.forEachIndexed { i, length ->
            if (length > 0.0) {
                lastPiece = i
                if (remaining <= length) return pointOn(points[i], points[i + 1], remaining)
            }
            remaining -= length
        }
        val a = points[lastPiece]
        val b = points[lastPiece + 1]
        return Halfway(b.first, b.second, GeoMath.initialBearing(a.first, a.second, b.first, b.second))
    }

    private fun pointOn(a: Pair<Double, Double>, b: Pair<Double, Double>, distanceM: Double): Halfway {
        val bearing = GeoMath.initialBearing(a.first, a.second, b.first, b.second)
        val (lat, lon) = GeoMath.destination(a.first, a.second, bearing, distanceM)
        return Halfway(lat, lon, bearing)
    }

    private fun distanceToSegment(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0.0, 1.0)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }
}
