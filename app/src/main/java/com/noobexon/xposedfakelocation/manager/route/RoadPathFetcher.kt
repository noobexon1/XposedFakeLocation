package com.noobexon.xposedfakelocation.manager.route

import com.noobexon.xposedfakelocation.data.ROAD_FETCH_DEBOUNCE_MS
import com.noobexon.xposedfakelocation.data.ROAD_FETCH_SPACING_MS
import com.noobexon.xposedfakelocation.data.model.RoadPath
import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.manager.geo.RoadRequest
import com.noobexon.xposedfakelocation.manager.geo.RoutingClient
import com.noobexon.xposedfakelocation.manager.geo.RoutingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Fetches the road paths a route is missing, one at a time, [debounceMs] after the route last
 * changed and at least [spacingMs] apart. Paused while [paused] is `true`. A failure sticks (no
 * refetch) until [retry] or until the request's inputs change.
 */
class RoadPathFetcher(
    private val scope: CoroutineScope,
    private val client: RoutingClient,
    private val routes: Flow<Route>,
    private val currentRoute: () -> Route,
    private val paused: Flow<Boolean>,
    private val now: () -> Long,
    private val onResult: (RoadRequest, RoadPath) -> Unit,
    private val debounceMs: Long = ROAD_FETCH_DEBOUNCE_MS,
    private val spacingMs: Long = ROAD_FETCH_SPACING_MS,
) {
    private val _failures = MutableStateFlow<Map<String, RoadFailure>>(emptyMap())
    /** Failed fetches by directed key. */
    val failures: StateFlow<Map<String, RoadFailure>> = _failures.asStateFlow()

    private var lastStartMs: Long? = null

    /** Starts watching the route; cancel the returned job (or [scope]) to stop. */
    fun start(): Job = scope.launch {
        combine(routes, paused, _failures) { _, isPaused, _ -> isPaused }
            .collectLatest { isPaused -> if (!isPaused) fetchMissing() }
    }

    /** Forgets the failure for [key] so it is fetched again. */
    fun retry(key: String) = _failures.update { it - key }

    private suspend fun fetchMissing() {
        delay(debounceMs)
        while (true) {
            val failed = _failures.value
            val next = currentRoute().missingRoadPaths().firstOrNull { failed[it.key]?.request != it } ?: return
            lastStartMs?.let { last ->
                val wait = last + spacingMs - now()
                if (wait > 0) delay(wait)
            }
            lastStartMs = now()
            try {
                val geometry = client.route(next)
                currentCoroutineContext().ensureActive()
                if (next in currentRoute().missingRoadPaths()) onResult(next, next.toRoadPath(geometry))
            } catch (e: RoutingException) {
                // A blocking request can fail after cancellation; its error must not be recorded then.
                currentCoroutineContext().ensureActive()
                _failures.update { it + (next.key to RoadFailure(next, e.kind)) }
            }
        }
    }
}
