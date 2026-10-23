package com.rizz.wanderlist.views.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizz.wanderlist.domain.model.MemberRole
import com.rizz.wanderlist.domain.model.Place
import com.rizz.wanderlist.domain.model.PlaceAccess
import com.rizz.wanderlist.domain.model.PlaceCollection
import com.rizz.wanderlist.domain.model.User
import com.rizz.wanderlist.domain.model.UserProfile
import com.rizz.wanderlist.domain.model.accessFor
import com.rizz.wanderlist.domain.repository.AuthRepository
import com.rizz.wanderlist.domain.repository.CollectionRepository
import com.rizz.wanderlist.domain.repository.PlaceRepository
import com.rizz.wanderlist.domain.repository.ProfileRepository
import com.rizz.wanderlist.util.resultOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Someone on a place: its owner or one of its members. [role] uses PlaceAccess's three levels. */
data class PlacePerson(
    val name: String?,
    val email: String?,
    val role: PlaceAccess,
    val isYou: Boolean,
    /** False for someone invited by email who hasn't signed in to Wanderlist yet. */
    val hasJoined: Boolean
)

sealed interface PlaceDetailUiState {
    data object Loading : PlaceDetailUiState

    /** Deleted, or no longer shared with this user. */
    data object NotFound : PlaceDetailUiState

    data class Content(
        val place: Place,
        val access: PlaceAccess,
        /** Only the owner sees the collection; null for people the place was shared with. */
        val collection: PlaceCollection?,
        /** The owner first, then members in the order they were added. */
        val people: List<PlacePerson>,
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
    authRepository: AuthRepository,
    profileRepository: ProfileRepository
) : ViewModel() {

    private data class ActionState(val inProgress: Boolean = false, val errorMessage: String? = null)

    private val actionState = MutableStateFlow(ActionState())

    private val _events = Channel<PlaceDetailEvent>(Channel.BUFFERED)
    val events: Flow<PlaceDetailEvent> = _events.receiveAsFlow()

    // Shared: both the screen state and the profile lookup below follow the same place.
    private val place: Flow<Place?> = placeRepository.observePlace(placeId)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** Names for everyone on the place, re-fetched only when its people change. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val profiles: Flow<Map<String, UserProfile>> = place
        .map { it?.let(::emailsOf).orEmpty() }
        .distinctUntilChanged()
        .flatMapLatest { profileRepository.observeProfiles(it) }

    val uiState: StateFlow<PlaceDetailUiState> = combine(
        place,
        authRepository.currentUser,
        collectionRepository.observeCollections(),
        profiles,
        actionState
    ) { place, user, collections, profiles, action ->
        val access = if (place != null && user != null) place.accessFor(user) else null
        if (place == null || user == null || access == null) {
            PlaceDetailUiState.NotFound
        } else {
            PlaceDetailUiState.Content(
                place = place,
                access = access,
                people = peopleOf(place, user, profiles),
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

    private fun emailsOf(place: Place): Set<String> =
        setOfNotNull(place.ownerEmail) + place.members.map { it.email }

    /**
     * Names come from each person's published profile. Fallbacks: the name stored on the
     * place for its owner, and the account itself for the signed-in user.
     */
    private fun peopleOf(place: Place, user: User, profiles: Map<String, UserProfile>): List<PlacePerson> {
        val ownerIsMe = place.ownerId == user.id
        // Places saved before owner details were stored have no ownerEmail; mine can use the account.
        val ownerEmail = place.ownerEmail ?: user.email?.lowercase().takeIf { ownerIsMe }
        val owner = PlacePerson(
            name = ownerEmail?.let { profiles[it]?.name }
                ?: place.ownerName
                ?: user.displayName.takeIf { ownerIsMe },
            email = ownerEmail,
            role = PlaceAccess.OWNER,
            isYou = ownerIsMe,
            hasJoined = true
        )
        // Member emails are stored lowercase.
        val myEmail = user.email?.lowercase()
        val members = place.members.map { member ->
            val isYou = member.email == myEmail
            val profile = profiles[member.email]
            PlacePerson(
                name = profile?.name ?: user.displayName.takeIf { isYou },
                email = member.email,
                role = if (member.role == MemberRole.COLLABORATOR) PlaceAccess.COLLABORATOR else PlaceAccess.VISITED_WITH,
                isYou = isYou,
                hasJoined = profile != null || isYou
            )
        }
        return listOf(owner) + members
    }

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
