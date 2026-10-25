package com.noobexon.xposedfakelocation.manager.ui.favorites

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noobexon.xposedfakelocation.data.model.FavoriteLocation
import com.noobexon.xposedfakelocation.data.model.Route
import com.noobexon.xposedfakelocation.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Favorites screen.
 *
 * Exposes the persisted list of [FavoriteLocation]s as a lifecycle-aware [StateFlow] and provides
 * mutation operations (remove, update) that delegate to [PreferencesRepository].
 *
 * The [favorites] flow uses [SharingStarted.WhileSubscribed] with a 5-second timeout so the
 * upstream [SharedPreferences] listener stays active briefly after the screen leaves the
 * composition, avoiding unnecessary restarts on quick navigations (e.g. back-then-forward).
 */
class FavoritesViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesRepository = PreferencesRepository(application)

    /**
     * The current list of saved favorite locations, kept in sync with [PreferencesRepository].
     * Emits a new list whenever any entry is added, removed, or updated.
     */
    val favorites: StateFlow<List<FavoriteLocation>> =
        preferencesRepository.getFavoritesFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Permanently removes [favorite] from the persisted list.
     *
     * @param favorite The entry to delete.
     */
    fun removeFavorite(favorite: FavoriteLocation) {
        viewModelScope.launch {
            preferencesRepository.removeFavorite(favorite)
        }
    }

    /**
     * Replaces [old] with [new] in the persisted list, preserving the entry's original position.
     * No-op if [old] is not found (e.g. it was already deleted concurrently).
     *
     * @param old The entry to replace; matched by value equality.
     * @param new The updated entry to write in its place.
     */
    fun updateFavorite(old: FavoriteLocation, new: FavoriteLocation) {
        viewModelScope.launch {
            preferencesRepository.updateFavorite(old, new)
        }
    }

    /** Saved routes, kept in sync with [PreferencesRepository]. */
    val routes: StateFlow<List<Route>> =
        preferencesRepository.getRoutesFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteRoute(route: Route) = preferencesRepository.deleteRoute(route.id)

    fun renameRoute(route: Route, name: String) = preferencesRepository.saveRoute(route.copy(name = name.trim()))
}
