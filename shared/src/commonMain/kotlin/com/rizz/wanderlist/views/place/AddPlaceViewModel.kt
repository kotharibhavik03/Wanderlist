package com.rizz.wanderlist.views.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizz.wanderlist.domain.model.CollectionSummary
import com.rizz.wanderlist.domain.model.MarkerStyle
import com.rizz.wanderlist.domain.model.MemberRole
import com.rizz.wanderlist.domain.model.NewPlace
import com.rizz.wanderlist.domain.model.NewPlacePhoto
import com.rizz.wanderlist.domain.model.Place
import com.rizz.wanderlist.domain.model.PlaceAccess
import com.rizz.wanderlist.domain.model.PlaceDetails
import com.rizz.wanderlist.domain.model.PlaceMember
import com.rizz.wanderlist.domain.model.PlaceStatus
import com.rizz.wanderlist.domain.model.SelectedLocation
import com.rizz.wanderlist.domain.model.accessFor
import com.rizz.wanderlist.domain.repository.AuthRepository
import com.rizz.wanderlist.domain.repository.PlaceRepository
import com.rizz.wanderlist.domain.usecase.ObserveCollectionSummariesUseCase
import com.rizz.wanderlist.domain.usecase.SavePlaceUseCase
import com.rizz.wanderlist.domain.usecase.UpdatePlaceUseCase
import com.rizz.wanderlist.util.isValidEmail
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A photo picked but not uploaded yet. The uri doubles as its id inside the draft. */
data class DraftPhoto(val uri: String, val isPrivate: Boolean = false)

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
    val photos: List<DraftPhoto> = emptyList(),
    val markerStyle: MarkerStyle = MarkerStyle.CollectionDefault,
    val members: List<PlaceMember> = emptyList(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
) {
    val isEditing: Boolean get() = mode != DraftMode.New

    /** Only the owner moves a place to another collection or changes its people. */
    val canManageCollectionAndPeople: Boolean
        get() = mode == DraftMode.New || (mode is DraftMode.Editing && mode.access == PlaceAccess.OWNER)

    val canSave: Boolean
        get() = (mode == DraftMode.New || mode is DraftMode.Editing) &&
                selectedLocation != null && selectedCollectionId != null && !isSaving
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
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddPlaceUiState())
    val uiState: StateFlow<AddPlaceUiState> = _uiState.asStateFlow()

    private val _events = Channel<AddPlaceEvent>(Channel.BUFFERED)
    val events: Flow<AddPlaceEvent> = _events.receiveAsFlow()

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

                if (place == null || (access != PlaceAccess.OWNER && access != PlaceAccess.COLLABORATOR)) {
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
                        markerStyle = place.markerStyle,
                        members = place.members
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

    fun onPhotosAdded(uris: List<String>) = _uiState.update { state ->
        val known = state.photos.map { it.uri }.toSet()
        val added = uris.distinct().filterNot { it in known }.map { DraftPhoto(it) }
        state.copy(photos = state.photos + added)
    }

    fun onPhotoRemoved(uri: String) = _uiState.update { state ->
        val marker = state.markerStyle
        state.copy(
            photos = state.photos.filterNot { it.uri == uri },
            markerStyle = if (marker is MarkerStyle.Photo && marker.photoId == uri) {
                MarkerStyle.CollectionDefault
            } else {
                marker
            }
        )
    }

    fun onPhotoPrivacyToggled(uri: String) = _uiState.update { state ->
        val photos = state.photos.map {
            if (it.uri == uri) it.copy(isPrivate = !it.isPrivate) else it
        }
        // A private photo must never be the marker, because markers are visible to others.
        val marker = state.markerStyle
        val markerStillValid = marker !is MarkerStyle.Photo ||
                photos.any { it.uri == marker.photoId && !it.isPrivate }
        state.copy(
            photos = photos,
            markerStyle = if (markerStillValid) marker else MarkerStyle.CollectionDefault
        )
    }

    fun onMarkerStyleChange(style: MarkerStyle) = _uiState.update { state ->
        val allowed = style !is MarkerStyle.Photo ||
                state.photos.any { it.uri == style.photoId && !it.isPrivate }
        if (allowed) state.copy(markerStyle = style) else state
    }

    fun onMemberAdded(email: String, role: MemberRole) {
        val normalized = email.trim().lowercase()
        if (!normalized.isValidEmail()) return
        _uiState.update { state ->
            if (state.members.any { it.email == normalized }) {
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

        val save: suspend () -> Result<Place> = when (mode) {
            DraftMode.New -> {
                { savePlace(NewPlace(details, state.photos.map { NewPlacePhoto(it.uri, it.isPrivate) })) }
            }
            is DraftMode.Editing -> {
                { updatePlace(mode.placeId, details) }
            }
            is DraftMode.Loading, is DraftMode.Unavailable -> return
        }

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            save().fold(
                onSuccess = { place ->
                    _uiState.update { it.copy(isSaving = false) }
                    _events.send(AddPlaceEvent.Saved(place.collectionId))
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isSaving = false, errorMessage = error.message ?: "Could not save this place.")
                    }
                }
            )
        }
    }

    fun clearDraft() {
        _uiState.value = AddPlaceUiState()
    }
}
