package com.rizz.wanderlist.domain.usecase

import com.rizz.wanderlist.domain.model.MAX_PHOTOS_PER_SAVE
import com.rizz.wanderlist.domain.model.PhotoChanges
import com.rizz.wanderlist.domain.model.Place
import com.rizz.wanderlist.domain.model.PlaceDetails
import com.rizz.wanderlist.domain.repository.PlaceRepository
import com.rizz.wanderlist.util.resultOf

/**
 * Saves edits to an existing place: its details and its photos. Who may change what, and
 * whether a photo marker is still valid, is checked by the repository, which holds the
 * current place.
 *
 * The steps run in an order where each one is valid on its own, and every step can be
 * repeated: if one fails, saving again finishes the job.
 */
class UpdatePlaceUseCase(private val repository: PlaceRepository) {

    suspend operator fun invoke(
        placeId: String,
        details: PlaceDetails,
        photos: PhotoChanges = PhotoChanges(),
        onPhotoUploaded: (uploaded: Int) -> Unit = {}
    ): Result<Place> {
        validatePlaceDetails(details)?.let { return Result.failure(IllegalArgumentException(it)) }
        if (photos.added.size > MAX_PHOTOS_PER_SAVE) {
            return Result.failure(IllegalArgumentException("You can add up to $MAX_PHOTOS_PER_SAVE photos at a time."))
        }
        return resultOf {
            // 1. New photos first: the marker may show one of them, and it must exist by then.
            repository.addPhotos(placeId, photos.added, onPhotoUploaded)
            // 2. The details and the marker. Before privacy changes: a photo can only become
            //    private once it's no longer the marker.
            val place = repository.updatePlace(placeId, details)
            // 3. Who sees the user's own photos.
            photos.privacy.forEach { (photoId, isPrivate) -> repository.setPhotoPrivate(placeId, photoId, isPrivate) }
            // 4. Deleted photos last, once nothing new depends on them.
            photos.removed.forEach { photoId -> repository.deletePhoto(placeId, photoId) }
            place
        }
    }
}
