package com.rizz.wanderlist.domain.repository

import com.rizz.wanderlist.domain.model.NewPlace
import com.rizz.wanderlist.domain.model.Place
import com.rizz.wanderlist.domain.model.PlaceDetails
import com.rizz.wanderlist.domain.model.PlacePhoto
import com.rizz.wanderlist.domain.model.SharedPlace
import kotlinx.coroutines.flow.Flow

interface PlaceRepository {
    /**
     * Places of one collection the signed-in user may see, newest first, with their photos
     * (see [observePhotos] for which).
     */
    fun observePlaces(collectionId: String): Flow<List<Place>>

    /**
     * One place with its photos, or null when it doesn't exist or the signed-in user may not
     * see it (not the owner and not a member).
     */
    fun observePlace(placeId: String): Flow<Place?>

    /**
     * Every place the signed-in user can see, own or shared with them, newest first. This is
     * what the map shows. Photos are not loaded ([Place.photos] is empty); a photo marker
     * carries its own URL. Use [observePhotos] for one place's photos.
     */
    fun observeVisiblePlaces(): Flow<List<Place>>

    /**
     * The photos of a place the signed-in user may see, oldest first: its shared photos plus
     * their own private ones. Other people's private photos never leave the server.
     */
    fun observePhotos(placeId: String): Flow<List<PlacePhoto>>

    /** How many places each collection holds, keyed by collection id. Missing key means none. */
    fun observePlaceCounts(): Flow<Map<String, Int>>

    /**
     * Places other people own where the signed-in user is a member, newest first.
     * Empty when signed out. Photos are not loaded, as in [observeVisiblePlaces].
     */
    fun observeSharedWithMe(): Flow<List<SharedPlace>>

    /**
     * Saves a place owned by the signed-in user, with its photos. All or nothing: if any
     * photo fails to upload, nothing is saved and the files already uploaded are removed.
     * [onPhotoUploaded] reports how many photos are up so far. Throws if nobody is signed in.
     */
    suspend fun createPlace(newPlace: NewPlace, onPhotoUploaded: (uploaded: Int) -> Unit = {}): Place

    /**
     * Applies [details] to a place. The owner may change everything; a collaborator may not
     * move it to another collection or change its people. Throws when not allowed.
     */
    suspend fun updatePlace(placeId: String, details: PlaceDetails): Place

    /**
     * Removes the signed-in member from a place, along with the photos they added.
     * The owner can't leave their own place. Throws when not allowed.
     */
    suspend fun leavePlace(placeId: String)

    /**
     * Deletes a place for everyone, with all its photos. Only the owner may do this.
     * Throws when not allowed.
     */
    suspend fun deletePlace(placeId: String)

    /** Deletes every place the signed-in user owns in a collection, with their photos. */
    suspend fun deletePlacesInCollection(collectionId: String)
}
