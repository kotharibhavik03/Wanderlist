package com.rizz.wanderlist.domain.model

/** What a user may do with a place. Mirrors the backend security rules. */
enum class PlaceAccess {
    OWNER,        // created it; sees its collection; can edit and delete
    COLLABORATOR, // can edit the place and add photos
    VISITED_WITH  // can view the place and add their own photos
}

/** The [user]'s access to this place, or null when it isn't theirs and wasn't shared with them. */
fun Place.accessFor(user: User): PlaceAccess? {
    if (ownerId == user.id) return PlaceAccess.OWNER
    // Member emails are stored lowercase, see AddPlaceViewModel.onMemberAdded.
    val myEmail = user.email?.lowercase() ?: return null
    return when (members.firstOrNull { it.email == myEmail }?.role) {
        MemberRole.COLLABORATOR -> PlaceAccess.COLLABORATOR
        MemberRole.VISITED_WITH -> PlaceAccess.VISITED_WITH
        null -> null
    }
}

/** Everyone on a place can add their own photos. They can delete their own; the owner can delete any. */
fun Place.canDeletePhoto(user: User, photo: PlacePhoto): Boolean =
    photo.uploaderId == user.id || ownerId == user.id
