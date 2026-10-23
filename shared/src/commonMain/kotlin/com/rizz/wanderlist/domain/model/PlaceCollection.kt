package com.rizz.wanderlist.domain.model

/**
 * A user-defined group of places, such as "Goa trip".
 *
 * Immutable by design: changes are made by creating a modified copy.
 * How many places it holds is derived from the places themselves; see [CollectionSummary].
 */
data class PlaceCollection(
    val id: String,
    val name: String,
    val icon: String,
    val colors: List<Long>
)
