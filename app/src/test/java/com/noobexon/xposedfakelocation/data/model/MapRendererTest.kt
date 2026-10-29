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
    fun unknownOrMissingTagsFallBackToOsmdroid() {
        assertEquals(MapRenderer.OSMDROID, MapRenderer.fromTag(null))
        assertEquals(MapRenderer.OSMDROID, MapRenderer.fromTag(""))
        assertEquals(MapRenderer.OSMDROID, MapRenderer.fromTag("MAPLIBRE"))
    }
}
