package com.rizz.wanderlist.views.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizz.wanderlist.domain.model.CollectionSummary
import com.rizz.wanderlist.domain.model.MAX_PHOTOS_PER_SAVE
import com.rizz.wanderlist.domain.model.MarkerStyle
import com.rizz.wanderlist.domain.model.MemberRole
import com.rizz.wanderlist.domain.model.NewPlace
import com.rizz.wanderlist.domain.model.NewPlacePhoto
import com.rizz.wanderlist.domain.model.PhotoChanges
import com.rizz.wanderlist.domain.model.Place
import com.rizz.wanderlist.domain.model.PlaceAccess
import com.rizz.wanderlist.domain.model.PlaceDetails
import com.rizz.wanderlist.domain.model.PlaceMember
import com.rizz.wanderlist.domain.model.PlacePhoto
import com.rizz.wanderlist.domain.model.PlaceStatus
import com.rizz.wanderlist.domain.model.SelectedLocation
import com.rizz.wanderlist.domain.model.accessFor
import com.rizz.wanderlist.domain.model.canDeletePhoto
import com.rizz.wanderlist.domain.repository.AuthRepository
import com.rizz.wanderlist.domain.repository.PhotoCompressor
import com.rizz.wanderlist.domain.repository.PlaceRepository
import com.rizz.wanderlist.domain.usecase.ObserveCollectionSummariesUseCase
import com.rizz.wanderlist.domain.usecase.SavePlaceUseCase
import com.rizz.wanderlist.domain.usecase.UpdatePlaceUseCase
import com.rizz.wanderlist.domain.usecase.memberEmailProblem
import com.rizz.wanderlist.util.pluralize
import com.rizz.wanderlist.util.resultOf
import com.rizz.wanderlist.views.photo.DraftPhoto
import com.rizz.wanderlist.views.photo.PhotoUploadProgress
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Whether the form is adding a new place or editing an existing one. */
sealed interface DraftMode {
    data object New : DraftMode

    /** Fetching the place to edit. */
    data class Loading(val placeId: String) : DraftMode

    data class Editing(val placeId: String, val access: PlaceAccess) : DraftMode

    /** Deleted, or the user isn't allowed to edit it. */
    data class Unavailable(val placeId: String) : DraftMode
}

sealed interface AddPlaceEvent {
    /** Saved, new or edited. Carries the place's collection so a new place can be shown in it. */
    data class Saved(val collectionId: String) : AddPlaceEvent
}

data class AddPlaceUiState(
    val mode: DraftMode = DraftMode.New,
    val searchQuery: String = "",
    val selectedLocation: SelectedLocation? = null,
    val customName: String = "",
    val notes: String = "",
    val status: PlaceStatus = PlaceStatus.WISHLIST,
    val visitedOnMillis: Long? = null,
    val rating: Int? = null,
    val selectedCollectionId: String? = null,
    /** New photos and, when editing, the ones already on the place. */
    val photos: List<DraftPhoto> = emptyList(),
    /** When editing: the place's photos as loaded, to work out what the save changes. */
    val originalPhotos: Map<String, PlacePhoto> = emptyMap(),
    val markerStyle: MarkerStyle = MarkerStyle.CollectionDefault,
    val members: List<PlaceMember> = emptyList(),
    val isSaving: Boolean = false,
    /** Set while a save is uploading photos. */
    val photoUpload: PhotoUploadProgress? = null,
    val errorMessage: String? = null,
) {
    val isEditing: Boolean get() = mode != DraftMode.New

    /** Only the owner moves a place to another collection or changes its people. */
    val canManageCollectionAndPeople: Boolean
        get() = mode == DraftMode.New || (mode is DraftMode.Editing && mode.access == PlaceAccess.OWNER)

    val isPreparingPhotos: Boolean get() = photos.any { !it.isReady }

    /** How many more new photos this save can take (photos already on the place don't count). */
    val photoRoom: Int get() = (MAX_PHOTOS_PER_SAVE - photos.count { !it.isSaved }).coerceAtLeast(0)

    /** What saving this edit does to the place's photos. */
    val photoChanges: PhotoChanges
        get() = PhotoChanges(
            // canSave guarantees every new photo is compressed by then.
            added = photos.filter { !it.isSaved }.mapNotNull { photo ->
                photo.localPath?.let { NewPlacePhoto(photo.id, it, photo.isPrivate) }
            },
            privacy = photos
                .filter { it.isSaved && originalPhotos[it.id]?.isPrivate?.let { was -> was != it.isPrivate } == true }
                .associate { it.id to it.isPrivate },
            removed = originalPhotos.keys - photos.map { it.id }.toSet()
        )

    val canSave: Boolean
        get() = (mode == DraftMode.New || mode is DraftMode.Editing) &&
                selectedLocation != null && selectedCollectionId != null &&
                !isSaving && !isPreparingPhotos
}

/**
 * Holds the add/edit-place draft. It is shared between the form and the Map Picker,
 * so AppNavigation owns it and clears it when the flow ends.
 */
class AddPlaceViewModel(
    observeCollectionSummaries: ObserveCollectionSummariesUseCase,
    private val savePlace: SavePlaceUseCase,
    private val updatePlace: UpdatePlaceUseCase,
    private val placeRepository: PlaceRepository,
    private val authRepository: AuthRepository,
    private val photoCompressor: PhotoCompressor
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddPlaceUiState())
    val uiState: StateFlow<AddPlaceUiState> = _uiState.asStateFlow()

    private val _events = Channel<AddPlaceEvent>(Channel.BUFFERED)
    val events: Flow<AddPlaceEvent> = _events.receiveAsFlow()

    /** Work that belongs to the current draft (compressing, saving); cancelled when it's cleared. */
    private val draftWork = mutableListOf<Job>()

    /**
     * The signed-in user's email, lowercase. Only the owner manages a place's people, so this is
     * the owner's email whenever the People section is shown.
     */
    val myEmail: StateFlow<String?> =
        authRepository.currentUser
            .map { it?.email?.lowercase() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val collections: StateFlow<List<CollectionSummary>> =
        observeCollectionSummaries()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Fills the draft from an existing place. Safe to call again for the same place. */
    fun startEditing(placeId: String) {
        val mode = _uiState.value.mode
        // Returning from the map picker calls this again; keep the unsaved changes.
        val alreadyThisPlace = (mode is DraftMode.Loading && mode.placeId == placeId) ||
                (mode is DraftMode.Editing && mode.placeId == placeId) ||
                (mode is DraftMode.Unavailable && mode.placeId == placeId)
        if (alreadyThisPlace) return

        _uiState.value = AddPlaceUiState(mode = DraftMode.Loading(placeId))
        viewModelScope.launch {
            val user = authRepository.currentUser.first()
            val place = placeRepository.observePlace(placeId).first()
            val access = if (user != null && place != null) place.accessFor(user) else null

            _uiState.update { state ->
                // The draft was cleared or reused while loading; don't overwrite it.
                if (state.mode != DraftMode.Loading(placeId)) return@update state

                if (user == null || place == null || (access != PlaceAccess.OWNER && access != PlaceAccess.COLLABORATOR)) {
                    state.copy(mode = DraftMode.Unavailable(placeId))
                } else {
                    state.copy(
                        mode = DraftMode.Editing(placeId, access),
                        selectedLocation = SelectedLocation(
                            latitude = place.latitude,
                            longitude = place.longitude,
                            actualName = place.actualName,
                            address = place.address
                        ),
                        customName = place.customName,
                        notes = place.notes,
                        status = place.status,
                        visitedOnMillis = place.visitedOnEpochMillis,
                        rating = place.rating,
                        selectedCollectionId = place.collectionId,
                        // The photos this user can see: shared ones and their own private ones.
                        photos = place.photos.map { photo ->
                            DraftPhoto(
                                id = photo.id,
                                isPrivate = photo.isPrivate,
                                url = photo.url,
                                canRemove = place.canDeletePhoto(user, photo),
                                canChangePrivacy = photo.uploaderId == user.id
                            )
                        },
                        originalPhotos = place.photos.associateBy { it.id },
                        markerStyle = place.markerStyle,
                        // Older versions let owners add themselves; drop that so the save is accepted.
                        members = place.members.filterNot { it.email == user.email?.lowercase() }
                    )
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) = _uiState.update { it.copy(searchQuery = query) }

    fun onLocationSelected(location: SelectedLocation) =
        _uiState.update { it.copy(selectedLocation = location) }

    fun onCustomNameChange(name: String) = _uiState.update { it.copy(customName = name) }

    fun onNotesChange(notes: String) = _uiState.update { it.copy(notes = notes) }

    fun onStatusChange(status: PlaceStatus) = _uiState.update {
        // A wishlist place has not been visited, so it has no date or rating.
        if (status == PlaceStatus.WISHLIST) {
            it.copy(status = status, visitedOnMillis = null, rating = null)
        } else {
            it.copy(status = status)
        }
    }

    fun onVisitedOnChange(millis: Long?) = _uiState.update { it.copy(visitedOnMillis = millis) }

    fun onRatingChange(rating: Int?) =
        _uiState.update { it.copy(rating = rating?.coerceIn(1, 5)) }

    fun onCollectionSelected(collectionId: String) =
        _uiState.update { it.copy(selectedCollectionId = collectionId) }

    /**
     * Adds what the photo picker returned. Each photo shows up at once with a spinner and is
     * compressed in the background, one at a time to keep memory low.
     */
    fun onPhotosPicked(sources: List<String>) {
        if (sources.isEmpty()) return
        val picked = sources.distinct()
        val accepted = picked.take(_uiState.value.photoRoom)
        val drafts = accepted.map { DraftPhoto(id = newPhotoId()) }
        _uiState.update { state ->
            state.copy(
                photos = state.photos + drafts,
                errorMessage = if (accepted.size < picked.size) {
                    "Added ${accepted.size} of ${picked.size} photos: you can add up to " +
                            "$MAX_PHOTOS_PER_SAVE at a time."
                } else {
                    state.errorMessage
                }
            )
        }

        launchDraftWork {
            var failed = 0
            drafts.zip(accepted).forEach { (draft, source) ->
                resultOf { photoCompressor.compress(source) }.fold(
                    onSuccess = { path -> attachCompressed(draft.id, path) },
                    onFailure = {
                        failed++
                        _uiState.update { state -> state.copy(photos = state.photos.filterNot { it.id == draft.id }) }
                    }
                )
            }
            if (failed > 0) {
                _uiState.update {
                    it.copy(errorMessage = "${pluralize(failed, "photo", "photos")} couldn't be opened.")
                }
            }
        }
    }

    /** Puts a compressed file on its draft photo, or deletes it if the photo was removed meanwhile. */
    private fun attachCompressed(photoId: String, path: String) {
        var attached = false
        _uiState.update { state ->
            attached = state.photos.any { it.id == photoId }
            if (!attached) return@update state
            state.copy(photos = state.photos.map { if (it.id == photoId) it.copy(localPath = path) else it })
        }
        if (!attached) photoCompressor.discard(path)
    }

    /** Removes a photo from the form; one already on the place is deleted when the edit is saved. */
    fun onPhotoRemoved(photoId: String) {
        val removed = _uiState.value.photos.firstOrNull { it.id == photoId } ?: return
        if (!removed.canRemove || _uiState.value.isSaving) return
        _uiState.update { state ->
            val marker = state.markerStyle
            state.copy(
                photos = state.photos.filterNot { it.id == photoId },
                markerStyle = if (marker is MarkerStyle.Photo && marker.photoId == photoId) {
                    MarkerStyle.CollectionDefault
                } else {
                    marker
                }
            )
        }
        removed.localPath?.let(photoCompressor::discard)
    }

    fun onPhotoPrivacyToggled(photoId: String) = _uiState.update { state ->
        if (state.isSaving || state.photos.none { it.id == photoId && it.canChangePrivacy }) return@update state
        val photos = state.photos.map {
            if (it.id == photoId) it.copy(isPrivate = !it.isPrivate) else it
        }
        // A private photo must never be the marker, because markers are visible to others.
        val marker = state.markerStyle
        val markerStillValid = marker !is MarkerStyle.Photo || marker.photoId != photoId ||
                photos.any { it.id == photoId && !it.isPrivate }
        state.copy(
            photos = photos,
            markerStyle = if (markerStillValid) marker else MarkerStyle.CollectionDefault
        )
    }

    fun onMarkerStyleChange(style: MarkerStyle) = _uiState.update { state ->
        val allowed = style !is MarkerStyle.Photo ||
                state.photos.any { it.id == style.photoId && it.isReady && !it.isPrivate }
        if (allowed) state.copy(markerStyle = style) else state
    }

    /** Adds a person. The dialog already refuses these cases; this keeps the draft valid regardless. */
    fun onMemberAdded(email: String, role: MemberRole) {
        val normalized = email.trim().lowercase()
        _uiState.update { state ->
            if (memberEmailProblem(normalized, myEmail.value, state.members) != null) {
                state
            } else {
                state.copy(members = state.members + PlaceMember(normalized, role))
            }
        }
    }

    fun onMemberRemoved(email: String) =
        _uiState.update { state -> state.copy(members = state.members.filterNot { it.email == email }) }

    fun onErrorShown() = _uiState.update { it.copy(errorMessage = null) }

    fun onSaveClicked() {
        val state = _uiState.value
        if (!state.canSave) return // also ignores double taps while saving
        val location = state.selectedLocation ?: return
        val collectionId = state.selectedCollectionId ?: return
        val mode = state.mode

        val details = PlaceDetails(
            collectionId = collectionId,
            actualName = location.actualName,
            customName = state.customName.trim(),
            address = location.address,
            latitude = location.latitude,
            longitude = location.longitude,
            status = state.status,
            visitedOnEpochMillis = state.visitedOnMillis,
            rating = state.rating,
            notes = state.notes.trim(),
            markerStyle = state.markerStyle,
            members = state.members
        )

        // New photos to upload; canSave guarantees every one is compressed by now.
        val photoChanges = state.photoChanges
        val photos = photoChanges.added
        val onPhotoUploaded: (Int) -> Unit = { uploaded ->
            _uiState.update { it.copy(photoUpload = PhotoUploadProgress(uploaded, photos.size)) }
        }
        val save: suspend () -> Result<Place> = when (mode) {
            DraftMode.New -> {
                { savePlace(NewPlace(details, photos), onPhotoUploaded) }
            }
            is DraftMode.Editing -> {
                { updatePlace(mode.placeId, details, photoChanges, onPhotoUploaded) }
            }
            is DraftMode.Loading, is DraftMode.Unavailable -> return
        }

        _uiState.update {
            it.copy(
                isSaving = true,
                photoUpload = if (photos.isNotEmpty()) PhotoUploadProgress(0, photos.size) else null,
                errorMessage = null
            )
        }
        launchDraftWork {
            save().fold(
                onSuccess = { place ->
                    _uiState.update { it.copy(isSaving = false, photoUpload = null) }
                    _events.send(AddPlaceEvent.Saved(place.collectionId))
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            photoUpload = null,
                            errorMessage = error.message ?: "Could not save this place."
                        )
                    }
                }
            )
        }
    }

    /**
     * Ends the draft: stops compressing and saving (a save that's cancelled saves nothing),
     * and deletes the draft's compressed photos.
     */
    fun clearDraft() {
        draftWork.forEach { it.cancel() }
        draftWork.clear()
        _uiState.value.photos.forEach { photo -> photo.localPath?.let(photoCompressor::discard) }
        _uiState.value = AddPlaceUiState()
    }

    /** Launches in viewModelScope, remembering the job so [clearDraft] can cancel it. */
    private fun launchDraftWork(block: suspend CoroutineScope.() -> Unit) {
        draftWork.removeAll { it.isCompleted }
        draftWork += viewModelScope.launch(block = block)
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun newPhotoId(): String = Uuid.random().toHexString()
}
