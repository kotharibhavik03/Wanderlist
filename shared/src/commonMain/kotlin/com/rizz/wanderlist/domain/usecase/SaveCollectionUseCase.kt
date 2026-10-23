package com.rizz.wanderlist.domain.usecase

import com.rizz.wanderlist.domain.model.PlaceCollection
import com.rizz.wanderlist.domain.repository.CollectionRepository
import com.rizz.wanderlist.util.resultOf

enum class CollectionError { NameBlank, NameTooLong, NoColor }

sealed interface SaveCollectionResult {
    data class Success(val collection: PlaceCollection) : SaveCollectionResult
    data class Invalid(val error: CollectionError) : SaveCollectionResult

    /** Valid input, but saving failed, e.g. no connection or permission denied. */
    data class Failed(val message: String) : SaveCollectionResult
}

/** Creates a collection, or edits one when given its id. Both follow the same rules. */
class SaveCollectionUseCase(private val repository: CollectionRepository) {

    suspend operator fun invoke(
        collectionId: String?,
        name: String,
        icon: String,
        colors: List<Long>
    ): SaveCollectionResult {
        val cleanName = name.trim()
        val error = when {
            cleanName.isEmpty() -> CollectionError.NameBlank
            cleanName.length > MAX_NAME_LENGTH -> CollectionError.NameTooLong
            colors.isEmpty() -> CollectionError.NoColor
            else -> null
        }
        if (error != null) return SaveCollectionResult.Invalid(error)

        return resultOf {
            if (collectionId == null) {
                repository.createCollection(cleanName, icon, colors)
            } else {
                repository.updateCollection(collectionId, cleanName, icon, colors)
            }
        }.fold(
            onSuccess = { SaveCollectionResult.Success(it) },
            onFailure = { SaveCollectionResult.Failed(it.message ?: "Couldn't save this collection.") }
        )
    }

    companion object {
        const val MAX_NAME_LENGTH = 40
    }
}
