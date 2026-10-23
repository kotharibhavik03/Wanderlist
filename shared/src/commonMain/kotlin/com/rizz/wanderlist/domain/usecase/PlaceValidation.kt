package com.rizz.wanderlist.domain.usecase

import com.rizz.wanderlist.domain.model.MemberRole
import com.rizz.wanderlist.domain.model.PlaceDetails
import com.rizz.wanderlist.domain.model.PlaceMember
import com.rizz.wanderlist.domain.model.PlaceStatus
import com.rizz.wanderlist.util.isValidEmail

internal const val MAX_PLACE_NOTES = 2_000

/** Rules every saved place must follow, new or edited. Returns a message for the user, or null when valid. */
internal fun validatePlaceDetails(details: PlaceDetails): String? = when {
    details.latitude !in -90.0..90.0 || details.longitude !in -180.0..180.0 ->
        "That location is not valid."
    details.notes.length > MAX_PLACE_NOTES -> "Notes can be at most $MAX_PLACE_NOTES characters."
    details.rating != null && details.rating !in 1..5 -> "Rating must be between 1 and 5."
    details.status == PlaceStatus.WISHLIST &&
            (details.visitedOnEpochMillis != null || details.rating != null) ->
        "A place you want to visit has no visit date or rating."
    else -> null
}

/** Why an email can't be added to a place's people. */
sealed interface MemberEmailProblem {
    data object Invalid : MemberEmailProblem

    /** The owner's own email: they already have the place. */
    data object Yourself : MemberEmailProblem

    data class AlreadyAdded(val role: MemberRole) : MemberEmailProblem
}

/**
 * Checks an email before adding it to a place owned by [ownerEmail] whose people are
 * [members]. Returns null when it can be added. Emails are compared trimmed and lowercase,
 * the way they're stored.
 */
internal fun memberEmailProblem(email: String, ownerEmail: String?, members: List<PlaceMember>): MemberEmailProblem? {
    val normalized = email.trim().lowercase()
    return when {
        !normalized.isValidEmail() -> MemberEmailProblem.Invalid
        ownerEmail != null && normalized == ownerEmail.lowercase() -> MemberEmailProblem.Yourself
        else -> members.firstOrNull { it.email == normalized }?.let { MemberEmailProblem.AlreadyAdded(it.role) }
    }
}
