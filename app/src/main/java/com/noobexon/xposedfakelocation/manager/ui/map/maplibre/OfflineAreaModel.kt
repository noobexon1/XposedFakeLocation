package com.noobexon.xposedfakelocation.manager.ui.map.maplibre

import com.noobexon.xposedfakelocation.data.OFFLINE_MAX_ZOOM
import com.noobexon.xposedfakelocation.data.OPENFREEMAP_DARK_STYLE_URL
import com.noobexon.xposedfakelocation.data.OPENFREEMAP_LIGHT_STYLE_URL
import com.noobexon.xposedfakelocation.data.model.OfflineAreaMetadata
import com.noobexon.xposedfakelocation.data.model.OfflineAreaStyle
import java.net.URI
import java.net.URISyntaxException
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** A latitude/longitude box; [west] > [east] when it crosses the antimeridian. */
data class AreaBounds(val north: Double, val east: Double, val south: Double, val west: Double)

/** What the MapLibre map shows: the bounding box of the visible region, its center and zoom (MapLibre units). */
data class VisibleArea(
    val bounds: AreaBounds,
    val centerLatitude: Double,
    val centerLongitude: Double,
    val zoom: Double,
)

/** A style to download for offline use; [kind] is one of [OfflineAreaStyle]. */
data class OfflineStyle(val kind: String, val url: String)

/** OpenFreeMap light and dark for a blank [customUrl], else just the custom style. */
fun offlineStyles(customUrl: String): List<OfflineStyle> =
    if (customUrl.isBlank()) {
        listOf(
            OfflineStyle(OfflineAreaStyle.LIGHT, OPENFREEMAP_LIGHT_STYLE_URL),
            OfflineStyle(OfflineAreaStyle.DARK, OPENFREEMAP_DARK_STYLE_URL),
        )
    } else {
        listOf(OfflineStyle(OfflineAreaStyle.CUSTOM, customUrl))
    }

/** From the current zoom (floored) up to [OFFLINE_MAX_ZOOM]. */
fun offlineZoomRange(mapLibreZoom: Double): IntRange =
    floor(mapLibreZoom).toInt().coerceIn(0, OFFLINE_MAX_ZOOM)..OFFLINE_MAX_ZOOM

/** Number of web-mercator tiles covering [bounds] at each zoom in [minZoom]..[maxZoom]. */
fun estimateTileCount(bounds: AreaBounds, minZoom: Int, maxZoom: Int): Long {
    var total = 0L
    for (zoom in minZoom..maxZoom) {
        val n = 1 shl zoom
        val west = tileX(bounds.west, n)
        val east = tileX(bounds.east, n)
        val columns = if (bounds.west <= bounds.east) east - west + 1 else (n - west) + east + 1
        val rows = tileY(bounds.south, n) - tileY(bounds.north, n) + 1
        total += columns.toLong() * rows
    }
    return total
}

private const val MAX_MERCATOR_LATITUDE = 85.05112878

private fun tileX(longitude: Double, n: Int): Int =
    floor((longitude + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)

private fun tileY(latitude: Double, n: Int): Int {
    val radians = latitude.coerceIn(-MAX_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE) * PI / 180.0
    val y = (1.0 - ln(tan(radians) + 1.0 / cos(radians)) / PI) / 2.0 * n
    return floor(y).toInt().coerceIn(0, n - 1)
}

/** A stored MapLibre region: decoded [metadata] (`null` if unreadable) and download progress. */
data class StoredRegion(
    val regionId: Long,
    val metadata: OfflineAreaMetadata?,
    val completedBytes: Long,
    val completedCount: Long,
    val requiredCount: Long,
    val complete: Boolean,
    val active: Boolean,
)

/** Download state of an offline area. */
sealed interface OfflineAreaStatus {
    data object Complete : OfflineAreaStatus
    data class Downloading(val percent: Int) : OfflineAreaStatus
    /** Stopped before finishing; can be resumed. */
    data object Incomplete : OfflineAreaStatus
}

/**
 * One offline area: every stored region sharing an area id (one per style). [name], [createdAt]
 * and [source] are `null` for regions whose metadata can't be read.
 */
data class OfflineArea(
    val key: String,
    val name: String?,
    val createdAt: Long?,
    val source: String?,
    val sizeBytes: Long,
    val regionIds: List<Long>,
    val status: OfflineAreaStatus,
)

/** The area a region belongs to; unreadable regions are each their own area. */
fun offlineAreaKey(regionId: Long, metadata: OfflineAreaMetadata?): String = metadata?.areaId ?: "region:$regionId"

/** Groups regions into areas, newest first. */
fun groupOfflineAreas(regions: List<StoredRegion>): List<OfflineArea> =
    regions.groupBy { offlineAreaKey(it.regionId, it.metadata) }
        .map { (key, group) ->
            val metadata = group.firstNotNullOfOrNull { it.metadata }
            val status = when {
                group.all { it.complete } -> OfflineAreaStatus.Complete
                group.any { it.active } -> OfflineAreaStatus.Downloading(
                    downloadPercent(group.sumOf { it.completedCount }, group.sumOf { it.requiredCount })
                )
                else -> OfflineAreaStatus.Incomplete
            }
            OfflineArea(
                key = key,
                name = metadata?.name,
                createdAt = metadata?.createdAt,
                source = metadata?.let(::offlineSourceLabel),
                sizeBytes = group.sumOf { it.completedBytes },
                regionIds = group.map { it.regionId },
                status = status,
            )
        }
        .sortedByDescending { it.createdAt ?: Long.MIN_VALUE }

/** [completed] out of [required] as 0..100; 0 while the total is unknown. */
fun downloadPercent(completed: Long, required: Long): Int =
    if (required <= 0) 0 else (completed * 100 / required).toInt().coerceIn(0, 100)

private fun offlineSourceLabel(metadata: OfflineAreaMetadata): String {
    if (metadata.style != OfflineAreaStyle.CUSTOM) return "OpenFreeMap"
    val host = try {
        URI(metadata.styleUrl).host
    } catch (e: URISyntaxException) {
        null
    }
    return host ?: metadata.styleUrl
}
