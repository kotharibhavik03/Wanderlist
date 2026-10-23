package com.rizz.wanderlist.data.firestore

import com.rizz.wanderlist.domain.model.User
import com.rizz.wanderlist.domain.model.UserProfile
import com.rizz.wanderlist.domain.repository.ProfileRepository
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/**
 * How a profile is stored at `profiles/{lowercase email}`. Keyed by email because places
 * list their members by email, so a name is one direct read away. The rules allow reading
 * a single profile but not listing them, so nobody can enumerate users.
 */
@Serializable
internal data class ProfileDocument(
    val uid: String = "",
    val name: String? = null,
    val photoUrl: String? = null,
    /** Client clock, epoch millis. */
    val updatedAt: Long = 0
)

class FirestoreProfileRepository(private val firestore: FirebaseFirestore) : ProfileRepository {

    private val profiles get() = firestore.collection(PROFILES)

    override suspend fun saveMyProfile(user: User) {
        val email = user.email?.lowercase()?.takeIf { it.isUsableId() } ?: return
        val document = ProfileDocument(
            uid = user.id,
            name = user.displayName,
            photoUrl = user.photoUrl,
            updatedAt = Clock.System.now().toEpochMilliseconds()
        )
        profiles.document(email).set(document) { encodeDefaults = true }
    }

    override fun observeProfiles(emails: Set<String>): Flow<Map<String, UserProfile>> {
        val ids = emails.map { it.lowercase() }.filter { it.isUsableId() }.distinct()
        if (ids.isEmpty()) return flowOf(emptyMap())

        val perEmail: List<Flow<UserProfile?>> = ids.map { email ->
            profiles.document(email).snapshots
                .map { snapshot ->
                    if (!snapshot.exists) return@map null
                    val document = snapshot.data<ProfileDocument>()
                    UserProfile(email = email, name = document.name, photoUrl = document.photoUrl)
                }
                // One unreadable profile shouldn't blank out everyone else's name.
                .catch { emit(null) }
        }
        return combine(perEmail) { found -> found.filterNotNull().associateBy { it.email } }
    }

    /** A Firestore document id can't contain "/". Real email addresses don't. */
    private fun String.isUsableId() = isNotBlank() && '/' !in this

    private companion object {
        const val PROFILES = "profiles"
    }
}
