package com.rizz.wanderlist.views.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizz.wanderlist.domain.model.User
import com.rizz.wanderlist.domain.repository.AuthRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface ProfileUiState {
    /** Also shown for the moment between signing out and the sign-in screen appearing. */
    data object Loading : ProfileUiState
    data class Content(val user: User) : ProfileUiState
}

class ProfileViewModel(authRepository: AuthRepository) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> = authRepository.currentUser
        .map { user -> if (user == null) ProfileUiState.Loading else ProfileUiState.Content(user) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState.Loading)
}
