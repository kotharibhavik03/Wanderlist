package com.rizz.wanderlist.views.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizz.wanderlist.domain.model.Place
import com.rizz.wanderlist.domain.model.PlaceAccess
import com.rizz.wanderlist.domain.model.PlaceCollection
import com.rizz.wanderlist.domain.model.accessFor
import com.rizz.wanderlist.domain.repository.AuthRepository
import com.rizz.wanderlist.domain.repository.CollectionRepository
import com.rizz.wanderlist.domain.repository.PlaceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface PlaceDetailUiState {
    data object Loading : PlaceDetailUiState

    /** Deleted, or no longer shared with this user. */
    data object NotFound : PlaceDetailUiState

    data class Content(
        val place: Place,
        val access: PlaceAccess,
        /** Only the owner sees the collection; null for people the place was shared with. */
        val collection: PlaceCollection?
    ) : PlaceDetailUiState
}

class PlaceDetailViewModel(
    placeId: String,
    placeRepository: PlaceRepository,
    collectionRepository: CollectionRepository,
    authRepository: AuthRepository
) : ViewModel() {

    val uiState: StateFlow<PlaceDetailUiState> = combine(
        placeRepository.observePlace(placeId),
        authRepository.currentUser,
        collectionRepository.observeCollections()
    ) { place, user, collections ->
        val access = if (place != null && user != null) place.accessFor(user) else null
        if (place == null || access == null) {
            PlaceDetailUiState.NotFound
        } else {
            PlaceDetailUiState.Content(
                place = place,
                access = access,
                collection = if (access == PlaceAccess.OWNER) {
                    collections.firstOrNull { it.id == place.collectionId }
                } else {
                    null
                }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaceDetailUiState.Loading)
}
