package com.noobexon.xposedfakelocation.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MapRendererTest {
    @Test
    fun knownTagsParse() {
        assertEquals(MapRenderer.OSMDROID, MapRenderer.fromTag("osmdroid"))
        assertEquals(MapRenderer.MAPLIBRE, MapRenderer.fromTag("maplibre"))
    }

    @Test
    fun unknownOrMissingTagsFallBackToMapLibre() {
        assertEquals(MapRenderer.MAPLIBRE, MapRenderer.fromTag(null))
        assertEquals(MapRenderer.MAPLIBRE, MapRenderer.fromTag(""))
        assertEquals(MapRenderer.MAPLIBRE, MapRenderer.fromTag("OSMDROID"))
    }
}
