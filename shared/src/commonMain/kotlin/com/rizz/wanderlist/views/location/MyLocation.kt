package com.rizz.wanderlist.views.location

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** The round "go to my location" button on maps. Shows a spinner while the position is found. */
@Composable
internal fun MyLocationButton(
    isLocating: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SmallFloatingActionButton(
        onClick = { if (!isLocating) onClick() },
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        modifier = modifier
    ) {
        if (isLocating) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Default.MyLocation, contentDescription = "Go to my location")
        }
    }
}

/**
 * What tapping "my location" does: asks for permission the first time, then [onAllowed];
 * [onPermissionAnswered] reports a fresh answer (to show or hide the blue dot), and [onRefused]
 * runs when the user said no (or turned it off in Settings).
 */
@Composable
internal fun rememberMyLocationClick(
    hasPermission: () -> Boolean,
    onPermissionAnswered: (granted: Boolean) -> Unit,
    onAllowed: () -> Unit,
    onRefused: () -> Unit
): () -> Unit {
    val requester = rememberLocationPermissionRequester()
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            val granted = hasPermission() || requester.request().also(onPermissionAnswered)
            if (granted) onAllowed() else onRefused()
        }
    }
}
