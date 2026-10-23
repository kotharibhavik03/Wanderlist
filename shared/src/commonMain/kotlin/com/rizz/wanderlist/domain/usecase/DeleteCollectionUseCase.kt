package com.rizz.wanderlist.domain.usecase

import com.rizz.wanderlist.domain.repository.CollectionRepository
import com.rizz.wanderlist.domain.repository.PlaceRepository
import com.rizz.wanderlist.util.resultOf

/**
 * Deletes a collection together with its places. Places go first: if deleting the
 * collection then fails, it is left empty rather than leaving places with no collection.
 */
class DeleteCollectionUseCase(
    private val collectionRepository: CollectionRepository,
    private val placeRepository: PlaceRepository
) {
    suspend operator fun invoke(collectionId: String): Result<Unit> = resultOf {
        placeRepository.deletePlacesInCollection(collectionId)
        collectionRepository.deleteCollection(collectionId)
    }
}
