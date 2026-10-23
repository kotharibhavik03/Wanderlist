package com.rizz.wanderlist.views.location

import androidx.compose.runtime.Composable

/** Asks the user for location permission. [request] suspends until they answer. */
fun interface LocationPermissionRequester {
    /** True when permission is (or already was) granted, precise or approximate. */
    suspend fun request(): Boolean
}

/**
 * A requester tied to the current screen: on Android it needs the Activity's result API,
 * on iOS a location manager.
 */
@Composable
expect fun rememberLocationPermissionRequester(): LocationPermissionRequester
