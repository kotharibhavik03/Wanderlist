package com.rizz.wanderlist.views.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizz.wanderlist.domain.usecase.CollectionError
import com.rizz.wanderlist.domain.usecase.ObserveCollectionSummariesUseCase
import com.rizz.wanderlist.domain.usecase.SaveCollectionResult
import com.rizz.wanderlist.domain.usecase.SaveCollectionUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CollectionFormUiState(
    /** The collection being edited, or null when creating a new one. */
    val collectionId: String? = null,
    /** Fetching the collection to edit. */
    val isLoading: Boolean = false,
    /** The collection to edit no longer exists (e.g. deleted on another device). */
    val notFound: Boolean = false,
    val name: String = "",
    val icon: String = CollectionOptions.icons.first().value,
    val colors: List<Long> = listOf(CollectionOptions.colors.first().value),
    /** Shown on the preview card; always 0 for a new collection. */
    val placeCount: Int = 0,
    val error: CollectionError? = null,
    val isSaving: Boolean = false,
    /** Saving failed after validation passed; shown once in a snackbar. */
    val saveErrorMessage: String? = null
) {
    val isEditing: Boolean get() = collectionId != null

    val canSave: Boolean get() = name.isNotBlank() && !isSaving && !isLoading && !notFound
}

sealed interface CollectionFormEvent {
    data object Saved : CollectionFormEvent
}

/** The new / edit collection form. [collectionId] is null for a new collection. */
class CollectionFormViewModel(
    private val collectionId: String?,
    private val saveCollection: SaveCollectionUseCase,
    observeCollectionSummaries: ObserveCollectionSummariesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CollectionFormUiState(collectionId = collectionId, isLoading = collectionId != null)
    )
    val uiState: StateFlow<CollectionFormUiState> = _uiState.asStateFlow()

    private val _events = Channel<CollectionFormEvent>(Channel.BUFFERED)
    val events: Flow<CollectionFormEvent> = _events.receiveAsFlow()

    init {
        if (collectionId != null) {
            viewModelScope.launch {
                val summary = observeCollectionSummaries().first()
                    .firstOrNull { it.collection.id == collectionId }
                _uiState.update { state ->
                    if (summary == null) {
                        state.copy(isLoading = false, notFound = true)
                    } else {
                        state.copy(
                            isLoading = false,
                            name = summary.collection.name,
                            icon = summary.collection.icon,
                            colors = summary.collection.colors,
                            placeCount = summary.placeCount
                        )
                    }
                }
            }
        }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name, error = null) }
    }

    fun onIconSelected(icon: String) {
        _uiState.update { it.copy(icon = icon) }
    }

    fun onColorToggled(color: Long) {
        _uiState.update { state ->
            val colors = state.colors
            val updated = when {
                color !in colors -> colors + color
                colors.size > 1 -> colors - color
                else -> colors
            }
            state.copy(colors = updated)
        }
    }

    fun onSaveClick() {
        val current = _uiState.value
        if (!current.canSave) return

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val result = saveCollection(collectionId, current.name, current.icon, current.colors)) {
                is SaveCollectionResult.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.send(CollectionFormEvent.Saved)
                }
                is SaveCollectionResult.Invalid ->
                    _uiState.update { it.copy(isSaving = false, error = result.error) }
                is SaveCollectionResult.Failed ->
                    _uiState.update { it.copy(isSaving = false, saveErrorMessage = result.message) }
            }
        }
    }

    fun onSaveErrorShown() {
        _uiState.update { it.copy(saveErrorMessage = null) }
    }
}
