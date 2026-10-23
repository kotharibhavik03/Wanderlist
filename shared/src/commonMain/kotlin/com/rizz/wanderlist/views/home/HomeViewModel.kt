package com.rizz.wanderlist.views.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizz.wanderlist.domain.model.CollectionSummary
import com.rizz.wanderlist.domain.model.GeoPoint
import com.rizz.wanderlist.domain.model.Place
import com.rizz.wanderlist.domain.model.PlaceCollection
import com.rizz.wanderlist.domain.model.SelectedLocation
import com.rizz.wanderlist.domain.repository.AuthRepository
import com.rizz.wanderlist.domain.repository.LocationService
import com.rizz.wanderlist.domain.repository.PlaceRepository
import com.rizz.wanderlist.domain.usecase.LocationSearchState
import com.rizz.wanderlist.domain.usecase.ObserveCollectionSummariesUseCase
import com.rizz.wanderlist.domain.usecase.SearchLocationsUseCase
import com.rizz.wanderlist.util.resultOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
    val pickedLocation: SelectedLocation? = null,
    /** Shows the blue "you are here" dot once location access is allowed. */
    val hasLocationPermission: Boolean = false,
    /** Finding the device's position after "my location" was tapped. */
    val isLocating: Boolean = false,
    /** Where the map should fly after "my location"; cleared once it has. */
    val myLocation: GeoPoint? = null,
    /** A one-off message for the user (e.g. location is off); cleared once shown. */
    val message: String? = null
) {
    /** No chip selected means "All". */
    val showingAll: Boolean get() = selectedCollectionIds.isEmpty() && !sharedSelected
    val isSearching: Boolean get() = query.isNotBlank()
}

class HomeViewModel(
    placeRepository: PlaceRepository,
    observeCollectionSummaries: ObserveCollectionSummariesUseCase,
    authRepository: AuthRepository,
    searchLocations: SearchLocationsUseCase,
    private val locationService: LocationService
) : ViewModel() {

    private data class LocationState(
        val hasPermission: Boolean = false,
        val isLocating: Boolean = false,
        val flyTo: GeoPoint? = null,
        val message: String? = null
    )

    private val location = MutableStateFlow(LocationState(hasPermission = locationService.hasPermission()))

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
        // combine() takes at most five typed flows: search results and location state travel together.
        combine(locationSearch, location, ::Pair)
    ) { places, summaries, user, f, (locations, here) ->
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
            pickedLocation = f.pickedLocation,
            hasLocationPermission = here.hasPermission,
            isLocating = here.isLocating,
            myLocation = here.flyTo,
            message = here.message
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

    fun hasLocationPermission(): Boolean = locationService.hasPermission()

    /** The permission dialog was answered: show or hide the "you are here" dot. */
    fun onPermissionAnswered(granted: Boolean) = location.update { it.copy(hasPermission = granted) }

    fun onPermissionRefused() = location.update {
        it.copy(message = "Location access is off. Allow it in Settings to see where you are.")
    }

    fun goToMyLocation() {
        if (location.value.isLocating) return
        location.update { it.copy(isLocating = true, hasPermission = true) }
        viewModelScope.launch {
            val here = withTimeoutOrNull(LOCATE_TIMEOUT_MS) {
                resultOf { locationService.currentLocation() }.getOrNull()
            }
            location.update {
                if (here == null) {
                    it.copy(isLocating = false, message = "Couldn't find your location. Check that location is turned on.")
                } else {
                    it.copy(isLocating = false, flyTo = here)
                }
            }
        }
    }

    fun onMyLocationShown() = location.update { it.copy(flyTo = null) }

    fun onMessageShown() = location.update { it.copy(message = null) }

    private companion object {
        /** A fix normally takes a few seconds; give up rather than spin forever. */
        const val LOCATE_TIMEOUT_MS = 15_000L
    }

}

/** Case-insensitive match on the place's names, address, or collection name. */
private fun HomePlace.matches(query: String): Boolean {
    val q = query.trim()
    return listOfNotNull(place.customName, place.actualName, place.address, collection?.name)
        .any { it.contains(q, ignoreCase = true) }
}
