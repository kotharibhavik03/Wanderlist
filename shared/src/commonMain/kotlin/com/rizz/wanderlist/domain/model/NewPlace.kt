package com.rizz.wanderlist.domain.model

/** Everything the user decides when saving. The repository adds id, owner and timestamps. */
data class NewPlace(
    val details: PlaceDetails,
    val photos: List<NewPlacePhoto>
)

data class NewPlacePhoto(val uri: String, val isPrivate: Boolean)
