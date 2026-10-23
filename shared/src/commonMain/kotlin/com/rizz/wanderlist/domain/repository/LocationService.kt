package com.rizz.wanderlist.domain.repository

import com.rizz.wanderlist.domain.model.GeoPoint

/**
 * The device's own position. Asking for permission is a UI interaction, so it lives in
 * views/location (rememberLocationPermissionRequester); this only reads.
 */
interface LocationService {
    /** Whether the user has allowed the app to use their location (precise or approximate). */
    fun hasPermission(): Boolean

    /**
     * The device's current position, or null when it can't be found (location turned off,
     * no fix) or permission isn't granted. May take several seconds.
     */
    suspend fun currentLocation(): GeoPoint?
}
