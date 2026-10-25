package com.noobexon.xposedfakelocation.manager.route

import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.data.model.RoutePoint
import com.noobexon.xposedfakelocation.data.model.SegmentSettings
import com.noobexon.xposedfakelocation.data.model.segmentKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RouteEditorTest {
    private fun route(vararg ids: String) =
        Route(id = "r", points = ids.mapIndexed { i, id -> RoutePoint(id, i.toDouble(), 0.0) })

    private fun ids(route: Route) = route.points.map { it.id }

    @Test
    fun appendAddsPointAtTheEnd() {
        val updated = RouteEditor.append(route("a"), 5.0, 6.0, "b")
        assertEquals(listOf("a", "b"), ids(updated))
        assertEquals(RoutePoint("b", 5.0, 6.0), updated.points.last())
    }

    @Test
    fun deleteRemovesPointButKeepsSegmentSettingsUntilPruned() {
        val withSettings = route("a", "b", "c").copy(
            segments = mapOf(
                segmentKey("a", "b") to SegmentSettings(dwellSeconds = 1),
                segmentKey("b", "c") to SegmentSettings(dwellSeconds = 2),
            )
        )
        val deleted = RouteEditor.delete(withSettings, "c")
        assertEquals(listOf("a", "b"), ids(deleted))
        assertEquals(2, deleted.segments.size)
        assertEquals(setOf(segmentKey("a", "b")), RouteEditor.pruneSegments(deleted).segments.keys)
    }

    @Test
    fun moveKeepsIdAndOrder() {
        val moved = RouteEditor.move(route("a", "b"), "a", 9.0, 8.0)
        assertEquals(listOf("a", "b"), ids(moved))
        assertEquals(RoutePoint("a", 9.0, 8.0), moved.points.first())
    }

    @Test
    fun moveToEarlierAndLaterReorder() {
        val r = route("a", "b", "c", "d")
        assertEquals(listOf("c", "a", "b", "d"), ids(RouteEditor.moveTo(r, "c", 1)))
        assertEquals(listOf("b", "c", "d", "a"), ids(RouteEditor.moveTo(r, "a", 99)))
        assertEquals(listOf("a", "c", "b", "d"), ids(RouteEditor.moveEarlier(r, "c")))
        assertEquals(listOf("a", "c", "b", "d"), ids(RouteEditor.moveLater(r, "b")))
        assertSame(r, RouteEditor.moveEarlier(r, "a"))
        assertSame(r, RouteEditor.moveLater(r, "d"))
    }

    @Test
    fun setOrderAppliesIdsAndIgnoresMismatchedLists() {
        val r = route("a", "b", "c")
        assertEquals(listOf("c", "a", "b"), ids(RouteEditor.setOrder(r, listOf("c", "a", "b"))))
        assertSame(r, RouteEditor.setOrder(r, listOf("a", "b")))
    }

    @Test
    fun setSegmentStoresOverridesAndDropsDefaults() {
        val r = route("a", "b")
        val custom = SegmentSettings(variationPercent = 20)
        val withCustom = RouteEditor.setSegment(r, "b", "a", custom)
        assertEquals(custom, withCustom.segments[segmentKey("a", "b")])
        assertEquals(emptyMap<String, SegmentSettings>(), RouteEditor.setSegment(withCustom, "a", "b", SegmentSettings()).segments)
    }

    @Test
    fun pruneKeepsClosingSegmentWhenLooping() {
        val r = route("a", "b", "c").copy(
            loop = true,
            segments = mapOf(segmentKey("c", "a") to SegmentSettings(dwellSeconds = 3)),
        )
        assertEquals(r.segments, RouteEditor.pruneSegments(r).segments)
        assertEquals(emptyMap<String, SegmentSettings>(), RouteEditor.pruneSegments(r.copy(loop = false)).segments)
    }
}
