package com.rizz.wanderlist.views.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizz.wanderlist.domain.model.CollectionSummary
import com.rizz.wanderlist.domain.usecase.ObserveCollectionSummariesUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface CollectionsUiState {
    data object Loading : CollectionsUiState
    data class Content(val collections: List<CollectionSummary>) : CollectionsUiState
}

class CollectionsViewModel(observeCollectionSummaries: ObserveCollectionSummariesUseCase) : ViewModel() {

    val uiState: StateFlow<CollectionsUiState> = observeCollectionSummaries()
        .map<List<CollectionSummary>, CollectionsUiState> { CollectionsUiState.Content(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CollectionsUiState.Loading
        )
}
