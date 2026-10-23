package com.rizz.wanderlist.domain.usecase

import com.rizz.wanderlist.domain.model.CollectionSummary
import com.rizz.wanderlist.domain.repository.CollectionRepository
import com.rizz.wanderlist.domain.repository.PlaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** The user's collections with live place counts. Re-emits when either side changes. */
class ObserveCollectionSummariesUseCase(
    private val collectionRepository: CollectionRepository,
    private val placeRepository: PlaceRepository
) {
    operator fun invoke(): Flow<List<CollectionSummary>> =
        combine(
            collectionRepository.observeCollections(),
            placeRepository.observePlaceCounts()
        ) { collections, counts ->
            collections.map { CollectionSummary(it, placeCount = counts[it.id] ?: 0) }
        }
}
