package com.rizz.wanderlist.domain.usecase

import com.rizz.wanderlist.domain.model.MAX_PHOTOS_PER_SAVE
import com.rizz.wanderlist.domain.model.MarkerStyle
import com.rizz.wanderlist.domain.model.NewPlace
import com.rizz.wanderlist.domain.model.Place
import com.rizz.wanderlist.domain.repository.PlaceRepository
import com.rizz.wanderlist.util.resultOf

class SavePlaceUseCase(private val repository: PlaceRepository) {

    suspend operator fun invoke(
        newPlace: NewPlace,
        onPhotoUploaded: (uploaded: Int) -> Unit = {}
    ): Result<Place> {
        validate(newPlace)?.let { return Result.failure(IllegalArgumentException(it)) }
        return resultOf { repository.createPlace(newPlace, onPhotoUploaded) }
    }

    private fun validate(place: NewPlace): String? {
        validatePlaceDetails(place.details)?.let { return it }
        if (place.photos.size > MAX_PHOTOS_PER_SAVE) {
            return "You can add up to $MAX_PHOTOS_PER_SAVE photos at a time."
        }
        val marker = place.details.markerStyle
        return if (marker is MarkerStyle.Photo &&
            place.photos.none { it.id == marker.photoId && !it.isPrivate }
        ) {
            "The marker photo must be one of the shared photos."
        } else {
            null
        }
    }
}
