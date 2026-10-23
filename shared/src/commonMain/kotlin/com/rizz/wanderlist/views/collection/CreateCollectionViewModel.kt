package com.rizz.wanderlist.views.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizz.wanderlist.domain.usecase.CollectionError
import com.rizz.wanderlist.domain.usecase.CreateCollectionResult
import com.rizz.wanderlist.domain.usecase.CreateCollectionUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateCollectionUiState(
    val name: String = "",
    val icon: String = CollectionOptions.icons.first().value,
    val colors: List<Long> = listOf(CollectionOptions.colors.first().value),
    val error: CollectionError? = null,
    val isSaving: Boolean = false
) {
    val canSave: Boolean get() = name.isNotBlank() && !isSaving
}

sealed interface CreateCollectionEvent {
    data object Saved : CreateCollectionEvent
}

class CreateCollectionViewModel(
    private val createCollection: CreateCollectionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateCollectionUiState())
    val uiState: StateFlow<CreateCollectionUiState> = _uiState.asStateFlow()

    private val _events = Channel<CreateCollectionEvent>(Channel.BUFFERED)
    val events: Flow<CreateCollectionEvent> = _events.receiveAsFlow()

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
            val result = createCollection(current.name, current.icon, current.colors)
            when (result) {
                is CreateCollectionResult.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.send(CreateCollectionEvent.Saved)
                }
                is CreateCollectionResult.Invalid ->
                    _uiState.update { it.copy(isSaving = false, error = result.error) }
            }
        }
    }
}