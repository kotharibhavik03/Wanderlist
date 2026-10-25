package com.rizz.wanderlist.data.firestore

import com.rizz.wanderlist.domain.model.PhotoImage
import com.rizz.wanderlist.domain.model.PlacePhoto
import kotlinx.serialization.Serializable

/**
 * How a photo is stored in Firestore at `places/{placeId}/photos/{photoId}`. One document per
 * photo, so firestore.rules can hide a private photo from everyone but its uploader (rules
 * can't hide part of a document). Field names are checked by firestore.rules.
 *
 * Its files' location follows from the place, the uploader and the photo id ([photoImage]), so
 * nothing about Storage is stored here.
 */
@Serializable
internal data class PhotoDocument(
    val uploaderId: String = "",
    val isPrivate: Boolean = false,
    /** Client clock, epoch millis. */
    val createdAt: Long = 0
)

internal fun PhotoDocument.toDomain(placeId: String, id: String) = PlacePhoto(
    id = id,
    uploaderId = uploaderId,
    image = photoImage(placeId, uploaderId, id),
    isPrivate = isPrivate,
    createdAtEpochMillis = createdAt
)

/**
 * Where a photo's files live in Storage. The uploader's id is part of the path, so
 * storage.rules can let people write and delete only their own files, and the photo id lets
 * it find the photo's document to decide who may download them.
 */
internal fun photoImage(placeId: String, uploaderId: String, photoId: String) = PhotoImage(
    path = "places/$placeId/$uploaderId/$photoId.jpg",
    thumbnailPath = "places/$placeId/$uploaderId/${photoId}_thumb.jpg"
)
