package com.rizz.wanderlist.domain.repository

import com.rizz.wanderlist.domain.model.SelectedLocation

/**
 * Finds locations anywhere by name or address, and names a point on the map. Implemented with
 * each phone's own free services (Android Geocoder, iOS MKLocalSearch and CLGeocoder); Google
 * Places could replace it behind this interface.
 */
interface PlaceSearch {
    /** A few matches for [query], best first. Empty when nothing matches; throws when search is unavailable. */
    suspend fun search(query: String): List<SelectedLocation>

    /**
     * What is at this point (a building, street or area) with its address, or null when the
     * point has no address, such as open sea. Throws when the service is unavailable.
     */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): SelectedLocation?
}
