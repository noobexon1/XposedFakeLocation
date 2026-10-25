package com.noobexon.xposedfakelocation.manager.route

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A playing route as the app UI sees it. */
data class RoutePlaybackStatus(
    val routeId: String,
    val routeName: String,
    val pointCount: Int,
    val status: RouteStatus,
)

/** Process-wide playback status published by [RoutePlaybackService]; `null` while no route plays. */
object RoutePlaybackState {
    private val _current = MutableStateFlow<RoutePlaybackStatus?>(null)
    val current: StateFlow<RoutePlaybackStatus?> = _current.asStateFlow()

    internal fun publish(value: RoutePlaybackStatus?) {
        _current.value = value
    }
}
