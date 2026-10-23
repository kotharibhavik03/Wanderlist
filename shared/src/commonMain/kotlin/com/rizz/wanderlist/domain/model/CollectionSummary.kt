package com.rizz.wanderlist.domain.model

/** A collection together with how many places it holds, for lists and pickers. */
data class CollectionSummary(
    val collection: PlaceCollection,
    val placeCount: Int
)
