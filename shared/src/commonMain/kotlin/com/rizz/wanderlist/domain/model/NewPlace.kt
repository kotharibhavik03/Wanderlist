package com.rizz.wanderlist.domain.model

/** Everything the user decides when saving. The repository adds id, owner and timestamps. */
data class NewPlace(
    val details: PlaceDetails,
    val photos: List<NewPlacePhoto>
)

/**
 * A compressed photo waiting to be uploaded with a new place. [id] becomes the stored
 * photo's id, so a [MarkerStyle.Photo] picked before saving keeps pointing at it.
 */
data class NewPlacePhoto(val id: String, val localPath: String, val isPrivate: Boolean)

/** How many photos one save can carry. See firestore.rules (photos) for why there's a cap. */
const val MAX_PHOTOS_PER_SAVE = 10
