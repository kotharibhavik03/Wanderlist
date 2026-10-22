package com.rizz.wanderlist.views.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizz.wanderlist.domain.model.PlaceCollection
import com.rizz.wanderlist.domain.repository.CollectionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface CollectionsUiState {
    data object Loading : CollectionsUiState
    data class Content(val collections: List<PlaceCollection>) : CollectionsUiState
}

class CollectionsViewModel(repository: CollectionRepository) : ViewModel() {

    val uiState: StateFlow<CollectionsUiState> = repository.observeCollections()
        .map<List<PlaceCollection>, CollectionsUiState> { CollectionsUiState.Content(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CollectionsUiState.Loading
        )
}