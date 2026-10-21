package com.rizz.wanderlist.domain.model

/**
 * A user-defined group of places, such as "Goa trip".
 *
 * Immutable by design: changes are made by creating a modified copy.
 */
data class PlaceCollection(
    val id: String,
    val name: String,
    val icon: String,
    val colors: List<Long>,
    val placeCount: Int = 0
)