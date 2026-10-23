package com.rizz.wanderlist.data.firestore

import com.rizz.wanderlist.domain.model.PlaceCollection
import com.rizz.wanderlist.domain.repository.AuthRepository
import com.rizz.wanderlist.domain.repository.CollectionRepository
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Collections in Firestore at `collections/{id}`, each owned by one user. */
class FirestoreCollectionRepository(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository
) : CollectionRepository {

    private val collections get() = firestore.collection(COLLECTIONS)

    /**
     * Only the signed-in user's collections; the security rules reject any query that isn't
     * filtered by owner. Switching accounts swaps the listener, so nobody sees another
     * user's collections.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCollections(): Flow<List<PlaceCollection>> =
        authRepository.currentUser
            .map { it?.id }
            .distinctUntilChanged()
            .flatMapLatest { userId ->
                if (userId == null) {
                    flowOf(emptyList())
                } else {
                    collections.where { "ownerId" equalTo userId }.snapshots.map { snapshot ->
                        snapshot.documents
                            .map { it.id to it.data<CollectionDocument>() }
                            // Sorted here rather than with orderBy, which would need a composite index.
                            .sortedBy { (_, document) -> document.createdAt }
                            .map { (id, document) -> document.toDomain(id) }
                    }
                }
            }
            // A listener error (e.g. permission denied mid sign-out) would otherwise crash the
            // screen collecting it. Show nothing rather than stale or foreign data.
            .catch { emit(emptyList()) }

    override suspend fun createCollection(name: String, icon: String, colors: List<Long>): PlaceCollection {
        val owner = authRepository.currentUser.first() ?: error("You need to be signed in to do that.")
        val reference = collections.document // new auto-id, known before the write
        val document = CollectionDocument(
            ownerId = owner.id,
            name = name,
            icon = icon,
            colors = colors,
            createdAt = Clock.System.now().toEpochMilliseconds()
        )
        reference.set(document) { encodeDefaults = true }
        return document.toDomain(reference.id)
    }

    override suspend fun updateCollection(
        collectionId: String,
        name: String,
        icon: String,
        colors: List<Long>
    ): PlaceCollection {
        val owner = authRepository.currentUser.first() ?: error("You need to be signed in to do that.")
        val reference = collections.document(collectionId)
        val snapshot = reference.get()
        check(snapshot.exists) { "This collection no longer exists." }
        val current = snapshot.data<CollectionDocument>()
        // firestore.rules enforces this too; checking here gives a clear message.
        check(current.ownerId == owner.id) { "Only the owner can edit this collection." }
        // Owner and creation time stay as they are.
        val updated = current.copy(name = name, icon = icon, colors = colors)
        reference.set(updated) { encodeDefaults = true }
        return updated.toDomain(collectionId)
    }

    override suspend fun deleteCollection(collectionId: String) {
        collections.document(collectionId).delete()
    }

    private companion object {
        const val COLLECTIONS = "collections"
    }
}
