package com.rizz.wanderlist.views.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizz.wanderlist.domain.model.User
import com.rizz.wanderlist.domain.repository.AuthRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object Loading : AuthUiState
    data object SignedOut : AuthUiState
    data class SignedIn(val user: User) : AuthUiState
}

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    val uiState: StateFlow<AuthUiState> = authRepository.currentUser
        .map<User?, AuthUiState> { user ->
            if (user == null) AuthUiState.SignedOut else AuthUiState.SignedIn(user)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthUiState.Loading)

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }
}