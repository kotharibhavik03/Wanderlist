package com.rizz.wanderlist.views.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizz.wanderlist.domain.model.MAX_PHOTOS_PER_SAVE
import com.rizz.wanderlist.domain.model.MemberRole
import com.rizz.wanderlist.domain.model.NewPlacePhoto
import com.rizz.wanderlist.domain.model.Place
import com.rizz.wanderlist.domain.model.PlaceAccess
import com.rizz.wanderlist.domain.model.PlaceCollection
import com.rizz.wanderlist.domain.model.User
import com.rizz.wanderlist.domain.model.UserProfile
import com.rizz.wanderlist.domain.model.accessFor
import com.rizz.wanderlist.domain.model.canDeletePhoto
import com.rizz.wanderlist.domain.repository.AuthRepository
import com.rizz.wanderlist.domain.repository.CollectionRepository
import com.rizz.wanderlist.domain.repository.PhotoCompressor
import com.rizz.wanderlist.domain.repository.PlaceRepository
import com.rizz.wanderlist.domain.repository.ProfileRepository
import com.rizz.wanderlist.util.pluralize
import com.rizz.wanderlist.util.resultOf
import com.rizz.wanderlist.views.photo.DraftPhoto
import com.rizz.wanderlist.views.photo.DraftPhotos
import com.rizz.wanderlist.views.photo.PhotoUploadProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
    /** Their profile photo, if they have one. */
    val photoUrl: String?,
    val role: PlaceAccess,
    val isYou: Boolean,
    /** False for someone invited by email who hasn't signed in to Wanderlist yet. */
    val hasJoined: Boolean
)

/** Photos picked to add to the place, shown in the "Add photos" sheet until they're uploaded. */
data class PhotoDraft(
    val photos: List<DraftPhoto> = emptyList(),
    /** Set while uploading. */
    val upload: PhotoUploadProgress? = null
) {
    val isUploading: Boolean get() = upload != null
    val isPreparing: Boolean get() = photos.any { !it.isReady }

    /** How many more photos this upload can take. */
    val room: Int get() = (MAX_PHOTOS_PER_SAVE - photos.size).coerceAtLeast(0)
    val canUpload: Boolean get() = photos.isNotEmpty() && !isPreparing && !isUploading
}

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
        /** The photos this user may delete: their own, or all of them for the owner. */
        val deletablePhotoIds: Set<String> = emptySet(),
        /** Photos being deleted right now. */
        val deletingPhotoIds: Set<String> = emptySet(),
        /** The "Add photos" sheet's draft; null while the sheet is closed. */
        val photoDraft: PhotoDraft? = null,
        /** Leaving or deleting is in progress. */
        val isBusy: Boolean = false,
        /** A one-off message for the user (a failure, or photos added); cleared once shown. */
        val message: String? = null
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
    profileRepository: ProfileRepository,
    private val photoCompressor: PhotoCompressor
) : ViewModel() {

    /** Everything the user is doing on this screen, as opposed to the place's data. */
    private data class ActionState(
        val inProgress: Boolean = false,
        val message: String? = null,
        val photoDraft: PhotoDraft? = null,
        val deletingPhotoIds: Set<String> = emptySet()
    )

    private val actionState = MutableStateFlow(ActionState())

    private val draftPhotos = DraftPhotos(
        compressor = photoCompressor,
        updatePhotos = { transform -> updateDraft { it.copy(photos = transform(it.photos)) } },
        showMessage = { message -> actionState.update { it.copy(message = message) } }
    )

    /** Compressing and uploading the draft's photos; cancelled when the sheet is closed. */
    private val draftWork = mutableListOf<Job>()

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
                deletablePhotoIds = place.photos.filter { place.canDeletePhoto(user, it) }.map { it.id }.toSet(),
                deletingPhotoIds = action.deletingPhotoIds,
                photoDraft = action.photoDraft,
                isBusy = action.inProgress,
                message = action.message
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaceDetailUiState.Loading)

    fun onLeaveConfirmed() =
        removePlace(failureMessage = "Couldn't leave this place.") { placeRepository.leavePlace(placeId) }

    fun onDeleteConfirmed() =
        removePlace(failureMessage = "Couldn't delete this place.") { placeRepository.deletePlace(placeId) }

    fun onMessageShown() = actionState.update { it.copy(message = null) }

    /**
     * Adds what the photo picker returned to the "Add photos" sheet, opening it if needed. Each
     * photo is compressed in the background.
     */
    fun onPhotosPicked(sources: List<String>) {
        if (sources.isEmpty()) return
        val draft = actionState.value.photoDraft ?: PhotoDraft()
        if (draft.isUploading) return
        actionState.update { it.copy(photoDraft = draft) }
        val pending = draftPhotos.accept(sources, draft.room)
        if (pending.isNotEmpty()) launchDraftWork { draftPhotos.compress(pending) }
    }

    fun onDraftPhotoRemoved(photoId: String) {
        val draft = actionState.value.photoDraft ?: return
        if (draft.isUploading) return
        val removed = draft.photos.firstOrNull { it.id == photoId } ?: return
        updateDraft { it.copy(photos = it.photos.filterNot { photo -> photo.id == photoId }) }
        draftPhotos.discard(listOf(removed))
    }

    fun onDraftPhotoPrivacyToggled(photoId: String) = updateDraft { draft ->
        if (draft.isUploading) return@updateDraft draft
        draft.copy(photos = draft.photos.map { if (it.id == photoId) it.copy(isPrivate = !it.isPrivate) else it })
    }

    /** Uploads the draft. On success the sheet closes; on failure it stays, so the user can retry. */
    fun onAddPhotosConfirmed() {
        val draft = actionState.value.photoDraft ?: return
        if (!draft.canUpload) return
        // canUpload guarantees every photo is compressed by now.
        val photos = draft.photos.mapNotNull { photo -> photo.localPath?.let { NewPlacePhoto(photo.id, it, photo.isPrivate) } }
        updateDraft { it.copy(upload = PhotoUploadProgress(0, photos.size)) }

        launchDraftWork {
            resultOf {
                placeRepository.addPhotos(placeId, photos) { uploaded ->
                    updateDraft { it.copy(upload = PhotoUploadProgress(uploaded, photos.size)) }
                }
            }.fold(
                onSuccess = {
                    // Uploaded: the compressed copies aren't needed any more.
                    photos.forEach { photoCompressor.discard(it.localPath) }
                    actionState.update {
                        it.copy(photoDraft = null, message = "Added ${pluralize(photos.size, "photo")}.")
                    }
                },
                onFailure = { error ->
                    updateDraft { it.copy(upload = null) }
                    actionState.update { it.copy(message = error.message ?: "Couldn't add the photos.") }
                }
            )
        }
    }

    /** Closes the "Add photos" sheet without uploading. Ignored while uploading. */
    fun onAddPhotosDismissed() {
        val draft = actionState.value.photoDraft ?: return
        if (draft.isUploading) return
        discardDraft()
    }

    fun onDeletePhoto(photoId: String) {
        if (photoId in actionState.value.deletingPhotoIds) return
        actionState.update { it.copy(deletingPhotoIds = it.deletingPhotoIds + photoId) }
        viewModelScope.launch {
            val result = resultOf { placeRepository.deletePhoto(placeId, photoId) }
            actionState.update { state ->
                state.copy(
                    deletingPhotoIds = state.deletingPhotoIds - photoId,
                    message = result.exceptionOrNull()?.let { it.message ?: "Couldn't delete the photo." } ?: state.message
                )
            }
        }
    }

    override fun onCleared() {
        // viewModelScope is cancelled already; only the compressed files are left to clean up.
        discardDraft()
    }

    private fun updateDraft(transform: (PhotoDraft) -> PhotoDraft) = actionState.update { state ->
        state.photoDraft?.let { state.copy(photoDraft = transform(it)) } ?: state
    }

    /** Stops compressing or uploading, deletes the compressed files and closes the sheet. */
    private fun discardDraft() {
        draftWork.forEach { it.cancel() }
        draftWork.clear()
        actionState.value.photoDraft?.let { draftPhotos.discard(it.photos) }
        actionState.update { it.copy(photoDraft = null) }
    }

    private fun launchDraftWork(block: suspend CoroutineScope.() -> Unit) {
        draftWork.removeAll { it.isCompleted }
        draftWork += viewModelScope.launch(block = block)
    }

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
            photoUrl = ownerEmail?.let { profiles[it]?.photoUrl } ?: user.photoUrl.takeIf { ownerIsMe },
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
                photoUrl = profile?.photoUrl ?: user.photoUrl.takeIf { isYou },
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
        actionState.update { it.copy(inProgress = true) }
        viewModelScope.launch {
            resultOf { action() }.fold(
                onSuccess = {
                    actionState.update { it.copy(inProgress = false) }
                    _events.send(PlaceDetailEvent.PlaceRemoved)
                },
                onFailure = { error ->
                    actionState.update { it.copy(inProgress = false, message = error.message ?: failureMessage) }
                }
            )
        }
    }
}
