package com.rizz.wanderlist.domain.usecase

import com.rizz.wanderlist.domain.model.PlaceCollection
import com.rizz.wanderlist.domain.repository.CollectionRepository
import com.rizz.wanderlist.util.resultOf

enum class CollectionError { NameBlank, NameTooLong, NoColor }

sealed interface CreateCollectionResult {
    data class Success(val collection: PlaceCollection) : CreateCollectionResult
    data class Invalid(val error: CollectionError) : CreateCollectionResult

    /** Valid input, but saving failed, e.g. no connection or permission denied. */
    data class Failed(val message: String) : CreateCollectionResult
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

        return resultOf { repository.createCollection(cleanName, icon, colors) }.fold(
            onSuccess = { CreateCollectionResult.Success(it) },
            onFailure = { CreateCollectionResult.Failed(it.message ?: "Couldn't save this collection.") }
        )
    }

    companion object {
        const val MAX_NAME_LENGTH = 40
    }
}
