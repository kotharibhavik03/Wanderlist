package com.rizz.wanderlist.domain.usecase

import com.rizz.wanderlist.domain.model.Place
import com.rizz.wanderlist.domain.model.PlaceDetails
import com.rizz.wanderlist.domain.repository.PlaceRepository
import com.rizz.wanderlist.util.resultOf

/**
 * Saves edits to an existing place. Who may change what, and whether a photo
 * marker is still valid, is checked by the repository, which holds the current place.
 */
class UpdatePlaceUseCase(private val repository: PlaceRepository) {

    suspend operator fun invoke(placeId: String, details: PlaceDetails): Result<Place> {
        validatePlaceDetails(details)?.let { return Result.failure(IllegalArgumentException(it)) }
        return resultOf { repository.updatePlace(placeId, details) }
    }
}
