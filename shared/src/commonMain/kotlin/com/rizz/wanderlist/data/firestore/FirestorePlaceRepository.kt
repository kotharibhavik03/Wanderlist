package com.rizz.wanderlist.data.firestore

import com.rizz.wanderlist.domain.model.MarkerStyle
import com.rizz.wanderlist.domain.model.MemberRole
import com.rizz.wanderlist.domain.model.NewPlace
import com.rizz.wanderlist.domain.model.Place
import com.rizz.wanderlist.domain.model.PlaceAccess
import com.rizz.wanderlist.domain.model.PlaceDetails
import com.rizz.wanderlist.domain.model.SharedPlace
import com.rizz.wanderlist.domain.model.User
import com.rizz.wanderlist.domain.model.accessFor
import com.rizz.wanderlist.domain.repository.AuthRepository
import com.rizz.wanderlist.domain.repository.PlaceRepository
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.QuerySnapshot
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Places in Firestore at `places/{id}`. Every query is scoped to the signed-in user: places
 * they own (`ownerId`) or were added to (`memberEmails`). firestore.rules enforces the same
 * access; the checks here only turn a rejected write into a clear message.
 */
class FirestorePlaceRepository(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository
) : PlaceRepository {

    private val places get() = firestore.collection(PLACES)

    override fun observePlaces(collectionId: String): Flow<List<Place>> = perUser(emptyList()) { user ->
        places.where { ("ownerId" equalTo user.id) and ("collectionId" equalTo collectionId) }
            .snapshots
            .map { snapshot -> snapshot.toPlaces().sortedByDescending { it.createdAtEpochMillis } }
    }

    override fun observePlace(placeId: String): Flow<Place?> = perUser(null) { user ->
        places.document(placeId).snapshots.map { snapshot ->
            if (!snapshot.exists) return@map null
            snapshot.data<PlaceDocument>().toDomain(snapshot.id).takeIf { it.accessFor(user) != null }
        }
    }

    override fun observeVisiblePlaces(): Flow<List<Place>> = perUser(emptyList()) { user ->
        combine(ownedPlaces(user), placesSharedWith(user)) { owned, shared ->
            // A place can be in both if the owner added their own email; keep one copy.
            (owned + shared).distinctBy { it.id }.sortedByDescending { it.createdAtEpochMillis }
        }
    }

    override fun observePlaceCounts(): Flow<Map<String, Int>> = perUser(emptyMap()) { user ->
        ownedPlaces(user).map { owned -> owned.groupingBy { it.collectionId }.eachCount() }
    }

    override fun observeSharedWithMe(): Flow<List<SharedPlace>> = perUser(emptyList()) { user ->
        placesSharedWith(user).map { shared ->
            shared.mapNotNull { place ->
                when (place.accessFor(user)) {
                    PlaceAccess.COLLABORATOR -> SharedPlace(place, MemberRole.COLLABORATOR)
                    PlaceAccess.VISITED_WITH -> SharedPlace(place, MemberRole.VISITED_WITH)
                    PlaceAccess.OWNER, null -> null
                }
            }.sortedByDescending { it.place.createdAtEpochMillis }
        }
    }

    override suspend fun createPlace(newPlace: NewPlace): Place {
        val owner = requireSignedInUser()
        val now = now()
        val reference = places.document // new auto-id, known before the write
        // Photos are stored in their own subcollection, added with the photo step.
        val document = newPlace.details.toDocument(
            owner = PlaceOwner(id = owner.id, name = owner.displayName, email = owner.email?.lowercase()),
            createdAt = now,
            updatedAt = now
        )
        reference.set(document) { encodeDefaults = true }
        return document.toDomain(reference.id)
    }

    override suspend fun updatePlace(placeId: String, details: PlaceDetails): Place {
        val user = requireSignedInUser()
        val reference = places.document(placeId)
        // A transaction, so an edit can't silently overwrite a change made in the meantime.
        return firestore.runTransaction {
            val snapshot = get(reference)
            check(snapshot.exists) { "This place no longer exists." }
            val current = snapshot.data(PlaceDocument.serializer())
            val currentPlace = current.toDomain(placeId)

            val access = currentPlace.accessFor(user)
            val allowed = when (access) {
                PlaceAccess.OWNER -> details
                // A collaborator can't move the place or change its people; keep the current ones.
                PlaceAccess.COLLABORATOR -> details.copy(
                    collectionId = currentPlace.collectionId,
                    members = currentPlace.members
                )
                PlaceAccess.VISITED_WITH, null -> error("You can't edit this place.")
            }
            val marker = allowed.markerStyle
            require(marker !is MarkerStyle.Photo || currentPlace.photos.any { it.id == marker.photoId && !it.isPrivate }) {
                "The marker photo must be one of the shared photos."
            }

            val updated = allowed.toDocument(owner = current.owner, createdAt = current.createdAt, updatedAt = now())
            if (access == PlaceAccess.OWNER) {
                set(reference, PlaceDocument.serializer(), updated) { encodeDefaults = true }
            } else {
                // Only the fields a collaborator may change. Rewriting the whole document would
                // also touch fields the rules don't let them change (e.g. ones a newer app
                // version added), and the write would be rejected.
                set(reference, PlaceDocument.serializer(), updated, *COLLABORATOR_EDIT_FIELDS) { encodeDefaults = true }
            }
            updated.toDomain(placeId)
        }
    }

    override suspend fun leavePlace(placeId: String) {
        val user = requireSignedInUser()
        val myEmail = user.email?.lowercase() ?: error("Your account has no email address.")
        val reference = places.document(placeId)
        firestore.runTransaction {
            val snapshot = get(reference)
            check(snapshot.exists) { "This place no longer exists." }
            val current = snapshot.data(PlaceDocument.serializer())
            when (current.toDomain(placeId).accessFor(user)) {
                PlaceAccess.COLLABORATOR, PlaceAccess.VISITED_WITH -> Unit
                PlaceAccess.OWNER -> error("You own this place, so you can't leave it.")
                null -> error("You're not a member of this place.")
            }
            // Only the membership fields: the rules let a leaving member change nothing else.
            // Removing the member's photos joins this once photos have their subcollection.
            set(reference, PlaceDocument.serializer(), current.withoutMember(myEmail, now()), *MEMBERSHIP_FIELDS) {
                encodeDefaults = true
            }
        }
    }

    override suspend fun deletePlace(placeId: String) {
        val user = requireSignedInUser()
        val reference = places.document(placeId)
        val snapshot = reference.get()
        check(snapshot.exists) { "This place no longer exists." }
        check(snapshot.data<PlaceDocument>().ownerId == user.id) { "Only the owner can delete this place." }
        reference.delete()
    }

    override suspend fun deletePlacesInCollection(collectionId: String) {
        val user = requireSignedInUser()
        val snapshot = places
            .where { ("ownerId" equalTo user.id) and ("collectionId" equalTo collectionId) }
            .get()
        // A batch commits all of its deletes or none; Firestore allows 500 writes per batch.
        snapshot.documents.chunked(MAX_BATCH_WRITES).forEach { chunk ->
            chunk.fold(firestore.batch()) { batch, document -> batch.delete(document.reference) }.commit()
        }
    }

    private fun ownedPlaces(user: User): Flow<List<Place>> =
        places.where { "ownerId" equalTo user.id }.snapshots.map { it.toPlaces() }

    private fun placesSharedWith(user: User): Flow<List<Place>> {
        // Member emails are stored lowercase, see PlaceDetails.toDocument.
        val myEmail = user.email?.lowercase() ?: return flowOf(emptyList())
        return places.where { "memberEmails" contains myEmail }.snapshots.map { it.toPlaces() }
    }

    /**
     * Runs [query] for the signed-in user and restarts it when the account changes, so one
     * account's places never reach another. A listener error (e.g. permission denied during
     * sign-out) emits [empty] instead of crashing the screen collecting it.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> perUser(empty: T, query: (User) -> Flow<T>): Flow<T> =
        authRepository.currentUser
            .distinctUntilChanged()
            .flatMapLatest { user ->
                if (user == null) flowOf(empty) else query(user).catch { emit(empty) }
            }

    private fun QuerySnapshot.toPlaces(): List<Place> =
        documents.map { it.data<PlaceDocument>().toDomain(it.id) }

    private suspend fun requireSignedInUser(): User =
        authRepository.currentUser.first() ?: error("You need to be signed in to do that.")

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    private companion object {
        const val PLACES = "places"
        const val MAX_BATCH_WRITES = 500
    }
}
