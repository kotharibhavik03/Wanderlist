package com.rizz.wanderlist.domain.usecase

import com.rizz.wanderlist.domain.model.PlaceDetails
import com.rizz.wanderlist.domain.model.PlaceStatus

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
