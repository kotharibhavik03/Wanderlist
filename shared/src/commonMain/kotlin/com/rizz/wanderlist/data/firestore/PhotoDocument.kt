package com.rizz.wanderlist.data.firestore

import com.rizz.wanderlist.domain.model.PlacePhoto
import kotlinx.serialization.Serializable

/**
 * How a photo is stored in Firestore at `places/{placeId}/photos/{photoId}`. One document per
 * photo, so firestore.rules can hide a private photo from everyone but its uploader (rules
 * can't hide part of a document). Field names are checked by firestore.rules.
 */
@Serializable
internal data class PhotoDocument(
    val uploaderId: String = "",
    /** Firebase Storage download URL. Anyone holding it can download the file. */
    val url: String = "",
    val isPrivate: Boolean = false,
    /** Client clock, epoch millis. */
    val createdAt: Long = 0
)

internal fun PhotoDocument.toDomain(id: String) = PlacePhoto(
    id = id,
    uploaderId = uploaderId,
    url = url,
    isPrivate = isPrivate
)

/**
 * Where a photo's file lives in Storage. The uploader's id is part of the path, so
 * storage.rules can let people write and delete only their own files.
 */
internal fun photoStoragePath(placeId: String, uploaderId: String, photoId: String) =
    "places/$placeId/$uploaderId/$photoId.jpg"
