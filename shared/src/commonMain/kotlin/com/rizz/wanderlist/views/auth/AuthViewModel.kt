package com.rizz.wanderlist.views.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizz.wanderlist.domain.model.User
import com.rizz.wanderlist.domain.repository.AuthRepository
import com.rizz.wanderlist.domain.repository.ProfileRepository
import com.rizz.wanderlist.util.resultOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object Loading : AuthUiState
    data object SignedOut : AuthUiState
    data class SignedIn(val user: User) : AuthUiState
}

class AuthViewModel(
    private val authRepository: AuthRepository,
    profileRepository: ProfileRepository
) : ViewModel() {

    val uiState: StateFlow<AuthUiState> = authRepository.currentUser
        .map<User?, AuthUiState> { user ->
            if (user == null) AuthUiState.SignedOut else AuthUiState.SignedIn(user)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthUiState.Loading)

    init {
        // Publish the signed-in user's name, so people on their shared places see it.
        // On every sign-in, so a changed Google name reaches others too.
        viewModelScope.launch {
            authRepository.currentUser.filterNotNull().distinctUntilChanged().collect { user ->
                // A failure only means others see this user's email instead of their name.
                resultOf { profileRepository.saveMyProfile(user) }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }
}
