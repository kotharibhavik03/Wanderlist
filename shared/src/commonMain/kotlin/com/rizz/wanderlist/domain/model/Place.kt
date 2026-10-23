package com.rizz.wanderlist.domain.model

data class Place(
    val id: String,
    val ownerId: String,
    val collectionId: String,
    val actualName: String,          // name from the map
    val customName: String,          // the user's own name; may be blank
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val status: PlaceStatus,
    val visitedOnEpochMillis: Long?, // UTC midnight of the visit date
    val rating: Int?,                // 1..5
    val notes: String,
    val photos: List<PlacePhoto> = emptyList(),
    val markerStyle: MarkerStyle = MarkerStyle.CollectionDefault,
    val members: List<PlaceMember> = emptyList(),
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
) {
    val displayName: String get() = customName.ifBlank { actualName }
}

enum class PlaceStatus { WISHLIST, VISITED }

data class PlacePhoto(
    val id: String,
    val uploaderId: String,
    val url: String,
    val isPrivate: Boolean
)

enum class MemberRole {
    COLLABORATOR, // can edit the place and add photos
    VISITED_WITH  // can view the place and add their own photos
}

data class PlaceMember(
    val email: String,
    val role: MemberRole,
    val userId: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null
)

sealed interface MarkerStyle {
    data object CollectionDefault : MarkerStyle
    data class Icon(val iconKey: String) : MarkerStyle
    data class Photo(val photoId: String) : MarkerStyle
}