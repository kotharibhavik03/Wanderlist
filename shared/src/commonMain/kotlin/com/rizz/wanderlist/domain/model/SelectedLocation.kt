package com.rizz.wanderlist.domain.model

/**
 * A point on the map with a name: found by search, or picked in the location picker.
 * It becomes a place's location once the place is saved.
 */
data class SelectedLocation(
    val latitude: Double,
    val longitude: Double,
    val actualName: String,
    val address: String? = null,
    val placeId: String? = null
)
