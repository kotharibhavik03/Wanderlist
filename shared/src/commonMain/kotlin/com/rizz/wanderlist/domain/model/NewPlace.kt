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

/** What an edit changes about a place's photos, applied when the edit is saved. */
data class PhotoChanges(
    /** New photos to upload, as the signed-in user's. */
    val added: List<NewPlacePhoto> = emptyList(),
    /** The user's own photos whose privacy changed: photo id to the new "private" value. */
    val privacy: Map<String, Boolean> = emptyMap(),
    /** Photos to delete. */
    val removed: Set<String> = emptySet()
)
