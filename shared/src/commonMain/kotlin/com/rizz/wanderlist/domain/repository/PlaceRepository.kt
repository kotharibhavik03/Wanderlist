package com.rizz.wanderlist.domain.repository

import com.rizz.wanderlist.domain.model.NewPlace
import com.rizz.wanderlist.domain.model.NewPlacePhoto
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
     * Saves a place owned by the signed-in user, with any number of photos. All or nothing: if
     * any photo fails to upload or save, nothing is kept and the files already uploaded are removed.
     * [onPhotoUploaded] reports how many photos are up so far. Throws if nobody is signed in.
     */
    suspend fun createPlace(newPlace: NewPlace, onPhotoUploaded: (uploaded: Int) -> Unit = {}): Place

    /**
     * Applies [details] to a place. The owner may change everything; a collaborator may not
     * move it to another collection or change its people. People the owner removes lose the
     * photos they added. Throws when not allowed.
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

    /**
     * Adds any number of the signed-in user's photos to a place they own or are on. They're
     * saved a few at a time; if one fails, the photos saved before it stay on the place and the
     * rest are not added. Photos already on the place are skipped, so a failed save can be retried.
     * Throws when not allowed.
     */
    suspend fun addPhotos(placeId: String, photos: List<NewPlacePhoto>, onPhotoUploaded: (uploaded: Int) -> Unit = {})

    /**
     * Makes one of the signed-in user's own photos private (only they see it) or shared. A photo
     * that is the place's marker can't be made private. Throws when not allowed.
     */
    suspend fun setPhotoPrivate(placeId: String, photoId: String, isPrivate: Boolean)

    /**
     * Deletes one photo, for everyone. Its uploader or the place's owner may do this; a marker
     * showing it falls back to the collection's. Throws when not allowed.
     */
    suspend fun deletePhoto(placeId: String, photoId: String)

    /** Deletes every place the signed-in user owns in a collection, with their photos. */
    suspend fun deletePlacesInCollection(collectionId: String)
}
