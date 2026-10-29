package com.noobexon.xposedfakelocation.manager.route

import com.noobexon.xposedfakelocation.data.EncodedPolyline
import com.noobexon.xposedfakelocation.data.model.RoadPath
import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.data.model.RoutePoint
import com.noobexon.xposedfakelocation.manager.geo.RoadGeometry
import com.noobexon.xposedfakelocation.manager.geo.RoadRequest
import com.noobexon.xposedfakelocation.manager.geo.RoutingClient
import com.noobexon.xposedfakelocation.manager.geo.RoutingException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoadPathFetcherTest {
    private val a = RoutePoint("a", 0.0, 0.0)
    private val b = RoutePoint("b", 0.001, 0.0)
    private val routes = MutableStateFlow(Route(id = "r", points = listOf(a, b), followRoads = true))
    private val paused = MutableStateFlow(false)

    private class FakeClient(private val scope: TestScope) : RoutingClient {
        val calls = mutableListOf<Pair<String, Long>>()
        var answer: suspend (RoadRequest) -> RoadGeometry = { request ->
            RoadGeometry(EncodedPolyline.encode(listOf(request.fromLatitude to 0.0001, request.toLatitude to 0.0001)), 120.0)
        }
        override suspend fun route(request: RoadRequest): RoadGeometry {
            calls += request.key to scope.testScheduler.currentTime
            return answer(request)
        }
    }

    private fun TestScope.fetcher(client: FakeClient, results: MutableList<String> = mutableListOf()) = RoadPathFetcher(
        scope = backgroundScope,
        client = client,
        routes = routes,
        currentRoute = { routes.value },
        paused = paused,
        now = { testScheduler.currentTime },
        onResult = { request: RoadRequest, path: RoadPath ->
            results += request.key
            routes.value = routes.value.copy(roadPaths = routes.value.roadPaths + (request.key to path))
        },
    ).also { it.start() }

    // advanceUntilIdle() ignores backgroundScope work, so advance a fixed window instead.
    private fun TestScope.settle() {
        advanceTimeBy(30_000)
        runCurrent()
    }

    @Test
    fun waitsForTheDebounceThenFetchesBothDirectionsSpacedOut() = runTest {
        val client = FakeClient(this)
        val results = mutableListOf<String>()
        fetcher(client, results)
        advanceTimeBy(599)
        runCurrent()
        assertTrue(client.calls.isEmpty())
        settle()
        assertEquals(listOf("a>b", "b>a"), client.calls.map { it.first })
        assertEquals(600L, client.calls[0].second)
        assertTrue(client.calls[1].second - client.calls[0].second >= 1_000L)
        assertEquals(listOf("a>b", "b>a"), results)
        assertTrue(routes.value.missingRoadPaths().isEmpty())
    }

    @Test
    fun pausedFetchesNothingUntilResumed() = runTest {
        paused.value = true
        val client = FakeClient(this)
        fetcher(client)
        settle()
        assertTrue(client.calls.isEmpty())
        paused.value = false
        settle()
        assertEquals(2, client.calls.size)
    }

    @Test
    fun failureSticksUntilRetryOrInputChange() = runTest {
        val client = FakeClient(this)
        client.answer = { throw RoutingException(RoutingException.Kind.NETWORK) }
        val fetcher = fetcher(client)
        settle()
        assertEquals(2, client.calls.size)
        assertEquals(setOf("a>b", "b>a"), fetcher.failures.value.keys)
        settle()
        assertEquals(2, client.calls.size)

        fetcher.retry("a>b")
        settle()
        assertEquals(3, client.calls.size)
        assertEquals("a>b", client.calls.last().first)

        routes.value = routes.value.copy(points = listOf(a, b.copy(latitude = 0.002)))
        settle()
        assertEquals(5, client.calls.size)
    }

    @Test
    fun resultForAnOutdatedRequestIsDropped() = runTest {
        val gate = CompletableDeferred<Unit>()
        val client = FakeClient(this)
        val first = client.answer
        client.answer = { request ->
            if (request.toLatitude == 0.001) withContext(NonCancellable) { gate.await() }
            first(request)
        }
        val results = mutableListOf<String>()
        fetcher(client, results)
        advanceTimeBy(601)
        runCurrent()
        assertEquals(1, client.calls.size)
        routes.value = routes.value.copy(points = listOf(a, b.copy(latitude = 0.002)))
        gate.complete(Unit)
        settle()
        assertEquals(listOf("a>b", "b>a"), results)
        assertEquals(0.002, routes.value.roadPaths.getValue("a>b").toLatitude, 0.0)
        assertTrue(routes.value.missingRoadPaths().isEmpty())
    }

    @Test
    fun slowResponsesAreNotRequestedTwice() = runTest {
        val client = FakeClient(this)
        val first = client.answer
        client.answer = { request ->
            delay(2_000)
            first(request)
        }
        val results = mutableListOf<String>()
        fetcher(client, results)
        settle()
        assertEquals(listOf("a>b", "b>a"), client.calls.map { it.first })
        assertEquals(listOf("a>b", "b>a"), results)
    }

    @Test
    fun retryAllForgetsEveryFailure() = runTest {
        val client = FakeClient(this)
        client.answer = { throw RoutingException(RoutingException.Kind.NETWORK) }
        val fetcher = fetcher(client)
        settle()
        assertEquals(2, client.calls.size)
        fetcher.retryAll()
        settle()
        assertEquals(4, client.calls.size)
        assertEquals(setOf("a>b", "b>a"), fetcher.failures.value.keys)
    }
}
