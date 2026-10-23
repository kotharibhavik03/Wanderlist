package com.rizz.wanderlist.domain.usecase

import com.rizz.wanderlist.domain.model.PlaceCollection
import com.rizz.wanderlist.domain.repository.CollectionRepository

enum class CollectionError { NameBlank, NameTooLong, NoColor }

sealed interface CreateCollectionResult {
    data class Success(val collection: PlaceCollection) : CreateCollectionResult
    data class Invalid(val error: CollectionError) : CreateCollectionResult
}

class CreateCollectionUseCase(private val repository: CollectionRepository) {

    suspend operator fun invoke(
        name: String,
        icon: String,
        colors: List<Long>
    ): CreateCollectionResult {
        val cleanName = name.trim()
        val error = when {
            cleanName.isEmpty() -> CollectionError.NameBlank
            cleanName.length > MAX_NAME_LENGTH -> CollectionError.NameTooLong
            colors.isEmpty() -> CollectionError.NoColor
            else -> null
        }
        if (error != null) return CreateCollectionResult.Invalid(error)

        return CreateCollectionResult.Success(repository.createCollection(cleanName, icon, colors))
    }

    companion object {
        const val MAX_NAME_LENGTH = 40
    }
}