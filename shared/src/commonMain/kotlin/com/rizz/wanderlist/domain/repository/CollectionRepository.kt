package com.rizz.wanderlist.domain.repository

import com.rizz.wanderlist.domain.model.PlaceCollection
import kotlinx.coroutines.flow.Flow

/** Contract for reading and writing collections. Implementations decide where data lives. */
interface CollectionRepository {

    /** Emits the full list now, and again every time it changes. */
    fun observeCollections(): Flow<List<PlaceCollection>>

    /** Creates a collection and returns it with its generated id. */
    suspend fun createCollection(
        name: String,
        icon: String,
        colors: List<Long>
    ): PlaceCollection

    /** Deletes the collection itself. Its places are removed separately, see DeleteCollectionUseCase. */
    suspend fun deleteCollection(collectionId: String)
}