package com.rizz.wanderlist.domain.repository

import com.rizz.wanderlist.domain.model.NewPlace
import com.rizz.wanderlist.domain.model.Place
import kotlinx.coroutines.flow.Flow

interface PlaceRepository {
    /**
     * Places of one collection the signed-in user may see, newest first.
     * The repository applies visibility: other people's private photos are already removed.
     */
    fun observePlaces(collectionId: String): Flow<List<Place>>

    suspend fun createPlace(newPlace: NewPlace): Place
}