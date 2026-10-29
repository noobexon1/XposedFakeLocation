package com.noobexon.xposedfakelocation.manager.ui.map

import com.noobexon.xposedfakelocation.data.OPENFREEMAP_DARK_STYLE_URL
import com.noobexon.xposedfakelocation.data.OPENFREEMAP_LIGHT_STYLE_URL
import org.junit.Assert.assertEquals
import org.junit.Test

class MapStyleTest {
    @Test
    fun blankUrlFollowsTheTheme() {
        assertEquals(OPENFREEMAP_LIGHT_STYLE_URL, resolveStyleUrl("", dark = false))
        assertEquals(OPENFREEMAP_DARK_STYLE_URL, resolveStyleUrl("", dark = true))
    }

    @Test
    fun customUrlIsUsedForBothThemes() {
        val custom = "https://tiles.example.org/style.json"
        assertEquals(custom, resolveStyleUrl(custom, dark = false))
        assertEquals(custom, resolveStyleUrl(custom, dark = true))
    }

    @Test
    fun zoomConvertsBetweenTileSizes() {
        assertEquals(17.0, MapZoom.toMapLibre(18.0), 0.0)
        assertEquals(18.0, MapZoom.fromMapLibre(17.0), 0.0)
        assertEquals(12.25, MapZoom.fromMapLibre(MapZoom.toMapLibre(12.25)), 0.0)
    }
}
