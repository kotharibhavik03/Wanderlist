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
import com.rizz.wanderlist.util.resultOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PlaceDetailUiState {
    data object Loading : PlaceDetailUiState

    /** Deleted, or no longer shared with this user. */
    data object NotFound : PlaceDetailUiState

    data class Content(
        val place: Place,
        val access: PlaceAccess,
        /** Only the owner sees the collection; null for people the place was shared with. */
        val collection: PlaceCollection?,
        /** Leaving or deleting is in progress. */
        val isBusy: Boolean = false,
        val errorMessage: String? = null
    ) : PlaceDetailUiState {
        val canEdit: Boolean get() = access == PlaceAccess.OWNER || access == PlaceAccess.COLLABORATOR
        val canLeave: Boolean get() = access != PlaceAccess.OWNER
        val canDelete: Boolean get() = access == PlaceAccess.OWNER
    }
}

sealed interface PlaceDetailEvent {
    /** The user left or deleted the place, so this screen should close. */
    data object PlaceRemoved : PlaceDetailEvent
}

class PlaceDetailViewModel(
    private val placeId: String,
    private val placeRepository: PlaceRepository,
    collectionRepository: CollectionRepository,
    authRepository: AuthRepository
) : ViewModel() {

    private data class ActionState(val inProgress: Boolean = false, val errorMessage: String? = null)

    private val actionState = MutableStateFlow(ActionState())

    private val _events = Channel<PlaceDetailEvent>(Channel.BUFFERED)
    val events: Flow<PlaceDetailEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<PlaceDetailUiState> = combine(
        placeRepository.observePlace(placeId),
        authRepository.currentUser,
        collectionRepository.observeCollections(),
        actionState
    ) { place, user, collections, action ->
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
                },
                isBusy = action.inProgress,
                errorMessage = action.errorMessage
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaceDetailUiState.Loading)

    fun onLeaveConfirmed() =
        removePlace(failureMessage = "Couldn't leave this place.") { placeRepository.leavePlace(placeId) }

    fun onDeleteConfirmed() =
        removePlace(failureMessage = "Couldn't delete this place.") { placeRepository.deletePlace(placeId) }

    fun onErrorShown() = actionState.update { it.copy(errorMessage = null) }

    /** Runs a leave or delete, then closes the screen on success or shows why it failed. */
    private fun removePlace(failureMessage: String, action: suspend () -> Unit) {
        if (actionState.value.inProgress) return
        actionState.value = ActionState(inProgress = true)
        viewModelScope.launch {
            resultOf { action() }.fold(
                onSuccess = {
                    actionState.value = ActionState()
                    _events.send(PlaceDetailEvent.PlaceRemoved)
                },
                onFailure = { error ->
                    actionState.value = ActionState(errorMessage = error.message ?: failureMessage)
                }
            )
        }
    }
}
