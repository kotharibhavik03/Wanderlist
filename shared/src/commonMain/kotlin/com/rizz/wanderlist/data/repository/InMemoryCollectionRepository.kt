package com.rizz.wanderlist.data.repository

import com.rizz.wanderlist.domain.model.PlaceCollection
import com.rizz.wanderlist.domain.repository.CollectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class InMemoryCollectionRepository : CollectionRepository {
    private val collections = MutableStateFlow<List<PlaceCollection>>(emptyList())

    override fun observeCollections(): Flow<List<PlaceCollection>> = collections

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun createCollection(
        name: String,
        icon: String,
        colors: List<Long>
    ): PlaceCollection {
        val created = PlaceCollection(
            id = Uuid.random().toString(),
            name = name,
            icon = icon,
            colors = colors
        )
        collections.update { current -> current + created }
        return created
    }

    override suspend fun deleteCollection(collectionId: String) {
        collections.update { current -> current.filterNot { it.id == collectionId } }
    }
}