package com.rizz.wanderlist.data.firestore

import com.rizz.wanderlist.domain.model.MarkerStyle
import com.rizz.wanderlist.domain.model.MemberRole
import com.rizz.wanderlist.domain.model.Place
import com.rizz.wanderlist.domain.model.PlaceDetails
import com.rizz.wanderlist.domain.model.PlaceMember
import com.rizz.wanderlist.domain.model.PlaceStatus
import kotlinx.serialization.Serializable

/**
 * How a place is stored in Firestore at `places/{id}`. Field names are the database schema;
 * firestore.rules checks the same names. Enums are stored as their names and read back
 * leniently, so an unknown value from a newer app version doesn't break decoding.
 *
 * Photos are not stored here: rules can't hide part of a document, so each photo is its own
 * document in the `photos` subcollection (see PhotoDocument).
 */
@Serializable
internal data class PlaceDocument(
    val ownerId: String = "",
    /** Copied from the owner's account at creation; people the place is shared with can't look up other users. */
    val ownerName: String? = null,
    val ownerEmail: String? = null,
    val collectionId: String = "",
    val actualName: String = "",
    val customName: String = "",
    val address: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val status: String = PlaceStatus.WISHLIST.name,
    val visitedOn: Long? = null,
    val rating: Int? = null,
    val notes: String = "",
    val marker: MarkerDocument = MarkerDocument(),
    val members: List<MemberDocument> = emptyList(),
    /** Every member's email. Lets "Shared with me" be one array-contains query, and rules check read access. */
    val memberEmails: List<String> = emptyList(),
    /** Collaborators' emails. Rules can't search inside [members], so edit access is checked here. */
    val collaboratorEmails: List<String> = emptyList(),
    /** Client clock, epoch millis. */
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

@Serializable
internal data class MarkerDocument(
    val type: String = TYPE_COLLECTION,
    val iconKey: String? = null,
    val photoId: String? = null
) {
    companion object {
        const val TYPE_COLLECTION = "COLLECTION"
        const val TYPE_ICON = "ICON"
        const val TYPE_PHOTO = "PHOTO"
    }
}

@Serializable
internal data class MemberDocument(
    val email: String = "",
    val role: String = MemberRole.VISITED_WITH.name
)

internal fun PlaceDocument.toDomain(id: String) = Place(
    id = id,
    ownerId = ownerId,
    ownerName = ownerName,
    ownerEmail = ownerEmail,
    collectionId = collectionId,
    actualName = actualName,
    customName = customName,
    address = address,
    latitude = latitude,
    longitude = longitude,
    status = PlaceStatus.entries.firstOrNull { it.name == status } ?: PlaceStatus.WISHLIST,
    visitedOnEpochMillis = visitedOn,
    rating = rating,
    notes = notes,
    photos = emptyList(),
    markerStyle = marker.toDomain(),
    members = members.map { it.toDomain() },
    createdAtEpochMillis = createdAt,
    updatedAtEpochMillis = updatedAt
)

/** A full document for [details]. The membership lists are always derived from [PlaceDetails.members]. */
internal fun PlaceDetails.toDocument(
    owner: PlaceOwner,
    createdAt: Long,
    updatedAt: Long
): PlaceDocument {
    val memberDocuments = members.map { MemberDocument(it.email.lowercase(), it.role.name) }
    return PlaceDocument(
        ownerId = owner.id,
        ownerName = owner.name,
        ownerEmail = owner.email,
        collectionId = collectionId,
        actualName = actualName,
        customName = customName,
        address = address,
        latitude = latitude,
        longitude = longitude,
        status = status.name,
        visitedOn = visitedOnEpochMillis,
        rating = rating,
        notes = notes,
        marker = markerStyle.toDocument(),
        members = memberDocuments,
        memberEmails = memberDocuments.map { it.email },
        collaboratorEmails = memberDocuments.filter { it.role == MemberRole.COLLABORATOR.name }.map { it.email },
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

/**
 * The only fields a collaborator's edit writes. Must match the collaborator branch of the
 * places update rule in firestore.rules, which rejects a change to any other field.
 */
internal val COLLABORATOR_EDIT_FIELDS = arrayOf(
    "actualName", "customName", "address", "latitude", "longitude",
    "status", "visitedOn", "rating", "notes", "marker", "updatedAt"
)

/** The only fields leaving a place writes. Must match the leave branch of the places update rule. */
internal val MEMBERSHIP_FIELDS = arrayOf("members", "memberEmails", "collaboratorEmails", "updatedAt")

/** Who owns a place, as stored on it. */
internal data class PlaceOwner(val id: String, val name: String?, val email: String?)

internal val PlaceDocument.owner get() = PlaceOwner(ownerId, ownerName, ownerEmail)

/** The same place without one member, keeping the derived email lists in step. */
internal fun PlaceDocument.withoutMember(email: String, updatedAt: Long) = copy(
    members = members.filterNot { it.email == email },
    memberEmails = memberEmails.filterNot { it == email },
    collaboratorEmails = collaboratorEmails.filterNot { it == email },
    updatedAt = updatedAt
)

private fun MarkerDocument.toDomain(): MarkerStyle = when (type) {
    MarkerDocument.TYPE_ICON -> iconKey?.let { MarkerStyle.Icon(it) } ?: MarkerStyle.CollectionDefault
    MarkerDocument.TYPE_PHOTO -> photoId?.let { MarkerStyle.Photo(it) } ?: MarkerStyle.CollectionDefault
    else -> MarkerStyle.CollectionDefault
}

private fun MarkerStyle.toDocument(): MarkerDocument = when (this) {
    MarkerStyle.CollectionDefault -> MarkerDocument(MarkerDocument.TYPE_COLLECTION)
    is MarkerStyle.Icon -> MarkerDocument(MarkerDocument.TYPE_ICON, iconKey = iconKey)
    is MarkerStyle.Photo -> MarkerDocument(MarkerDocument.TYPE_PHOTO, photoId = photoId)
}

private fun MemberDocument.toDomain() = PlaceMember(
    email = email,
    role = MemberRole.entries.firstOrNull { it.name == role } ?: MemberRole.VISITED_WITH
)
