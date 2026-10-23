package com.rizz.wanderlist.views.location

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.CompletableDeferred

private val LocationPermissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

/** Holds the answer the system dialog will deliver to the launcher callback. */
private class PendingAnswer {
    var deferred: CompletableDeferred<Boolean>? = null
}

@Composable
actual fun rememberLocationPermissionRequester(): LocationPermissionRequester {
    val pending = remember { PendingAnswer() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        // Approximate-only (coarse) still counts: the map can centre on it.
        pending.deferred?.complete(results.values.any { it })
        pending.deferred = null
    }
    return remember(launcher) {
        LocationPermissionRequester {
            val answer = CompletableDeferred<Boolean>()
            pending.deferred = answer
            launcher.launch(LocationPermissions)
            answer.await()
        }
    }
}
