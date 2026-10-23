package com.rizz.wanderlist.views.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizz.wanderlist.domain.model.Place
import com.rizz.wanderlist.domain.model.PlaceCollection
import com.rizz.wanderlist.domain.model.PlacePhoto
import com.rizz.wanderlist.domain.repository.CollectionRepository
import com.rizz.wanderlist.domain.repository.PlaceRepository
import com.rizz.wanderlist.domain.usecase.DeleteCollectionUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CollectionPhoto(val placeId: String, val placeName: String, val photo: PlacePhoto)

data class CollectionDetailUiState(
    val collection: PlaceCollection? = null,
    val places: List<Place> = emptyList(),
    val isLoading: Boolean = true,
    val isDeleting: Boolean = false,
    val errorMessage: String? = null
) {
    /** Every photo across all places: the "All photos" view. */
    val photos: List<CollectionPhoto>
        get() = places.flatMap { place ->
            place.photos.map { CollectionPhoto(place.id, place.displayName, it) }
        }
}

sealed interface CollectionDetailEvent {
    data object Deleted : CollectionDetailEvent
}

class CollectionDetailViewModel(
    private val collectionId: String,
    collectionRepository: CollectionRepository,
    placeRepository: PlaceRepository,
    private val deleteCollection: DeleteCollectionUseCase
) : ViewModel() {

    private data class DeleteState(val inProgress: Boolean = false, val errorMessage: String? = null)

    private val deleteState = MutableStateFlow(DeleteState())

    private val _events = Channel<CollectionDetailEvent>(Channel.BUFFERED)
    val events: Flow<CollectionDetailEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<CollectionDetailUiState> = combine(
        collectionRepository.observeCollections()
            .map { list -> list.firstOrNull { it.id == collectionId } },
        placeRepository.observePlaces(collectionId),
        deleteState
    ) { collection, places, delete ->
        CollectionDetailUiState(
            collection = collection,
            places = places,
            isLoading = false,
            isDeleting = delete.inProgress,
            errorMessage = delete.errorMessage
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionDetailUiState())

    fun onDeleteConfirmed() {
        if (deleteState.value.inProgress) return
        deleteState.value = DeleteState(inProgress = true)
        viewModelScope.launch {
            deleteCollection(collectionId).fold(
                onSuccess = {
                    deleteState.value = DeleteState()
                    _events.send(CollectionDetailEvent.Deleted)
                },
                onFailure = { error ->
                    deleteState.value = DeleteState(errorMessage = error.message ?: "Couldn't delete this collection.")
                }
            )
        }
    }

    fun onErrorShown() = deleteState.update { it.copy(errorMessage = null) }
}
