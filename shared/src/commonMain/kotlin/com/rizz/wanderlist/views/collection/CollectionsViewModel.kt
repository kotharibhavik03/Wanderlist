package com.rizz.wanderlist.views.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizz.wanderlist.domain.model.CollectionSummary
import com.rizz.wanderlist.domain.model.SharedPlace
import com.rizz.wanderlist.domain.model.UserProfile
import com.rizz.wanderlist.domain.repository.PlaceRepository
import com.rizz.wanderlist.domain.repository.ProfileRepository
import com.rizz.wanderlist.domain.usecase.ObserveCollectionSummariesUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

/** A place shared with me, with the name of the person who shared it. */
data class SharedPlaceItem(
    val shared: SharedPlace,
    /** The owner's name, or their email if they have no name. Null for places saved before owners were stored. */
    val sharedBy: String?
)

sealed interface CollectionsUiState {
    data object Loading : CollectionsUiState
    data class Content(
        val collections: List<CollectionSummary>,
        val sharedPlaces: List<SharedPlaceItem>
    ) : CollectionsUiState
}

class CollectionsViewModel(
    observeCollectionSummaries: ObserveCollectionSummariesUseCase,
    placeRepository: PlaceRepository,
    profileRepository: ProfileRepository
) : ViewModel() {

    // Shared: the list and the owner-name lookup below follow the same places.
    private val shared: Flow<List<SharedPlace>> = placeRepository.observeSharedWithMe()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** Owners' profiles, re-fetched only when the set of owners changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val ownerProfiles: Flow<Map<String, UserProfile>> = shared
        .map { places -> places.mapNotNull { it.place.ownerEmail }.toSet() }
        .distinctUntilChanged()
        .flatMapLatest { profileRepository.observeProfiles(it) }

    val uiState: StateFlow<CollectionsUiState> = combine(
        observeCollectionSummaries(),
        shared,
        ownerProfiles
    ) { collections, sharedPlaces, profiles ->
        CollectionsUiState.Content(
            collections = collections,
            sharedPlaces = sharedPlaces.map { item ->
                val place = item.place
                SharedPlaceItem(
                    shared = item,
                    // Current profile name first (it follows Google name changes), then what the place stored.
                    sharedBy = place.ownerEmail?.let { profiles[it]?.name } ?: place.ownerName ?: place.ownerEmail
                )
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CollectionsUiState.Loading
    )
}
