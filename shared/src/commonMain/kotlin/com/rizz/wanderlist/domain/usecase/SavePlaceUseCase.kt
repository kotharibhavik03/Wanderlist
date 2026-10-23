package com.rizz.wanderlist.domain.usecase

import com.rizz.wanderlist.domain.model.MarkerStyle
import com.rizz.wanderlist.domain.model.NewPlace
import com.rizz.wanderlist.domain.model.Place
import com.rizz.wanderlist.domain.model.PlaceStatus
import com.rizz.wanderlist.domain.repository.PlaceRepository
import kotlin.coroutines.cancellation.CancellationException

class SavePlaceUseCase(private val repository: PlaceRepository) {

    suspend operator fun invoke(newPlace: NewPlace): Result<Place> {
        validate(newPlace)?.let { return Result.failure(IllegalArgumentException(it)) }
        return try {
            Result.success(repository.createPlace(newPlace))
        } catch (e: CancellationException) {
            throw e // never swallow cancellation
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun validate(place: NewPlace): String? {
        val marker = place.markerStyle
        return when {
            place.latitude !in -90.0..90.0 || place.longitude !in -180.0..180.0 ->
                "That location is not valid."
            place.notes.length > MAX_NOTES -> "Notes can be at most $MAX_NOTES characters."
            place.rating != null && place.rating !in 1..5 -> "Rating must be between 1 and 5."
            place.status == PlaceStatus.WISHLIST &&
                    (place.visitedOnEpochMillis != null || place.rating != null) ->
                "A place you want to visit has no visit date or rating."
            marker is MarkerStyle.Photo &&
                    place.photos.none { it.uri == marker.photoId && !it.isPrivate } ->
                "The marker photo must be one of the shared photos."
            else -> null
        }
    }

    private companion object {
        const val MAX_NOTES = 2_000
    }
}