package com.rizz.wanderlist.data.repository

import com.rizz.wanderlist.domain.model.NewPlace
import com.rizz.wanderlist.domain.model.Place
import com.rizz.wanderlist.domain.model.PlacePhoto
import com.rizz.wanderlist.domain.model.SharedPlace
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
        places.map { all ->
            all.filter { it.collectionId == collectionId }
                .sortedByDescending { it.createdAtEpochMillis }
        }

    override fun observePlaceCounts(): Flow<Map<String, Int>> =
        places.map { all -> all.groupingBy { it.collectionId }.eachCount() }

    override fun observeSharedWithMe(): Flow<List<SharedPlace>> =
        combine(places, authRepository.currentUser) { all, user ->
            val myEmail = user?.email?.lowercase() ?: return@combine emptyList()
            all.filter { it.ownerId != user.id }
                .mapNotNull { place ->
                    // Member emails are stored lowercase, see AddPlaceViewModel.onMemberAdded.
                    place.members.firstOrNull { it.email == myEmail }
                        ?.let { SharedPlace(place, it.role) }
                }
                .sortedByDescending { it.place.createdAtEpochMillis }
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
}
