package com.noobexon.xposedfakelocation.manager.ui.map

import com.noobexon.xposedfakelocation.data.DEFAULT_MAP_ZOOM
import com.noobexon.xposedfakelocation.data.WORLD_MAP_ZOOM
import com.noobexon.xposedfakelocation.manager.geo.LatLon
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class InitialCameraTest {
    private val marker = LatLon(1.0, 2.0)
    private val device = LatLon(3.0, 4.0)

    @Test
    fun savedMarkerWins() = runTest {
        var asked = false
        val camera = resolveInitialCamera(marker, savedZoom = 15.0, lastKnown = { asked = true; device }, liveFix = { device })
        assertEquals(InitialCamera(marker, 15.0, InitialCameraSource.MARKER), camera)
        assertEquals(false, asked)
    }

    @Test
    fun lastKnownLocationComesNext() = runTest {
        val camera = resolveInitialCamera(null, savedZoom = null, lastKnown = { device }, liveFix = { null })
        assertEquals(InitialCamera(device, DEFAULT_MAP_ZOOM, InitialCameraSource.LAST_KNOWN), camera)
    }

    @Test
    fun liveFixIsPolled() = runTest {
        var polls = 0
        val camera = resolveInitialCamera(
            null, savedZoom = 12.0, lastKnown = { null },
            liveFix = { polls++; if (polls == 3) device else null },
            attempts = 5, delayMs = 100,
        )
        assertEquals(InitialCamera(device, 12.0, InitialCameraSource.LIVE_FIX), camera)
        assertEquals(200L, currentTime)
    }

    @Test
    fun worldViewWhenNothingIsFound() = runTest {
        val camera = resolveInitialCamera(null, savedZoom = 12.0, lastKnown = { null }, liveFix = { null }, attempts = 5, delayMs = 100)
        assertEquals(InitialCamera(LatLon(0.0, 0.0), WORLD_MAP_ZOOM, InitialCameraSource.WORLD), camera)
        assertEquals(500L, currentTime)
    }

    @Test
    fun sourcesKnowHowTheyAreApplied() {
        assertEquals(listOf(false, true, true, false), InitialCameraSource.entries.map { it.isDeviceLocation })
        assertEquals(listOf(true, false, true, false), InitialCameraSource.entries.map { it.animates })
    }
}
