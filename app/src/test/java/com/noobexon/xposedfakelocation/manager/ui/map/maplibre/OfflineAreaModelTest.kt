package com.noobexon.xposedfakelocation.manager.ui.map.maplibre

import com.noobexon.xposedfakelocation.data.OPENFREEMAP_DARK_STYLE_URL
import com.noobexon.xposedfakelocation.data.OPENFREEMAP_LIGHT_STYLE_URL
import com.noobexon.xposedfakelocation.data.model.OfflineAreaMetadata
import com.noobexon.xposedfakelocation.data.model.OfflineAreaStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineAreaModelTest {
    private val world = AreaBounds(north = 85.0, east = 180.0, south = -85.0, west = -180.0)

    @Test
    fun defaultStylesAreLightAndDark() {
        assertEquals(
            listOf(
                OfflineStyle(OfflineAreaStyle.LIGHT, OPENFREEMAP_LIGHT_STYLE_URL),
                OfflineStyle(OfflineAreaStyle.DARK, OPENFREEMAP_DARK_STYLE_URL),
            ),
            offlineStyles(""),
        )
        assertEquals(listOf(OfflineStyle(OfflineAreaStyle.CUSTOM, "https://x.org/s.json")), offlineStyles("https://x.org/s.json"))
    }

    @Test
    fun zoomRangeStartsAtTheCurrentZoomAndStopsAt16() {
        assertEquals(12..16, offlineZoomRange(12.7))
        assertEquals(16..16, offlineZoomRange(17.2))
        assertEquals(0..16, offlineZoomRange(-0.5))
    }

    @Test
    fun worldTileCounts() {
        assertEquals(1L, estimateTileCount(world, 0, 0))
        assertEquals(21L, estimateTileCount(world, 0, 2))
    }

    @Test
    fun boxTileCounts() {
        val box = AreaBounds(north = 80.0, east = 170.0, south = 10.0, west = 10.0)
        assertEquals(1L, estimateTileCount(box, 1, 1))
        assertEquals(5L, estimateTileCount(box, 1, 2))
    }

    @Test
    fun boxAcrossTheAntimeridian() {
        val box = AreaBounds(north = 10.0, east = -170.0, south = -10.0, west = 170.0)
        assertEquals(4L, estimateTileCount(box, 1, 1))
    }

    private fun metadata(areaId: String, style: String, createdAt: Long = 1L, url: String = OPENFREEMAP_LIGHT_STYLE_URL) =
        OfflineAreaMetadata(areaId, "Area $areaId", createdAt, style, url)

    private fun region(id: Long, metadata: OfflineAreaMetadata?, done: Long, required: Long, complete: Boolean = false, active: Boolean = false) =
        StoredRegion(id, metadata, completedBytes = done * 10, completedCount = done, requiredCount = required, complete = complete, active = active)

    @Test
    fun regionsOfOneAreaAreGrouped() {
        val areas = groupOfflineAreas(
            listOf(
                region(1, metadata("a", OfflineAreaStyle.LIGHT), done = 100, required = 100, complete = true),
                region(2, metadata("a", OfflineAreaStyle.DARK), done = 0, required = 100, active = true),
            )
        )
        val area = areas.single()
        assertEquals("a", area.key)
        assertEquals("Area a", area.name)
        assertEquals("OpenFreeMap", area.source)
        assertEquals(1000L, area.sizeBytes)
        assertEquals(listOf(1L, 2L), area.regionIds)
        assertEquals(OfflineAreaStatus.Downloading(50), area.status)
    }

    @Test
    fun statusIsCompleteOrIncomplete() {
        val complete = groupOfflineAreas(listOf(region(1, metadata("a", OfflineAreaStyle.LIGHT), 5, 5, complete = true)))
        assertEquals(OfflineAreaStatus.Complete, complete.single().status)
        val stopped = groupOfflineAreas(listOf(region(1, metadata("a", OfflineAreaStyle.LIGHT), 2, 5)))
        assertEquals(OfflineAreaStatus.Incomplete, stopped.single().status)
    }

    @Test
    fun unreadableRegionsAreTheirOwnUnknownArea() {
        val area = groupOfflineAreas(listOf(region(7, null, 1, 1, complete = true))).single()
        assertEquals("region:7", area.key)
        assertNull(area.name)
        assertNull(area.createdAt)
        assertNull(area.source)
    }

    @Test
    fun customStyleShowsItsHostAndNewestComesFirst() {
        val areas = groupOfflineAreas(
            listOf(
                region(1, metadata("old", OfflineAreaStyle.LIGHT, createdAt = 1L), 1, 1, complete = true),
                region(2, metadata("new", OfflineAreaStyle.CUSTOM, createdAt = 2L, url = "https://tiles.example.org/s.json"), 1, 1, complete = true),
            )
        )
        assertEquals(listOf("new", "old"), areas.map { it.key })
        assertEquals("tiles.example.org", areas.first().source)
    }

    @Test
    fun percentIsClamped() {
        assertEquals(0, downloadPercent(5, 0))
        assertEquals(33, downloadPercent(1, 3))
        assertEquals(100, downloadPercent(7, 5))
    }
}
