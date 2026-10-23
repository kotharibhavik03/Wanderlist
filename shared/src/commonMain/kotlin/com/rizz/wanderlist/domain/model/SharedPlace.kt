package com.rizz.wanderlist.domain.model

/**
 * A place someone else owns that the signed-in user was added to.
 * The invitee sees only the place, never the collection it belongs to.
 */
data class SharedPlace(
    val place: Place,
    val myRole: MemberRole
)
