package com.noobexon.xposedfakelocation.manager.ui.map.search

import com.noobexon.xposedfakelocation.manager.geo.Place

/** What picking a search result does; mirrors what a map tap would do in the same state. */
sealed interface SearchPickAction {
    val place: Place

    data class PlaceMarker(override val place: Place) : SearchPickAction
    data class AddRoutePoint(override val place: Place) : SearchPickAction
    data class MoveCamera(override val place: Place) : SearchPickAction
}

/** While spoofing or a route plays the map ignores taps, so a pick only moves the camera. */
fun searchPickAction(place: Place, routeMode: Boolean, spoofingActive: Boolean, routePlaying: Boolean): SearchPickAction =
    when {
        spoofingActive || routePlaying -> SearchPickAction.MoveCamera(place)
        routeMode -> SearchPickAction.AddRoutePoint(place)
        else -> SearchPickAction.PlaceMarker(place)
    }
