package com.rizz.wanderlist.views.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizz.wanderlist.domain.model.CollectionSummary
import com.rizz.wanderlist.domain.model.SharedPlace
import com.rizz.wanderlist.domain.repository.PlaceRepository
import com.rizz.wanderlist.domain.usecase.ObserveCollectionSummariesUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface CollectionsUiState {
    data object Loading : CollectionsUiState
    data class Content(
        val collections: List<CollectionSummary>,
        val sharedPlaces: List<SharedPlace>
    ) : CollectionsUiState
}

class CollectionsViewModel(
    observeCollectionSummaries: ObserveCollectionSummariesUseCase,
    placeRepository: PlaceRepository
) : ViewModel() {

    val uiState: StateFlow<CollectionsUiState> = combine(
        observeCollectionSummaries(),
        placeRepository.observeSharedWithMe()
    ) { collections, shared ->
        CollectionsUiState.Content(collections, shared)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CollectionsUiState.Loading
    )
}
