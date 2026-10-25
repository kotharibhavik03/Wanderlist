package com.rizz.wanderlist.data.firestore

import com.rizz.wanderlist.domain.model.MarkerStyle
import com.rizz.wanderlist.domain.model.MemberRole
import com.rizz.wanderlist.domain.model.NewPlace
import com.rizz.wanderlist.domain.model.NewPlacePhoto
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
import com.rizz.wanderlist.util.resultOf
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
        // The place is written with its first photos; the rest follow a few at a time. A photo
        // marker's photo goes in that first write, so the marker never shows a missing photo.
        val markerPhotoId = (newPlace.details.markerStyle as? MarkerStyle.Photo)?.photoId
        val ordered = newPlace.photos.sortedByDescending { it.id == markerPhotoId }
        val firstPhotos = ordered.take(PHOTOS_PER_WRITE)
        val laterPhotos = ordered.drop(PHOTOS_PER_WRITE)

        val created = withUploadedPhotos(reference.id, owner, firstPhotos, now, onPhotoUploaded) { photos ->
            // The place and its first photo documents in one batch: they appear together or not at all.
            // A photo marker must be one of these photos, and a shared one.
            val details = newPlace.details.withValidMarkerPhoto { photoId ->
                photos.any { (id, photo) -> id == photoId && !photo.isPrivate }
            }
            val document = details.toDocument(
                owner = PlaceOwner(id = owner.id, name = owner.displayName, email = owner.email?.lowercase()),
                createdAt = now,
                updatedAt = now
            ).copy(photoUploaders = photos.associate { (photoId, photo) -> photoId to photo.uploaderId })
            val batch = firestore.batch()
                .set(reference, PlaceDocument.serializer(), document) { encodeDefaults = true }
            photos.forEach { (photoId, photo) ->
                batch.set(reference.collection(PHOTOS).document(photoId), PhotoDocument.serializer(), photo) {
                    encodeDefaults = true
                }
            }
            batch.commit()

            document.toDomain(reference.id).copy(photos = photos.map { (id, photo) -> photo.toDomain(reference.id, id) })
        }
        if (laterPhotos.isEmpty()) return created

        try {
            val added = addPhotosInWrites(reference.id, owner, laterPhotos, now) { uploaded ->
                onPhotoUploaded(firstPhotos.size + uploaded)
            }
            return created.copy(photos = created.photos + added)
        } catch (e: Throwable) {
            // Saving a new place is all or nothing: take down the place and the photos saved so
            // far, so trying again doesn't make a second copy. Even when cancelled.
            withContext(NonCancellable) {
                runCatching {
                    val saved = places.document(reference.id).get()
                    if (saved.exists) deleteWithPhotos(reference.id, saved.data<PlaceDocument>(), owner)
                }
            }
            throw e
        }
    }

    override suspend fun updatePlace(placeId: String, details: PlaceDetails): Place {
        val user = requireSignedInUser()
        val reference = places.document(placeId)
        // People the owner removes take their photos with them. The photos go first: if the
        // save then fails, retrying it finishes the job, and no removed member's photos are
        // left behind out of the index's reach.
        val removedPhotos = photosOfRemovedMembers(placeId, details, user)
        deletePhotos(placeId, removedPhotos)
        // A transaction, so an edit can't silently overwrite a change made in the meantime.
        return firestore.runTransaction {
            val snapshot = get(reference)
            check(snapshot.exists) { "This place no longer exists." }
            val current = snapshot.data(PlaceDocument.serializer())
            val currentPlace = current.toDomain(placeId)

            val access = currentPlace.accessFor(user)
            val allowed = when (access) {
                PlaceAccess.OWNER -> details.also { requireNotAMember(user, it) }.withoutMarkerPhoto(removedPhotos.keys)
                // A collaborator can't move the place or change its people; keep the current ones.
                PlaceAccess.COLLABORATOR -> details.copy(
                    collectionId = currentPlace.collectionId,
                    members = currentPlace.members
                )
                PlaceAccess.VISITED_WITH, null -> error("You can't edit this place.")
            }
            // A photo marker must be one of the place's shared photos.
            val marker = allowed.markerStyle
            if (marker is MarkerStyle.Photo) {
                val photo = get(reference.collection(PHOTOS).document(marker.photoId))
                require(photo.exists && !photo.data(PhotoDocument.serializer()).isPrivate) {
                    "The marker photo must be one of the shared photos."
                }
            }

            val updated = allowed.toDocument(owner = current.owner, createdAt = current.createdAt, updatedAt = now())
                .copy(photoUploaders = current.photoUploaders - removedPhotos.keys)
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
        val myPhotos = firestore.runTransaction {
            val snapshot = get(reference)
            check(snapshot.exists) { "This place no longer exists." }
            val current = snapshot.data(PlaceDocument.serializer())
            when (current.toDomain(placeId).accessFor(user)) {
                PlaceAccess.COLLABORATOR, PlaceAccess.VISITED_WITH -> Unit
                PlaceAccess.OWNER -> error("You own this place, so you can't leave it.")
                null -> error("You're not a member of this place.")
            }
            // My photos leave with me, in the same transaction: the place, its index and the
            // photo documents change together or not at all.
            val myPhotos = current.photoUploaders.filterValues { it == user.id }
            val updated = current.withoutMember(myEmail, now()).withoutPhotos(myPhotos.keys)
            // Only the membership fields and the photo index: the rules let a leaving member
            // change nothing else. The marker only when it showed one of my photos; rewriting an
            // unchanged one could still differ from what's stored (an older place may lack a key).
            val fields = if (updated.marker == current.marker) MEMBERSHIP_FIELDS else MEMBERSHIP_FIELDS + "marker"
            set(reference, PlaceDocument.serializer(), updated, *fields) { encodeDefaults = true }
            myPhotos.keys.forEach { photoId -> delete(reference.collection(PHOTOS).document(photoId)) }
            myPhotos
        }
        // The files are in my own Storage folder, so I can delete them after leaving.
        deletePhotoFiles(placeId, myPhotos)
    }

    override suspend fun deletePlace(placeId: String) {
        val user = requireSignedInUser()
        val snapshot = places.document(placeId).get()
        check(snapshot.exists) { "This place no longer exists." }
        val document = snapshot.data<PlaceDocument>()
        check(document.ownerId == user.id) { "Only the owner can delete this place." }
        deleteWithPhotos(placeId, document, user)
    }

    override suspend fun addPhotos(placeId: String, photos: List<NewPlacePhoto>, onPhotoUploaded: (uploaded: Int) -> Unit) {
        if (photos.isEmpty()) return
        val user = requireSignedInUser()
        val reference = places.document(placeId)
        // Check before uploading anything, so someone who was removed doesn't upload in vain.
        val place = requireOnPlace(reference.get().takeIf { it.exists }?.data<PlaceDocument>(), placeId, user)
        // Photos an earlier try of the same save already added (their files can't be overwritten).
        val toAdd = photos.filterNot { it.id in place.photoUploaders }
        if (toAdd.isEmpty()) return
        addPhotosInWrites(placeId, user, toAdd, now(), onPhotoUploaded)
    }

    override suspend fun setPhotoPrivate(placeId: String, photoId: String, isPrivate: Boolean) {
        val user = requireSignedInUser()
        val reference = places.document(placeId)
        val photoReference = reference.collection(PHOTOS).document(photoId)
        // A transaction, so the marker check and the change see the same place.
        firestore.runTransaction {
            val current = requireOnPlace(get(reference).takeIf { it.exists }?.data(PlaceDocument.serializer()), placeId, user)
            val snapshot = get(photoReference)
            check(snapshot.exists) { "This photo was deleted." }
            val photo = snapshot.data(PhotoDocument.serializer())
            check(photo.uploaderId == user.id) { "Only the person who added a photo can change who sees it." }
            if (photo.isPrivate == isPrivate) return@runTransaction
            // Markers are visible to everyone on the place, so the marker's photo stays shared.
            val isMarker = current.marker.type == MarkerDocument.TYPE_PHOTO && current.marker.photoId == photoId
            check(!(isPrivate && isMarker)) { "This photo is the place's marker, so it has to stay shared." }
            // Only that field: the rules let the uploader change nothing else.
            set(photoReference, PhotoDocument.serializer(), photo.copy(isPrivate = isPrivate), "isPrivate") {
                encodeDefaults = true
            }
        }
    }

    override suspend fun deletePhoto(placeId: String, photoId: String) {
        val user = requireSignedInUser()
        val reference = places.document(placeId)
        val photoReference = reference.collection(PHOTOS).document(photoId)
        // The document and the index entry go together; the file after, once nobody can find it.
        val uploaderId = firestore.runTransaction {
            val snapshot = get(reference)
            val current = requireOnPlace(snapshot.takeIf { it.exists }?.data(PlaceDocument.serializer()), placeId, user)
            val photoSnapshot = get(photoReference)
            if (!photoSnapshot.exists) return@runTransaction null // already deleted
            val photo = photoSnapshot.data(PhotoDocument.serializer())
            check(photo.uploaderId == user.id || current.ownerId == user.id) { "You can only delete photos you added." }

            val updated = current.withoutPhotos(setOf(photoId)).copy(updatedAt = now())
            val fields = if (updated.marker == current.marker) PHOTO_INDEX_FIELDS else PHOTO_INDEX_FIELDS + "marker"
            set(reference, PlaceDocument.serializer(), updated, *fields) { encodeDefaults = true }
            delete(photoReference)
            photo.uploaderId
        }
        uploaderId?.let { deletePhotoFiles(placeId, mapOf(photoId to it)) }
    }

    override suspend fun deletePlacesInCollection(collectionId: String) {
        val user = requireSignedInUser()
        val snapshot = places
            .where { ("ownerId" equalTo user.id) and ("collectionId" equalTo collectionId) }
            .get()
        // One place at a time, each with its photos. If one fails, the ones before it are gone
        // and deleting the collection again picks up the rest.
        snapshot.documents.forEach { document ->
            deleteWithPhotos(document.id, document.data<PlaceDocument>(), user)
        }
    }

    /**
     * Adds [photos] as [user]'s to a place that exists, [PHOTOS_PER_WRITE] at a time: each group
     * is uploaded, then saved in one transaction with its index entries, so every group is on the
     * place completely or not at all. If one fails, the groups before it stay. [onPhotoUploaded]
     * counts across all groups. Returns the added photos.
     */
    private suspend fun addPhotosInWrites(
        placeId: String,
        user: User,
        photos: List<NewPlacePhoto>,
        createdAt: Long,
        onPhotoUploaded: (uploaded: Int) -> Unit
    ): List<PlacePhoto> {
        val reference = places.document(placeId)
        var uploadedBefore = 0
        return photos.chunked(PHOTOS_PER_WRITE).flatMap { group ->
            val offset = uploadedBefore
            val added = withUploadedPhotos(placeId, user, group, createdAt, { onPhotoUploaded(offset + it) }) { uploaded ->
                // A transaction: the index is read and extended in one step, so photos two people
                // add at the same moment don't overwrite each other's entries.
                firestore.runTransaction {
                    val snapshot = get(reference)
                    val current = requireOnPlace(snapshot.takeIf { it.exists }?.data(PlaceDocument.serializer()), placeId, user)
                    val updated = current.copy(
                        photoUploaders = current.photoUploaders + uploaded.associate { (photoId, _) -> photoId to user.id },
                        updatedAt = now()
                    )
                    // Only the index: the rules let anyone on the place add their own photos to it.
                    set(reference, PlaceDocument.serializer(), updated, *PHOTO_INDEX_FIELDS) { encodeDefaults = true }
                    uploaded.forEach { (photoId, photo) ->
                        set(reference.collection(PHOTOS).document(photoId), PhotoDocument.serializer(), photo) {
                            encodeDefaults = true
                        }
                    }
                }
                uploaded.map { (photoId, photo) -> photo.toDomain(placeId, photoId) }
            }
            uploadedBefore += group.size
            added
        }
    }

    /**
     * Uploads [photos] as [uploader]'s, one at a time so progress is simple and memory stays low,
     * then runs [save] with their documents (photo id to document). Each photo is two files, the
     * photo and its thumbnail. If anything fails, or the work is cancelled, the uploaded files are
     * deleted again: nothing was saved, so they'd be orphans. A file that can't be deleted right
     * then is left behind.
     */
    private suspend fun <T> withUploadedPhotos(
        placeId: String,
        uploader: User,
        photos: List<NewPlacePhoto>,
        createdAt: Long,
        onPhotoUploaded: (uploaded: Int) -> Unit,
        save: suspend (List<Pair<String, PhotoDocument>>) -> T
    ): T {
        val uploadedPaths = mutableListOf<String>()
        try {
            val documents = photos.mapIndexed { index, photo ->
                val image = photoImage(placeId, uploader.id, photo.id)
                photoStorage.upload(image.path, photo.file.path)
                uploadedPaths += image.path
                photoStorage.upload(image.thumbnailPath, photo.file.thumbnailPath)
                uploadedPaths += image.thumbnailPath
                onPhotoUploaded(index + 1)
                photo.id to PhotoDocument(uploaderId = uploader.id, isPrivate = photo.isPrivate, createdAt = createdAt)
            }
            return save(documents)
        } catch (e: Throwable) {
            withContext(NonCancellable) {
                uploadedPaths.forEach { path -> runCatching { photoStorage.delete(path) } }
            }
            throw e
        }
    }

    /** [place] if it exists and [user] is its owner or one of its members; otherwise a clear error. */
    private fun requireOnPlace(place: PlaceDocument?, placeId: String, user: User): PlaceDocument {
        checkNotNull(place) { "This place no longer exists." }
        checkNotNull(place.toDomain(placeId).accessFor(user)) { "This place is no longer shared with you." }
        return place
    }

    /**
     * Deletes a place the [owner] owns, with every photo on it: photo documents, then files,
     * then the place. The place goes last because the rules look up its owner while the photos
     * are deleted (the photos' delete rule, and storage.rules for other people's files).
     */
    private suspend fun deleteWithPhotos(placeId: String, document: PlaceDocument, owner: User) {
        deletePhotos(placeId, document.photoUploaders + unindexedPhotos(placeId, owner))
        places.document(placeId).delete()
    }

    /**
     * The owner's photos on a place saved before the photo index existed. Back then only the
     * owner could add photos, so their own photos are all there are; they can always read them.
     */
    private suspend fun unindexedPhotos(placeId: String, owner: User): Map<String, String> =
        places.document(placeId).collection(PHOTOS)
            .where { "uploaderId" equalTo owner.id }
            .get()
            .documents
            .associate { it.id to owner.id }

    /**
     * Photos (id to uploader) of the members the owner's edit removes. Empty unless [user] owns
     * the place. Uploaders are found through the removed members' profiles; someone who never
     * signed in has no profile, and no photos either.
     */
    private suspend fun photosOfRemovedMembers(placeId: String, details: PlaceDetails, user: User): Map<String, String> {
        val snapshot = places.document(placeId).get()
        if (!snapshot.exists) return emptyMap()
        val current = snapshot.data<PlaceDocument>()
        if (current.ownerId != user.id) return emptyMap()
        val kept = details.members.map { it.email.lowercase() }.toSet()
        val removedUids = current.memberEmails
            .filterNot { it in kept }
            .mapNotNull { email -> firestore.uidForEmail(email) }
            .toSet()
        if (removedUids.isEmpty()) return emptyMap()
        return current.photoUploaders.filterValues { it in removedUids }
    }

    /**
     * Deletes photos (id to uploader) from a place that still exists: their documents first, so
     * nobody sees them any more, then their files. Batches stay small because the delete rule may
     * look up the place once per photo, and a batch may make at most 20 lookups.
     */
    private suspend fun deletePhotos(placeId: String, photos: Map<String, String>) {
        if (photos.isEmpty()) return
        val photoDocuments = places.document(placeId).collection(PHOTOS)
        photos.keys.chunked(MAX_RULE_LOOKUPS_PER_BATCH).forEach { ids ->
            ids.fold(firestore.batch()) { batch, id -> batch.delete(photoDocuments.document(id)) }.commit()
        }
        deletePhotoFiles(placeId, photos)
    }

    /**
     * Deletes photo files (id to uploader): each photo and its thumbnail. Best effort: a file that
     * is already gone, or can't be deleted right now, is left behind; its document is already
     * deleted, so nobody can find it.
     */
    private suspend fun deletePhotoFiles(placeId: String, photos: Map<String, String>) {
        photos.forEach { (photoId, uploaderId) ->
            val image = photoImage(placeId, uploaderId, photoId)
            resultOf { photoStorage.delete(image.path) }
            resultOf { photoStorage.delete(image.thumbnailPath) }
        }
    }

    /**
     * The photos of one place that [user] may read, oldest first. Two queries, because the
     * rules only allow reads they can prove: shared photos, and the user's own (private too).
     * An error (e.g. the place was deleted or the user removed) emits an empty list.
     */
    private fun photosOf(placeId: String, user: User): Flow<List<PlacePhoto>> {
        val photos = places.document(placeId).collection(PHOTOS)
        val shared = photos.where { "isPrivate" equalTo false }.snapshots.map { it.toPhotos(placeId) }
        val mine = photos.where { "uploaderId" equalTo user.id }.snapshots.map { it.toPhotos(placeId) }
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

    private fun QuerySnapshot.toPhotos(placeId: String): List<PlacePhoto> =
        documents.map { it.data<PhotoDocument>().toDomain(placeId, it.id) }

    /** [this], or with the collection default instead of a photo marker whose photo [isValid] rejects. */
    private fun PlaceDetails.withValidMarkerPhoto(isValid: (photoId: String) -> Boolean): PlaceDetails {
        val marker = markerStyle as? MarkerStyle.Photo ?: return this
        return if (isValid(marker.photoId)) this else copy(markerStyle = MarkerStyle.CollectionDefault)
    }

    /** A photo marker showing one of [photoIds] (photos being deleted) becomes the collection default. */
    private fun PlaceDetails.withoutMarkerPhoto(photoIds: Set<String>): PlaceDetails {
        val marker = markerStyle as? MarkerStyle.Photo ?: return this
        return if (marker.photoId in photoIds) copy(markerStyle = MarkerStyle.CollectionDefault) else this
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
        /** firestore.rules allows a batch at most 20 document lookups; deleting someone else's photo costs one. */
        const val MAX_RULE_LOOKUPS_PER_BATCH = 20
        /**
         * Photos saved per batch or transaction. Creating a photo costs one rule lookup (the place,
         * via getAfter) and the place itself may cost one more, so this stays well inside the 20.
         */
        const val PHOTOS_PER_WRITE = 10
    }
}
