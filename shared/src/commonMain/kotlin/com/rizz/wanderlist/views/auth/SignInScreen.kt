package com.rizz.wanderlist.views.auth
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmk.kmpauth.google.rememberGoogleAuthState
import com.mmk.kmpauth.uihelper.google.GoogleSignInButton
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SignInRoute(viewModel: SignInViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val googleAuth = rememberGoogleAuthState(onResult = { result ->
        result.onFailure { viewModel.onSignInFailed(it) }
    })

    SignInScreen(
        errorMessage = uiState.errorMessage,
        isSigningIn = googleAuth.isInProgress,
        onGoogleClick = {
            viewModel.onSignInStarted()
            googleAuth.launch()
        }
    )
}

@Composable
fun SignInScreen(
    errorMessage: String?,
    isSigningIn: Boolean,
    onGoogleClick: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🧭", fontSize = 64.sp)
            Spacer(Modifier.height(16.dp))
            Text("Wanderlist", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Keep the places you've been, and the ones you dream of.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(40.dp))

            if (isSigningIn) {
                CircularProgressIndicator()
            } else {
                GoogleSignInButton(
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    onGoogleClick()
                }
            }

            if (errorMessage != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}