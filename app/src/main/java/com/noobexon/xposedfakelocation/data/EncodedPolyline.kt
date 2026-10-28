package com.noobexon.xposedfakelocation.data

import kotlin.math.pow
import kotlin.math.roundToLong

/** Google's encoded polyline format; OSRM's `polyline6` is the same with 6 decimal places. */
object EncodedPolyline {
    /** (latitude, longitude) pairs from [encoded], or `null` when it is malformed. */
    fun decode(encoded: String, precision: Int = 6): List<Pair<Double, Double>>? {
        val factor = 10.0.pow(precision)
        val points = ArrayList<Pair<Double, Double>>()
        var index = 0
        var lat = 0L
        var lon = 0L
        while (index < encoded.length) {
            val dLat = readValue(encoded, index) ?: return null
            val dLon = readValue(encoded, dLat.second) ?: return null
            lat += dLat.first
            lon += dLon.first
            index = dLon.second
            points += lat / factor to lon / factor
        }
        return points
    }

    /** Encodes (latitude, longitude) [points] with [precision] decimal places. */
    fun encode(points: List<Pair<Double, Double>>, precision: Int = 6): String {
        val factor = 10.0.pow(precision)
        val out = StringBuilder()
        var prevLat = 0L
        var prevLon = 0L
        for ((latitude, longitude) in points) {
            val lat = (latitude * factor).roundToLong()
            val lon = (longitude * factor).roundToLong()
            writeValue(out, lat - prevLat)
            writeValue(out, lon - prevLon)
            prevLat = lat
            prevLon = lon
        }
        return out.toString()
    }

    /** The signed value starting at [start] and the index after it, or `null` if truncated or invalid. */
    private fun readValue(encoded: String, start: Int): Pair<Long, Int>? {
        var result = 0L
        var shift = 0
        var index = start
        while (true) {
            if (index >= encoded.length || shift > 60) return null
            val chunk = encoded[index++].code - 63
            if (chunk !in 0..63) return null
            result = result or ((chunk and 0x1f).toLong() shl shift)
            shift += 5
            if (chunk < 0x20) break
        }
        val value = if ((result and 1L) != 0L) (result shr 1).inv() else result shr 1
        return value to index
    }

    private fun writeValue(out: StringBuilder, value: Long) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            out.append(((0x20 or (v and 0x1fL).toInt()) + 63).toChar())
            v = v ushr 5
        }
        out.append((v.toInt() + 63).toChar())
    }
}
