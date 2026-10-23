package com.rizz.wanderlist.views.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizz.wanderlist.domain.model.CollectionSummary
import com.rizz.wanderlist.domain.model.Place
import com.rizz.wanderlist.domain.model.PlaceCollection
import com.rizz.wanderlist.domain.model.SelectedLocation
import com.rizz.wanderlist.domain.repository.AuthRepository
import com.rizz.wanderlist.domain.repository.PlaceRepository
import com.rizz.wanderlist.domain.usecase.LocationSearchState
import com.rizz.wanderlist.domain.usecase.ObserveCollectionSummariesUseCase
import com.rizz.wanderlist.domain.usecase.SearchLocationsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** A place as Home shows it. [collection] is null for places shared with the user. */
data class HomePlace(val place: Place, val collection: PlaceCollection?) {
    val isShared: Boolean get() = collection == null
}

data class HomeUiState(
    val isLoading: Boolean = true,
    val collections: List<CollectionSummary> = emptyList(),
    val sharedCount: Int = 0,
    val selectedCollectionIds: Set<String> = emptySet(),
    val sharedSelected: Boolean = false,
    val query: String = "",
    /** Places matching the filter chips: what the map will show as markers. */
    val places: List<HomePlace> = emptyList(),
    /** Places matching the filter chips and the search text. Empty while not searching. */
    val searchResults: List<HomePlace> = emptyList(),
    /** Locations anywhere matching the search text, from the phone's search. */
    val otherLocations: List<SelectedLocation> = emptyList(),
    val isSearchingLocations: Boolean = false,
    val locationSearchFailed: Boolean = false,
    /** A searched location shown on the map with a temporary pin, not (yet) one of the user's places. */
    val pickedLocation: SelectedLocation? = null
) {
    /** No chip selected means "All". */
    val showingAll: Boolean get() = selectedCollectionIds.isEmpty() && !sharedSelected
    val isSearching: Boolean get() = query.isNotBlank()
}

class HomeViewModel(
    placeRepository: PlaceRepository,
    observeCollectionSummaries: ObserveCollectionSummariesUseCase,
    authRepository: AuthRepository,
    searchLocations: SearchLocationsUseCase
) : ViewModel() {

    private data class Filters(
        val collectionIds: Set<String> = emptySet(),
        val shared: Boolean = false,
        val query: String = "",
        val pickedLocation: SelectedLocation? = null
    )

    private val filters = MutableStateFlow(Filters())

    /** Searches everywhere as the user types (see SearchLocationsUseCase for pacing). */
    private val locationSearch: Flow<LocationSearchState> = searchLocations(filters.map { it.query })

    val uiState: StateFlow<HomeUiState> = combine(
        placeRepository.observeVisiblePlaces(),
        observeCollectionSummaries(),
        authRepository.currentUser,
        filters,
        locationSearch
    ) { places, summaries, user, f, locations ->
        val collectionsById = summaries.associate { it.collection.id to it.collection }
        // A place in one of my collections is mine; anything else I can see was shared with me.
        val homePlaces = places.map { place ->
            HomePlace(place, collectionsById[place.collectionId].takeIf { place.ownerId == user?.id })
        }
        val sharedCount = homePlaces.count { it.isShared }
        // Ignore selections that no longer have a chip: deleted collections, or nothing shared any more.
        val selectedIds = f.collectionIds.intersect(collectionsById.keys)
        val sharedSelected = f.shared && sharedCount > 0
        val showAll = selectedIds.isEmpty() && !sharedSelected
        val filtered = homePlaces.filter { item ->
            showAll ||
                    (item.isShared && sharedSelected) ||
                    (!item.isShared && item.place.collectionId in selectedIds)
        }

        HomeUiState(
            isLoading = false,
            collections = summaries,
            sharedCount = sharedCount,
            selectedCollectionIds = selectedIds,
            sharedSelected = sharedSelected,
            query = f.query,
            places = filtered,
            searchResults = if (f.query.isBlank()) emptyList() else filtered.filter { it.matches(f.query) },
            otherLocations = locations.results,
            isSearchingLocations = locations.isLoading,
            locationSearchFailed = locations.failed,
            pickedLocation = f.pickedLocation
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onQueryChange(query: String) = filters.update { it.copy(query = query) }

    fun onAllSelected() = filters.update { it.copy(collectionIds = emptySet(), shared = false) }

    fun onCollectionToggled(collectionId: String) = filters.update {
        val ids = it.collectionIds
        it.copy(collectionIds = if (collectionId in ids) ids - collectionId else ids + collectionId)
    }

    fun onSharedToggled() = filters.update { it.copy(shared = !it.shared) }

    /** Shows a searched location on the map; picking one ends the search. */
    fun onLocationPicked(location: SelectedLocation) =
        filters.update { it.copy(query = "", pickedLocation = location) }

    fun onPickedLocationCleared() = filters.update { it.copy(pickedLocation = null) }

}

/** Case-insensitive match on the place's names, address, or collection name. */
private fun HomePlace.matches(query: String): Boolean {
    val q = query.trim()
    return listOfNotNull(place.customName, place.actualName, place.address, collection?.name)
        .any { it.contains(q, ignoreCase = true) }
}
