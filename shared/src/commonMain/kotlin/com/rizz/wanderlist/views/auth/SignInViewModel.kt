package com.rizz.wanderlist.views.auth

import androidx.lifecycle.ViewModel
import com.mmk.kmpauth.core.auth.KMPAuthUserCancelledException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SignInUiState(val errorMessage: String? = null)

class SignInViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    fun onSignInStarted() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun onSignInFailed(error: Throwable) {
        if (error is KMPAuthUserCancelledException) return
        _uiState.update {
            it.copy(errorMessage = error.message ?: "Sign-in failed. Please try again.")
        }
    }
}