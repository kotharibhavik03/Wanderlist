package com.rizz.wanderlist.views.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizz.wanderlist.domain.model.User
import com.rizz.wanderlist.domain.model.UserProfile
import com.rizz.wanderlist.domain.repository.AuthRepository
import com.rizz.wanderlist.domain.repository.PhotoCompressor
import com.rizz.wanderlist.domain.repository.ProfileRepository
import com.rizz.wanderlist.util.resultOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ProfileUiState {
    /** Also shown for the moment between signing out and the sign-in screen appearing. */
    data object Loading : ProfileUiState

    data class Content(
        val user: User,
        /** The photo everyone sees: the published profile's, else the account's (Google) photo. */
        val photoUrl: String?,
        /** The user uploaded [photoUrl] themselves, so they can go back to the Google photo. */
        val hasCustomPhoto: Boolean,
        /** Uploading a new photo, or going back to the Google one. */
        val isUpdatingPhoto: Boolean = false,
        /** A one-off message (photo changed, or why it failed); cleared once shown. */
        val message: String? = null
    ) : ProfileUiState
}

class ProfileViewModel(
    authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val photoCompressor: PhotoCompressor
) : ViewModel() {

    private data class PhotoState(val isUpdating: Boolean = false, val message: String? = null)

    private val photoState = MutableStateFlow(PhotoState())

    /** The signed-in user's published profile: what other people see. Live, so a change shows at once. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val myProfile: Flow<UserProfile?> = authRepository.currentUser
        .map { it?.email?.lowercase() }
        .distinctUntilChanged()
        .flatMapLatest { email ->
            if (email == null) flowOf(null) else profileRepository.observeProfiles(setOf(email)).map { it[email] }
        }

    val uiState: StateFlow<ProfileUiState> = combine(
        authRepository.currentUser,
        myProfile,
        photoState
    ) { user, profile, photo ->
        if (user == null) {
            ProfileUiState.Loading
        } else {
            ProfileUiState.Content(
                user = user,
                photoUrl = profile?.photoUrl ?: user.photoUrl,
                hasCustomPhoto = profile?.hasCustomPhoto == true,
                isUpdatingPhoto = photo.isUpdating,
                message = photo.message
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState.Loading)

    /** Compresses the picked photo (the same way as place photos) and makes it the profile photo. */
    fun onPhotoPicked(sources: List<String>) {
        val source = sources.firstOrNull() ?: return
        updatePhoto(success = "Profile photo updated.", failure = "Couldn't change your photo.") {
            val compressed = photoCompressor.compress(source)
            try {
                profileRepository.setMyPhoto(compressed.path)
            } finally {
                // Uploaded or not, the compressed copy isn't needed any more.
                photoCompressor.discard(compressed)
            }
        }
    }

    fun onUseAccountPhoto() =
        updatePhoto(success = "Using your Google photo.", failure = "Couldn't change your photo.") {
            profileRepository.useAccountPhoto()
        }

    fun onMessageShown() = photoState.update { it.copy(message = null) }

    /** Runs one photo change at a time and reports how it went. */
    private fun updatePhoto(success: String, failure: String, action: suspend () -> Unit) {
        if (photoState.value.isUpdating) return
        photoState.value = PhotoState(isUpdating = true)
        viewModelScope.launch {
            val result = resultOf { action() }
            photoState.value = PhotoState(
                message = result.exceptionOrNull()?.let { it.message ?: failure } ?: success
            )
        }
    }
}
