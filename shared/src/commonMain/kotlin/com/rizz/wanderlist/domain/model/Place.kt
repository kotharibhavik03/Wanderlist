package com.rizz.wanderlist.domain.model

data class Place(
    val id: String,
    val ownerId: String,
    /** Copied from the owner's account when the place is created, so people it's shared with can see who added them. */
    val ownerName: String? = null,
    val ownerEmail: String? = null,
    val collectionId: String,
    val actualName: String,          // name from the map
    val customName: String,          // the user's own name; may be blank
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val status: PlaceStatus,
    val visitedOnEpochMillis: Long?, // UTC midnight of the visit date
    val rating: Int?,                // 1..5
    val notes: String,
    val photos: List<PlacePhoto> = emptyList(),
    val markerStyle: MarkerStyle = MarkerStyle.CollectionDefault,
    val members: List<PlaceMember> = emptyList(),
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
) {
    val displayName: String get() = customName.ifBlank { actualName }

    /**
     * The photo to draw as this place's marker, or null to fall back to the collection's badge.
     * Saved places know where their marker photo is; a draft's is found among [photos].
     */
    val markerPhoto: PhotoImage?
        get() = (markerStyle as? MarkerStyle.Photo)?.let { marker ->
            marker.image ?: photos.firstOrNull { it.id == marker.photoId && !it.isPrivate }?.image
        }
}

enum class PlaceStatus { WISHLIST, VISITED }

data class PlacePhoto(
    val id: String,
    val uploaderId: String,
    val image: PhotoImage,
    val isPrivate: Boolean,
    val createdAtEpochMillis: Long
)

/**
 * Where a photo's files are in photo storage: the full photo (for the viewer) and its small copy
 * (for tiles and markers). Loading either checks, every time, that the user may see the photo.
 */
data class PhotoImage(val path: String, val thumbnailPath: String)

enum class MemberRole {
    COLLABORATOR, // can edit the place and add photos
    VISITED_WITH  // can view the place and add their own photos
}

data class PlaceMember(
    val email: String,
    val role: MemberRole,
    val userId: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null
)

sealed interface MarkerStyle {
    data object CollectionDefault : MarkerStyle
    data class Icon(val iconKey: String) : MarkerStyle
    /**
     * One of the place's shared photos. [image] is filled in for a saved place, so the map can
     * draw the marker without loading the place's photos; null while it's only a draft.
     */
    data class Photo(val photoId: String, val image: PhotoImage? = null) : MarkerStyle
}