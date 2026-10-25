package com.rizz.wanderlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.setSingletonImageLoaderFactory
import com.rizz.wanderlist.domain.repository.PhotoStorage
import com.rizz.wanderlist.navigation.AppNavigation
import com.rizz.wanderlist.views.auth.AuthUiState
import com.rizz.wanderlist.views.auth.AuthViewModel
import com.rizz.wanderlist.views.auth.SignInRoute
import com.rizz.wanderlist.views.image.newAppImageLoader
import com.rizz.wanderlist.views.theme.WanderlistTheme
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App() {
    val photoStorage = koinInject<PhotoStorage>()
    setSingletonImageLoaderFactory { context -> newAppImageLoader(context, photoStorage) }
    WanderlistTheme {
        val authViewModel: AuthViewModel = koinViewModel()
        val authState by authViewModel.uiState.collectAsStateWithLifecycle()

        when (authState) {
            AuthUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            AuthUiState.SignedOut -> SignInRoute()
            is AuthUiState.SignedIn -> AppNavigation(onSignOut = authViewModel::signOut)
        }
    }
}