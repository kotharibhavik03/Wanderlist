package com.rizz.wanderlist.data.firestore

import com.rizz.wanderlist.domain.model.PlaceCollection
import kotlinx.serialization.Serializable

/**
 * How a collection is stored in Firestore at `collections/{id}`. Field names here are the
 * database schema, so renaming one is a migration; firestore.rules checks the same names.
 * Defaults only make decoding tolerant of documents missing a field.
 */
@Serializable
internal data class CollectionDocument(
    val ownerId: String = "",
    val name: String = "",
    val icon: String = "",
    val colors: List<Long> = emptyList(),
    /** Client clock, epoch millis. Only used to keep collections in creation order. */
    val createdAt: Long = 0
)

internal fun CollectionDocument.toDomain(id: String) = PlaceCollection(
    id = id,
    name = name,
    icon = icon,
    colors = colors
)
