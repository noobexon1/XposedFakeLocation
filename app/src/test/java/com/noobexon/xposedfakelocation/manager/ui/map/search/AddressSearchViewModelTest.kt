package com.noobexon.xposedfakelocation.manager.ui.map.search

import com.noobexon.xposedfakelocation.manager.geo.GeocodingClient
import com.noobexon.xposedfakelocation.manager.geo.GeocodingException
import com.noobexon.xposedfakelocation.manager.geo.LatLon
import com.noobexon.xposedfakelocation.manager.geo.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddressSearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val paris = Place("Paris", "Ile-de-France, France", 48.85, 2.35)

    private class FakeClient : GeocodingClient {
        val calls = mutableListOf<Pair<String, LatLon?>>()
        var answer: () -> List<Place> = { emptyList() }
        override suspend fun search(query: String, near: LatLon?): List<Place> {
            calls += query to near
            return answer()
        }
    }

    private val client = FakeClient()
    private lateinit var vm: AddressSearchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        vm = AddressSearchViewModel(client)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun shortQueryStaysIdleAndDoesNotSearch() = runTest(dispatcher) {
        vm.onQueryChange(" ab ")
        advanceUntilIdle()
        assertEquals(SearchResults.Idle, vm.ui.value.results)
        assertTrue(client.calls.isEmpty())
    }

    @Test
    fun searchRunsOnlyAfterTheDebounce() = runTest(dispatcher) {
        client.answer = { listOf(paris) }
        vm.onQueryChange("paris")
        advanceTimeBy(399)
        runCurrent()
        assertTrue(client.calls.isEmpty())
        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("paris"), client.calls.map { it.first })
        assertEquals(SearchResults.Found(listOf(paris)), vm.ui.value.results)
    }

    @Test
    fun newerQueryCancelsThePendingOne() = runTest(dispatcher) {
        vm.onQueryChange("berl")
        advanceTimeBy(200)
        vm.onQueryChange("berlin")
        advanceUntilIdle()
        assertEquals(listOf("berlin"), client.calls.map { it.first })
    }

    @Test
    fun searchNowSkipsTheDebounce() = runTest(dispatcher) {
        vm.onQueryChange("paris")
        vm.searchNow()
        runCurrent()
        assertEquals(1, client.calls.size)
        advanceUntilIdle()
        assertEquals(1, client.calls.size)
    }

    @Test
    fun queryIsTrimmedAndBiasIsPassed() = runTest(dispatcher) {
        vm.setBias(LatLon(1.0, 2.0))
        vm.onQueryChange("  paris  ")
        advanceUntilIdle()
        assertEquals(listOf("paris" to LatLon(1.0, 2.0)), client.calls)
        assertEquals("  paris  ", vm.ui.value.query)
    }

    @Test
    fun noResultsGivesEmpty() = runTest(dispatcher) {
        vm.onQueryChange("zzzzzz")
        advanceUntilIdle()
        assertEquals(SearchResults.Empty, vm.ui.value.results)
    }

    @Test
    fun failureGivesFailedAndRetrySearchesAgain() = runTest(dispatcher) {
        client.answer = { throw GeocodingException(GeocodingException.Kind.NETWORK) }
        vm.onQueryChange("paris")
        advanceUntilIdle()
        assertEquals(SearchResults.Failed(GeocodingException.Kind.NETWORK), vm.ui.value.results)

        client.answer = { listOf(paris) }
        vm.retry()
        advanceUntilIdle()
        assertEquals(2, client.calls.size)
        assertEquals(SearchResults.Found(listOf(paris)), vm.ui.value.results)
    }

    @Test
    fun loadingIsShownWhileSearching() = runTest(dispatcher) {
        val gate = kotlinx.coroutines.CompletableDeferred<List<Place>>()
        val slow = AddressSearchViewModel(object : GeocodingClient {
            override suspend fun search(query: String, near: LatLon?): List<Place> = gate.await()
        })
        slow.onQueryChange("paris")
        advanceTimeBy(401)
        runCurrent()
        assertEquals(SearchResults.Loading, slow.ui.value.results)
        gate.complete(listOf(paris))
        advanceUntilIdle()
        assertEquals(SearchResults.Found(listOf(paris)), slow.ui.value.results)
    }

    @Test
    fun closeResetsStateAndCancelsTheSearch() = runTest(dispatcher) {
        vm.open()
        vm.onQueryChange("paris")
        vm.close()
        advanceUntilIdle()
        assertEquals(AddressSearchUiState(), vm.ui.value)
        assertTrue(client.calls.isEmpty())
    }

    @Test
    fun openShowsTheSearch() {
        vm.open()
        assertTrue(vm.ui.value.isOpen)
    }
}
