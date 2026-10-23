package com.rizz.wanderlist.domain.model

/** Everything the user decides when saving. The repository adds id, owner and timestamps. */
data class NewPlace(
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
    val photos: List<NewPlacePhoto>,
    val markerStyle: MarkerStyle,
    val members: List<PlaceMember>
)

data class NewPlacePhoto(val uri: String, val isPrivate: Boolean)