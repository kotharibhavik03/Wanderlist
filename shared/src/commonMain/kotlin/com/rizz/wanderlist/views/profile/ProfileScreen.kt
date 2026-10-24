package com.rizz.wanderlist.views.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rizz.wanderlist.views.photo.rememberPhotoPicker
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ProfileRoute(
    onSignOut: () -> Unit,
    viewModel: ProfileViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val photoPicker = rememberPhotoPicker(multiple = false, onPicked = viewModel::onPhotoPicked)
    ProfileScreen(
        uiState = uiState,
        onChangePhoto = photoPicker::launch,
        onUseAccountPhoto = viewModel::onUseAccountPhoto,
        onMessageShown = viewModel::onMessageShown,
        onSignOut = onSignOut
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    uiState: ProfileUiState,
    onChangePhoto: () -> Unit,
    onUseAccountPhoto: () -> Unit,
    onMessageShown: () -> Unit,
    onSignOut: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val message = (uiState as? ProfileUiState.Content)?.message
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text("Profile") }) }
    ) { padding ->
        when (uiState) {
            ProfileUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            is ProfileUiState.Content -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                EditableAvatar(uiState, onChangePhoto)
                Spacer(Modifier.height(16.dp))
                Text(
                    uiState.user.displayName ?: "Wanderlist traveller",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                uiState.user.email?.let { email ->
                    Text(
                        email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                Text(
                    "Signed in with Google",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onChangePhoto, enabled = !uiState.isUpdatingPhoto) {
                        Text("Change photo")
                    }
                    if (uiState.hasCustomPhoto) {
                        TextButton(onClick = onUseAccountPhoto, enabled = !uiState.isUpdatingPhoto) {
                            Text("Use Google photo")
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign out")
                }
            }
        }
    }
}

/** The profile photo with a camera button on its edge; a spinner covers it while it changes. */
@Composable
private fun EditableAvatar(uiState: ProfileUiState.Content, onChangePhoto: () -> Unit) {
    Box {
        UserAvatar(
            name = uiState.user.displayName,
            email = uiState.user.email,
            photoUrl = uiState.photoUrl,
            size = 112.dp
        )
        if (uiState.isUpdatingPhoto) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Color.White) }
        }
        FilledTonalIconButton(
            onClick = onChangePhoto,
            enabled = !uiState.isUpdatingPhoto,
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Icon(Icons.Default.PhotoCamera, contentDescription = "Change profile photo")
        }
    }
}
