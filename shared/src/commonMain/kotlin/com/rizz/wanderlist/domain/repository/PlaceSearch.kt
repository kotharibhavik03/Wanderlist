package com.rizz.wanderlist.domain.repository

import com.rizz.wanderlist.domain.model.SelectedLocation

/**
 * Finds locations anywhere by name or address. Implemented with each phone's own free search
 * (Android Geocoder, iOS MKLocalSearch); Google Places could replace it behind this interface.
 */
interface PlaceSearch {
    /** A few matches for [query], best first. Empty when nothing matches; throws when search is unavailable. */
    suspend fun search(query: String): List<SelectedLocation>
}
