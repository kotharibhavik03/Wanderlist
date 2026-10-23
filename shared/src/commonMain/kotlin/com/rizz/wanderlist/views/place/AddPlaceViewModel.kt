package com.rizz.wanderlist.views.place

import kotlinx.coroutines.launch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizz.wanderlist.domain.model.CollectionSummary
import com.rizz.wanderlist.domain.model.MarkerStyle
import com.rizz.wanderlist.domain.model.MemberRole
import com.rizz.wanderlist.domain.model.NewPlace
import com.rizz.wanderlist.domain.model.NewPlacePhoto
import com.rizz.wanderlist.domain.model.PlaceMember
import com.rizz.wanderlist.domain.model.PlaceStatus
import com.rizz.wanderlist.domain.model.SelectedLocation
import com.rizz.wanderlist.domain.usecase.ObserveCollectionSummariesUseCase
import com.rizz.wanderlist.domain.usecase.SavePlaceUseCase
import com.rizz.wanderlist.util.isValidEmail
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** A photo picked but not uploaded yet. The uri doubles as its id inside the draft. */
data class DraftPhoto(val uri: String, val isPrivate: Boolean = false)

sealed interface AddPlaceEvent {
    data class Saved(val collectionId: String) : AddPlaceEvent
}

data class AddPlaceUiState(
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
    val canSave: Boolean
        get() = selectedLocation != null && selectedCollectionId != null && !isSaving
}

/**
 * Holds the add-place draft. It is shared between the Add Place and Map Picker
 * screens, so AppNavigation owns it and clears it when the flow ends.
 */
class AddPlaceViewModel(
    observeCollectionSummaries: ObserveCollectionSummariesUseCase,
    private val savePlace: SavePlaceUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddPlaceUiState())
    val uiState: StateFlow<AddPlaceUiState> = _uiState.asStateFlow()

    private val _events = Channel<AddPlaceEvent>(Channel.BUFFERED)
    val events: Flow<AddPlaceEvent> = _events.receiveAsFlow()

    val collections: StateFlow<List<CollectionSummary>> =
        observeCollectionSummaries()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
        val location = state.selectedLocation ?: return
        val collectionId = state.selectedCollectionId ?: return
        if (state.isSaving) return // ignore double taps

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val result = savePlace(
                NewPlace(
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
                    photos = state.photos.map { NewPlacePhoto(it.uri, it.isPrivate) },
                    markerStyle = state.markerStyle,
                    members = state.members
                )
            )
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.send(AddPlaceEvent.Saved(collectionId))
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