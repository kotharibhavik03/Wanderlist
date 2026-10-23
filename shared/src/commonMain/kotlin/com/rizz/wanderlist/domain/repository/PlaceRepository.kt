package com.rizz.wanderlist.domain.repository

import com.rizz.wanderlist.domain.model.NewPlace
import com.rizz.wanderlist.domain.model.Place
import com.rizz.wanderlist.domain.model.SharedPlace
import kotlinx.coroutines.flow.Flow

interface PlaceRepository {
    /**
     * Places of one collection the signed-in user may see, newest first.
     * The repository applies visibility: other people's private photos are already removed.
     */
    fun observePlaces(collectionId: String): Flow<List<Place>>

    /** How many places each collection holds, keyed by collection id. Missing key means none. */
    fun observePlaceCounts(): Flow<Map<String, Int>>

    /**
     * Places other people own where the signed-in user is a member, newest first.
     * Empty when signed out.
     */
    fun observeSharedWithMe(): Flow<List<SharedPlace>>

    /** Saves a place owned by the signed-in user. Throws if nobody is signed in. */
    suspend fun createPlace(newPlace: NewPlace): Place
}
