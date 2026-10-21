package com.rizz.wanderlist.data.repository

import com.rizz.wanderlist.domain.model.PlaceCollection
import com.rizz.wanderlist.domain.repository.CollectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class InMemoryCollectionRepository : CollectionRepository {

    private val collections = MutableStateFlow(seedCollections())

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

    private fun seedCollections() = listOf(
        PlaceCollection("1", "Goa trip", "🏖️", listOf(0xFF00ACC1, 0xFFFFB300), 4),
        PlaceCollection("2", "Wanted to visit", "⭐", listOf(0xFF7E57C2, 0xFFEC407A), 12),
        PlaceCollection("3", "Visited", "✅", listOf(0xFF43A047), 7),
        PlaceCollection("4", "Cafes", "☕", listOf(0xFF6D4C41, 0xFFFF7043, 0xFFFFCA28), 9)
    )
}