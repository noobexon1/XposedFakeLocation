package com.noobexon.xposedfakelocation.manager.ui.map

/**
 * Converts between osmdroid zoom (256 px tiles, the stored unit) and MapLibre zoom (512 px tiles),
 * so both renderers show the same area at the same stored zoom.
 */
object MapZoom {
    fun toMapLibre(osmdroidZoom: Double): Double = osmdroidZoom - 1

    fun fromMapLibre(mapLibreZoom: Double): Double = mapLibreZoom + 1
}
