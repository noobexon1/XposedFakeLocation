package com.noobexon.xposedfakelocation.manager.ui.map.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noobexon.xposedfakelocation.data.SEARCH_DEBOUNCE_MS
import com.noobexon.xposedfakelocation.data.SEARCH_MIN_QUERY_LENGTH
import com.noobexon.xposedfakelocation.manager.geo.GeocodingClient
import com.noobexon.xposedfakelocation.manager.geo.GeocodingException
import com.noobexon.xposedfakelocation.manager.geo.LatLon
import com.noobexon.xposedfakelocation.manager.geo.Place
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the address search bar shows. */
@Immutable
data class AddressSearchUiState(
    val isOpen: Boolean = false,
    val query: String = "",
    val results: SearchResults = SearchResults.Idle,
)

/** The result area below the search field. */
sealed interface SearchResults {
    data object Idle : SearchResults
    data object Loading : SearchResults
    data class Found(val places: List<Place>) : SearchResults
    data object Empty : SearchResults
    data class Failed(val kind: GeocodingException.Kind) : SearchResults
}

/** Address search on the map screen. Typing searches after a pause; only the latest query runs. */
class AddressSearchViewModel(private val client: GeocodingClient) : ViewModel() {
    private val _ui = MutableStateFlow(AddressSearchUiState())
    val ui: StateFlow<AddressSearchUiState> = _ui.asStateFlow()

    private var bias: LatLon? = null
    private var searchJob: Job? = null

    fun open() = _ui.update { it.copy(isOpen = true) }

    fun close() {
        searchJob?.cancel()
        _ui.value = AddressSearchUiState()
    }

    /** The map center; results near it rank first. */
    fun setBias(center: LatLon?) {
        bias = center
    }

    fun onQueryChange(text: String) {
        _ui.update { it.copy(query = text) }
        schedule(SEARCH_DEBOUNCE_MS)
    }

    fun searchNow() = schedule(0L)

    fun retry() = searchNow()

    private fun schedule(delayMs: Long) {
        searchJob?.cancel()
        val query = _ui.value.query.trim()
        if (query.length < SEARCH_MIN_QUERY_LENGTH) {
            _ui.update { it.copy(results = SearchResults.Idle) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(delayMs)
            _ui.update { it.copy(results = SearchResults.Loading) }
            val results = try {
                val places = client.search(query, bias)
                if (places.isEmpty()) SearchResults.Empty else SearchResults.Found(places)
            } catch (e: GeocodingException) {
                SearchResults.Failed(e.kind)
            }
            // A blocking request can fail after cancellation and surface its error instead of CancellationException.
            ensureActive()
            _ui.update { it.copy(results = results) }
        }
    }
}
