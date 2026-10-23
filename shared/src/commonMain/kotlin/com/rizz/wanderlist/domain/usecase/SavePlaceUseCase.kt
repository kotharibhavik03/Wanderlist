package com.rizz.wanderlist.domain.usecase

import com.rizz.wanderlist.domain.model.MarkerStyle
import com.rizz.wanderlist.domain.model.NewPlace
import com.rizz.wanderlist.domain.model.Place
import com.rizz.wanderlist.domain.repository.PlaceRepository
import com.rizz.wanderlist.util.resultOf

class SavePlaceUseCase(private val repository: PlaceRepository) {

    suspend operator fun invoke(newPlace: NewPlace): Result<Place> {
        validate(newPlace)?.let { return Result.failure(IllegalArgumentException(it)) }
        return resultOf { repository.createPlace(newPlace) }
    }

    private fun validate(place: NewPlace): String? {
        validatePlaceDetails(place.details)?.let { return it }
        val marker = place.details.markerStyle
        return if (marker is MarkerStyle.Photo &&
            place.photos.none { it.uri == marker.photoId && !it.isPrivate }
        ) {
            "The marker photo must be one of the shared photos."
        } else {
            null
        }
    }
}
