package com.rizz.wanderlist.views.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizz.wanderlist.domain.model.Place
import com.rizz.wanderlist.domain.model.PlaceCollection
import com.rizz.wanderlist.domain.model.PlacePhoto
import com.rizz.wanderlist.domain.repository.CollectionRepository
import com.rizz.wanderlist.domain.repository.PlaceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class CollectionPhoto(val placeId: String, val placeName: String, val photo: PlacePhoto)

data class CollectionDetailUiState(
    val collection: PlaceCollection? = null,
    val places: List<Place> = emptyList(),
    val isLoading: Boolean = true
) {
    /** Every photo across all places: the "All photos" view. */
    val photos: List<CollectionPhoto>
        get() = places.flatMap { place ->
            place.photos.map { CollectionPhoto(place.id, place.displayName, it) }
        }
}

class CollectionDetailViewModel(
    collectionId: String,
    collectionRepository: CollectionRepository,
    placeRepository: PlaceRepository
) : ViewModel() {

    val uiState: StateFlow<CollectionDetailUiState> = combine(
        collectionRepository.observeCollections()
            .map { list -> list.firstOrNull { it.id == collectionId } },
        placeRepository.observePlaces(collectionId)
    ) { collection, places ->
        CollectionDetailUiState(collection = collection, places = places, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionDetailUiState())
}