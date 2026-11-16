package com.noobexon.xposedfakelocation.gnss.nmea

import kotlin.math.floor

/**
 * EGM96 geoid height above the WGS-84 ellipsoid, interpolated bilinearly on a 5 degree grid. Errors are about
 * a metre on average and a few metres near mountain ranges; chips use similarly coarse models.
 */
object Geoid {
    private const val STEP_DEGREES = 5.0
    private const val ROWS = 37
    private const val COLUMNS = 72

    fun separationMeters(latitude: Double, longitude: Double): Double {
        val row = (90.0 - latitude.coerceIn(-90.0, 90.0)) / STEP_DEGREES
        val row0 = floor(row).toInt().coerceAtMost(ROWS - 2)
        val rowFraction = row - row0
        val column = ((longitude % 360.0 + 360.0) % 360.0) / STEP_DEGREES
        val column0 = floor(column).toInt() % COLUMNS
        val column1 = (column0 + 1) % COLUMNS
        val columnFraction = column - floor(column)
        fun at(r: Int, c: Int): Double = GEOID_GRID_DECIMETRES[r * COLUMNS + c] / 10.0
        val north = at(row0, column0) * (1 - columnFraction) + at(row0, column1) * columnFraction
        val south = at(row0 + 1, column0) * (1 - columnFraction) + at(row0 + 1, column1) * columnFraction
        return north * (1 - rowFraction) + south * rowFraction
    }
}
