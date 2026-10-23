package com.rizz.wanderlist.domain.model

/** The fields a person sets on a place, shared by creating and editing it. */
data class PlaceDetails(
    val collectionId: String,
    val actualName: String,
    val customName: String,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val status: PlaceStatus,
    val visitedOnEpochMillis: Long?,
    val rating: Int?,
    val notes: String,
    val markerStyle: MarkerStyle,
    val members: List<PlaceMember>
)
