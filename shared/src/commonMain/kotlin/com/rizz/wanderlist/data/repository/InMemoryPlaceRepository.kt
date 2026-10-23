package com.rizz.wanderlist.data.repository

import com.rizz.wanderlist.domain.model.MemberRole
import com.rizz.wanderlist.domain.model.NewPlace
import com.rizz.wanderlist.domain.model.Place
import com.rizz.wanderlist.domain.model.PlaceAccess
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
        val owner = authRepository.currentUser.first()
            ?: error("You need to be signed in to save a place.")
        val now = Clock.System.now().toEpochMilliseconds()
        val place = Place(
            id = "place-$now-${places.value.size}",
            ownerId = owner.id,
            collectionId = newPlace.collectionId,
            actualName = newPlace.actualName,
            customName = newPlace.customName,
            address = newPlace.address,
            latitude = newPlace.latitude,
            longitude = newPlace.longitude,
            status = newPlace.status,
            visitedOnEpochMillis = newPlace.visitedOnEpochMillis,
            rating = newPlace.rating,
            notes = newPlace.notes,
            photos = newPlace.photos.map {
                PlacePhoto(id = it.uri, uploaderId = owner.id, url = it.uri, isPrivate = it.isPrivate)
            },
            markerStyle = newPlace.markerStyle,
            members = newPlace.members,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now
        )
        places.update { it + place }
        return place
    }

    /** A private photo is visible only to the person who uploaded it. */
    private fun Place.visibleTo(user: User): Place =
        copy(photos = photos.filter { !it.isPrivate || it.uploaderId == user.id })
}
