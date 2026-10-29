package com.noobexon.xposedfakelocation.manager.ui.map.search

import com.noobexon.xposedfakelocation.manager.geo.Place
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchPickActionTest {
    private val place = Place("Paris", "France", 48.85, 2.35)

    @Test
    fun locationModePlacesTheMarker() {
        assertEquals(SearchPickAction.PlaceMarker(place), searchPickAction(place, routeMode = false, spoofingActive = false, routePlaying = false))
    }

    @Test
    fun routeModeAddsAPoint() {
        assertEquals(SearchPickAction.AddRoutePoint(place), searchPickAction(place, routeMode = true, spoofingActive = false, routePlaying = false))
    }

    @Test
    fun activeSpoofingOnlyMovesTheCamera() {
        assertEquals(SearchPickAction.MoveCamera(place), searchPickAction(place, routeMode = false, spoofingActive = true, routePlaying = false))
        assertEquals(SearchPickAction.MoveCamera(place), searchPickAction(place, routeMode = true, spoofingActive = true, routePlaying = false))
    }

    @Test
    fun playingRouteOnlyMovesTheCamera() {
        assertEquals(SearchPickAction.MoveCamera(place), searchPickAction(place, routeMode = true, spoofingActive = false, routePlaying = true))
    }
}
