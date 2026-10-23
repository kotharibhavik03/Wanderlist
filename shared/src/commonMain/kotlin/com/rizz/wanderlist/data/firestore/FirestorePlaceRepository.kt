package com.rizz.wanderlist.data.firestore

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
import com.rizz.wanderlist.domain.repository.PhotoStorage
import com.rizz.wanderlist.domain.repository.PlaceRepository
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.QuerySnapshot
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Places in Firestore at `places/{id}`. Every query is scoped to the signed-in user: places
 * they own (`ownerId`) or were added to (`memberEmails`). firestore.rules enforces the same
 * access; the checks here only turn a rejected write into a clear message.
 */
class FirestorePlaceRepository(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val photoStorage: PhotoStorage
) : PlaceRepository {

    private val places get() = firestore.collection(PLACES)

    override fun observePlaces(collectionId: String): Flow<List<Place>> = perUser(emptyList()) { user ->
        places.where { ("ownerId" equalTo user.id) and ("collectionId" equalTo collectionId) }
            .snapshots
            .map { snapshot -> snapshot.toPlaces().sortedByDescending { it.createdAtEpochMillis } }
            .withPhotos(user)
    }

    override fun observePlace(placeId: String): Flow<Place?> = perUser(null) { user ->
        val place = places.document(placeId).snapshots.map { snapshot ->
            if (!snapshot.exists) return@map null
            snapshot.data<PlaceDocument>().toDomain(snapshot.id).takeIf { it.accessFor(user) != null }
        }
        combine(place, photosOf(placeId, user)) { found, photos -> found?.copy(photos = photos) }
    }

    override fun observePhotos(placeId: String): Flow<List<PlacePhoto>> = perUser(emptyList()) { user ->
        photosOf(placeId, user)
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

    override suspend fun createPlace(newPlace: NewPlace, onPhotoUploaded: (uploaded: Int) -> Unit): Place {
        val owner = requireSignedInUser()
        requireNotAMember(owner, newPlace.details)
        val now = now()
        val reference = places.document // new auto-id, known before anything is written

        val uploadedPaths = mutableListOf<String>()
        try {
            // 1. The files first, one at a time so progress is simple and memory stays low.
            val photos = newPlace.photos.mapIndexed { index, photo ->
                val path = photoStoragePath(reference.id, owner.id, photo.id)
                val url = photoStorage.upload(path, photo.localPath)
                uploadedPaths += path
                onPhotoUploaded(index + 1)
                photo.id to PhotoDocument(uploaderId = owner.id, url = url, isPrivate = photo.isPrivate, createdAt = now)
            }

            // 2. The place and its photo documents in one batch: they appear together or not at all.
            //    A photo marker stores its photo's URL, so the map can draw it without the photos.
            val details = newPlace.details.withMarkerPhotoUrl { photoId ->
                photos.firstOrNull { (id, photo) -> id == photoId && !photo.isPrivate }?.second?.url
            }
            val document = details.toDocument(
                owner = PlaceOwner(id = owner.id, name = owner.displayName, email = owner.email?.lowercase()),
                createdAt = now,
                updatedAt = now
            )
            val batch = firestore.batch()
                .set(reference, PlaceDocument.serializer(), document) { encodeDefaults = true }
            photos.forEach { (photoId, photo) ->
                batch.set(reference.collection(PHOTOS).document(photoId), PhotoDocument.serializer(), photo) {
                    encodeDefaults = true
                }
            }
            batch.commit()

            return document.toDomain(reference.id).copy(photos = photos.map { (id, photo) -> photo.toDomain(id) })
        } catch (e: Throwable) {
            // Nothing was saved, so the uploaded files would be orphans. Clean up even when
            // the save was cancelled; a file that can't be deleted now is left behind.
            withContext(NonCancellable) {
                uploadedPaths.forEach { path -> runCatching { photoStorage.delete(path) } }
            }
            throw e
        }
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
                PlaceAccess.OWNER -> details.also { requireNotAMember(user, it) }
                // A collaborator can't move the place or change its people; keep the current ones.
                PlaceAccess.COLLABORATOR -> details.copy(
                    collectionId = currentPlace.collectionId,
                    members = currentPlace.members
                )
                PlaceAccess.VISITED_WITH, null -> error("You can't edit this place.")
            }
            // A photo marker must be one of the place's shared photos; its URL comes from the
            // photo's own document, never from the client.
            val marker = allowed.markerStyle
            val checked = if (marker is MarkerStyle.Photo) {
                val photo = get(reference.collection(PHOTOS).document(marker.photoId))
                require(photo.exists && !photo.data(PhotoDocument.serializer()).isPrivate) {
                    "The marker photo must be one of the shared photos."
                }
                allowed.copy(markerStyle = marker.copy(url = photo.data(PhotoDocument.serializer()).url))
            } else {
                allowed
            }

            val updated = checked.toDocument(owner = current.owner, createdAt = current.createdAt, updatedAt = now())
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

    /**
     * The photos of one place that [user] may read, oldest first. Two queries, because the
     * rules only allow reads they can prove: shared photos, and the user's own (private too).
     * An error (e.g. the place was deleted or the user removed) emits an empty list.
     */
    private fun photosOf(placeId: String, user: User): Flow<List<PlacePhoto>> {
        val photos = places.document(placeId).collection(PHOTOS)
        val shared = photos.where { "isPrivate" equalTo false }.snapshots.map { it.toPhotos() }
        val mine = photos.where { "uploaderId" equalTo user.id }.snapshots.map { it.toPhotos() }
        return combine(shared, mine) { sharedPhotos, myPhotos ->
            (sharedPhotos + myPhotos).distinctBy { it.id }.sortedBy { it.createdAtEpochMillis }
        }.catch { emit(emptyList()) }
    }

    /**
     * Adds each place's photos. The photo listeners restart only when the set of places
     * changes, not on every edit to a place. (The place list is collected twice; Firestore
     * shares one listener between identical queries.)
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun Flow<List<Place>>.withPhotos(user: User): Flow<List<Place>> {
        val photosByPlace = map { list -> list.map { it.id } }
            .distinctUntilChanged()
            .flatMapLatest { ids ->
                if (ids.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    combine(ids.map { id -> photosOf(id, user).map { id to it } }) { it.toMap() }
                }
            }
        return combine(this, photosByPlace) { list, photos ->
            list.map { it.copy(photos = photos[it.id].orEmpty()) }
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

    private fun QuerySnapshot.toPhotos(): List<PlacePhoto> =
        documents.map { it.data<PhotoDocument>().toDomain(it.id) }

    /** [this] with a photo marker's URL filled in by [urlOf], or the collection default if it has none. */
    private fun PlaceDetails.withMarkerPhotoUrl(urlOf: (photoId: String) -> String?): PlaceDetails {
        val marker = markerStyle as? MarkerStyle.Photo ?: return this
        val url = urlOf(marker.photoId)
        return copy(markerStyle = if (url != null) marker.copy(url = url) else MarkerStyle.CollectionDefault)
    }

    /** The owner already has the place; firestore.rules refuses them as a member too. */
    private fun requireNotAMember(owner: User, details: PlaceDetails) {
        val email = owner.email?.lowercase() ?: return
        require(details.members.none { it.email.lowercase() == email }) {
            "You can't add yourself to your own place."
        }
    }

    private suspend fun requireSignedInUser(): User =
        authRepository.currentUser.first() ?: error("You need to be signed in to do that.")

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    private companion object {
        const val PLACES = "places"
        const val PHOTOS = "photos"
        const val MAX_BATCH_WRITES = 500
    }
}
