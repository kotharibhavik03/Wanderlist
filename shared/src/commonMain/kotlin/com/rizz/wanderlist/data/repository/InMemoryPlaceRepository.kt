package com.rizz.wanderlist.data.repository

import com.rizz.wanderlist.domain.model.MarkerStyle
import com.rizz.wanderlist.domain.model.MemberRole
import com.rizz.wanderlist.domain.model.NewPlace
import com.rizz.wanderlist.domain.model.Place
import com.rizz.wanderlist.domain.model.PlaceAccess
import com.rizz.wanderlist.domain.model.PlaceDetails
import com.rizz.wanderlist.domain.model.PlacePhoto
import com.rizz.wanderlist.domain.model.SharedPlace
import com.rizz.wanderlist.domain.model.User
import com.rizz.wanderlist.domain.model.accessFor
import com.rizz.wanderlist.domain.repository.AuthRepository
import com.rizz.wanderlist.domain.repository.PlaceRepository
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Temporary store that starts empty. Replaced by the Firestore implementation. */
class InMemoryPlaceRepository(private val authRepository: AuthRepository) : PlaceRepository {

    private val places = MutableStateFlow<List<Place>>(emptyList())

    override fun observePlaces(collectionId: String): Flow<List<Place>> =
        combine(places, authRepository.currentUser) { all, user ->
            if (user == null) return@combine emptyList()
            all.filter { it.collectionId == collectionId }
                .sortedByDescending { it.createdAtEpochMillis }
                .map { it.visibleTo(user) }
        }

    override fun observePlace(placeId: String): Flow<Place?> =
        combine(places, authRepository.currentUser) { all, user ->
            if (user == null) return@combine null
            all.firstOrNull { it.id == placeId }
                ?.takeIf { it.accessFor(user) != null }
                ?.visibleTo(user)
        }

    override fun observeVisiblePlaces(): Flow<List<Place>> =
        combine(places, authRepository.currentUser) { all, user ->
            if (user == null) return@combine emptyList()
            all.filter { it.accessFor(user) != null }
                .sortedByDescending { it.createdAtEpochMillis }
                .map { it.visibleTo(user) }
        }

    override fun observePlaceCounts(): Flow<Map<String, Int>> =
        places.map { all -> all.groupingBy { it.collectionId }.eachCount() }

    override fun observeSharedWithMe(): Flow<List<SharedPlace>> =
        combine(places, authRepository.currentUser) { all, user ->
            if (user == null) return@combine emptyList()
            all.mapNotNull { place ->
                when (place.accessFor(user)) {
                    PlaceAccess.COLLABORATOR -> SharedPlace(place.visibleTo(user), MemberRole.COLLABORATOR)
                    PlaceAccess.VISITED_WITH -> SharedPlace(place.visibleTo(user), MemberRole.VISITED_WITH)
                    PlaceAccess.OWNER, null -> null
                }
            }.sortedByDescending { it.place.createdAtEpochMillis }
        }

    override suspend fun createPlace(newPlace: NewPlace): Place {
        val owner = requireSignedInUser()
        val details = newPlace.details
        val now = Clock.System.now().toEpochMilliseconds()
        val place = Place(
            id = "place-$now-${places.value.size}",
            ownerId = owner.id,
            collectionId = details.collectionId,
            actualName = details.actualName,
            customName = details.customName,
            address = details.address,
            latitude = details.latitude,
            longitude = details.longitude,
            status = details.status,
            visitedOnEpochMillis = details.visitedOnEpochMillis,
            rating = details.rating,
            notes = details.notes,
            photos = newPlace.photos.map {
                PlacePhoto(id = it.uri, uploaderId = owner.id, url = it.uri, isPrivate = it.isPrivate)
            },
            markerStyle = details.markerStyle,
            members = details.members,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now
        )
        places.update { it + place }
        return place
    }

    override suspend fun updatePlace(placeId: String, details: PlaceDetails): Place {
        val user = requireSignedInUser()
        val current = requirePlace(placeId)
        when (current.accessFor(user)) {
            PlaceAccess.OWNER -> Unit
            PlaceAccess.COLLABORATOR -> require(
                details.collectionId == current.collectionId && details.members == current.members
            ) { "Only the owner can change the collection or the people on this place." }
            PlaceAccess.VISITED_WITH, null -> error("You can't edit this place.")
        }
        val marker = details.markerStyle
        require(marker !is MarkerStyle.Photo || current.photos.any { it.id == marker.photoId && !it.isPrivate }) {
            "The marker photo must be one of the shared photos."
        }

        val updated = current.copy(
            collectionId = details.collectionId,
            actualName = details.actualName,
            customName = details.customName,
            address = details.address,
            latitude = details.latitude,
            longitude = details.longitude,
            status = details.status,
            visitedOnEpochMillis = details.visitedOnEpochMillis,
            rating = details.rating,
            notes = details.notes,
            markerStyle = details.markerStyle,
            members = details.members,
            updatedAtEpochMillis = Clock.System.now().toEpochMilliseconds()
        )
        places.update { all -> all.map { if (it.id == placeId) updated else it } }
        return updated.visibleTo(user)
    }

    override suspend fun leavePlace(placeId: String) {
        val user = requireSignedInUser()
        val current = requirePlace(placeId)
        when (current.accessFor(user)) {
            PlaceAccess.COLLABORATOR, PlaceAccess.VISITED_WITH -> Unit
            PlaceAccess.OWNER -> error("You own this place, so you can't leave it.")
            null -> error("You're not a member of this place.")
        }
        val myEmail = user.email?.lowercase()
        val myPhotoIds = current.photos.filter { it.uploaderId == user.id }.map { it.id }.toSet()
        val marker = current.markerStyle

        val updated = current.copy(
            members = current.members.filterNot { it.email == myEmail },
            photos = current.photos.filterNot { it.id in myPhotoIds },
            // If one of my photos was the marker, fall back to the collection's look.
            markerStyle = if (marker is MarkerStyle.Photo && marker.photoId in myPhotoIds) {
                MarkerStyle.CollectionDefault
            } else {
                marker
            },
            updatedAtEpochMillis = Clock.System.now().toEpochMilliseconds()
        )
        places.update { all -> all.map { if (it.id == placeId) updated else it } }
    }

    override suspend fun deletePlace(placeId: String) {
        val user = requireSignedInUser()
        val current = requirePlace(placeId)
        check(current.accessFor(user) == PlaceAccess.OWNER) { "Only the owner can delete this place." }
        places.update { all -> all.filterNot { it.id == placeId } }
    }

    override suspend fun deletePlacesInCollection(collectionId: String) {
        val user = requireSignedInUser()
        places.update { all -> all.filterNot { it.collectionId == collectionId && it.ownerId == user.id } }
    }

    private suspend fun requireSignedInUser(): User =
        authRepository.currentUser.first() ?: error("You need to be signed in to do that.")

    private fun requirePlace(placeId: String): Place =
        places.value.firstOrNull { it.id == placeId } ?: error("This place no longer exists.")

    /** A private photo is visible only to the person who uploaded it. */
    private fun Place.visibleTo(user: User): Place =
        copy(photos = photos.filter { !it.isPrivate || it.uploaderId == user.id })
}
