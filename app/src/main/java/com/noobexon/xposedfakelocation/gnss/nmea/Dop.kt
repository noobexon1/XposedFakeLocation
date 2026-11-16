package com.noobexon.xposedfakelocation.gnss.nmea

import com.noobexon.xposedfakelocation.gnss.SkySatellite
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Dilution of precision of a fix, from the geometry of the satellites used (one receiver clock). */
data class Dop(
    val pdop: Double,
    val hdop: Double,
    val vdop: Double,
) {
    companion object {
        private const val UNKNOWNS = 4
        private const val SINGULAR = 1e-12

        /** The DOP of [satellites] (one entry per satellite), or `null` for fewer than 4 or a degenerate geometry. */
        fun of(satellites: List<SkySatellite>): Dop? {
            if (satellites.size < UNKNOWNS) return null
            val normal = Array(UNKNOWNS) { DoubleArray(UNKNOWNS) }
            satellites.forEach { satellite ->
                val elevation = Math.toRadians(satellite.elevationDeg.toDouble())
                val azimuth = Math.toRadians(satellite.azimuthDeg.toDouble())
                val row = doubleArrayOf(
                    -cos(elevation) * sin(azimuth),
                    -cos(elevation) * cos(azimuth),
                    -sin(elevation),
                    1.0,
                )
                for (i in 0 until UNKNOWNS) for (j in 0 until UNKNOWNS) normal[i][j] += row[i] * row[j]
            }
            val q = invert(normal) ?: return null
            return Dop(
                pdop = sqrt(q[0][0] + q[1][1] + q[2][2]),
                hdop = sqrt(q[0][0] + q[1][1]),
                vdop = sqrt(q[2][2]),
            )
        }

        /** Gauss-Jordan inverse with partial pivoting; `null` when [matrix] is singular. */
        private fun invert(matrix: Array<DoubleArray>): Array<DoubleArray>? {
            val n = matrix.size
            val a = Array(n) { matrix[it].copyOf() }
            val inverse = Array(n) { row -> DoubleArray(n) { column -> if (row == column) 1.0 else 0.0 } }
            for (column in 0 until n) {
                val pivot = (column until n).maxBy { abs(a[it][column]) }
                if (abs(a[pivot][column]) < SINGULAR) return null
                a[column] = a[pivot].also { a[pivot] = a[column] }
                inverse[column] = inverse[pivot].also { inverse[pivot] = inverse[column] }
                val scale = a[column][column]
                for (k in 0 until n) {
                    a[column][k] /= scale
                    inverse[column][k] /= scale
                }
                for (row in 0 until n) {
                    if (row == column) continue
                    val factor = a[row][column]
                    for (k in 0 until n) {
                        a[row][k] -= factor * a[column][k]
                        inverse[row][k] -= factor * inverse[column][k]
                    }
                }
            }
            return inverse
        }
    }
}
