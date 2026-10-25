package com.rizz.wanderlist.data.firestore

import com.rizz.wanderlist.domain.model.User
import com.rizz.wanderlist.domain.model.UserProfile
import com.rizz.wanderlist.domain.repository.AuthRepository
import com.rizz.wanderlist.domain.repository.PhotoStorage
import com.rizz.wanderlist.domain.repository.ProfileRepository
import com.rizz.wanderlist.util.resultOf
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
    /** The photo everyone sees: the user's own upload, else their account's (Google) photo. */
    val photoUrl: String? = null,
    /**
     * Set when [photoUrl] is a photo the user uploaded, stored at [profilePhotoPath]. Signing in
     * keeps it instead of putting the account's photo back.
     */
    val customPhotoId: String? = null,
    /** Client clock, epoch millis. */
    val updatedAt: Long = 0
)

class FirestoreProfileRepository(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val photoStorage: PhotoStorage
) : ProfileRepository {

    private val profiles get() = firestore.collection(PROFILES)

    override suspend fun saveMyProfile(user: User) {
        val email = user.email?.lowercase()?.takeIf { it.isUsableId() } ?: return
        val reference = profiles.document(email)
        // A transaction, so a photo uploaded on another device at the same moment isn't lost.
        firestore.runTransaction {
            val snapshot = get(reference)
            val current = if (snapshot.exists) snapshot.data(ProfileDocument.serializer()) else null
            val custom = current?.takeIf { it.customPhotoId != null }
            val document = ProfileDocument(
                uid = user.id,
                name = user.displayName,
                photoUrl = custom?.photoUrl ?: user.photoUrl,
                customPhotoId = custom?.customPhotoId,
                updatedAt = now()
            )
            set(reference, ProfileDocument.serializer(), document) { encodeDefaults = true }
        }
    }

    override fun observeProfiles(emails: Set<String>): Flow<Map<String, UserProfile>> {
        val ids = emails.map { it.lowercase() }.filter { it.isUsableId() }.distinct()
        if (ids.isEmpty()) return flowOf(emptyMap())

        val perEmail: List<Flow<UserProfile?>> = ids.map { email ->
            profiles.document(email).snapshots
                .map { snapshot ->
                    if (!snapshot.exists) return@map null
                    val document = snapshot.data<ProfileDocument>()
                    UserProfile(
                        email = email,
                        name = document.name,
                        photoUrl = document.photoUrl,
                        hasCustomPhoto = document.customPhotoId != null
                    )
                }
                // One unreadable profile shouldn't blank out everyone else's name.
                .catch { emit(null) }
        }
        return combine(perEmail) { found -> found.filterNotNull().associateBy { it.email } }
    }

    override suspend fun setMyPhoto(localPath: String) {
        val user = requireSignedInUser()
        val reference = myProfile(user)
        // A new file name every time: Storage files can't be overwritten, and a cached old
        // photo is never shown under the new URL.
        val photoId = newPhotoId()
        val path = profilePhotoPath(user.id, photoId)
        photoStorage.upload(path, localPath)

        val previous = try {
            // Profile photos are for every signed-in user, so a public link is what's shared.
            val url = photoStorage.publicUrl(path)
            firestore.runTransaction {
                val snapshot = get(reference)
                val current = if (snapshot.exists) snapshot.data(ProfileDocument.serializer()) else null
                val document = ProfileDocument(
                    uid = user.id,
                    name = user.displayName,
                    photoUrl = url,
                    customPhotoId = photoId,
                    updatedAt = now()
                )
                set(reference, ProfileDocument.serializer(), document) { encodeDefaults = true }
                current?.customPhotoId
            }
        } catch (e: Throwable) {
            // The profile still points at the old photo, so the new file would be an orphan.
            withContext(NonCancellable) { resultOf { photoStorage.delete(path) } }
            throw e
        }
        deleteCustomPhoto(user, previous)
    }

    override suspend fun useAccountPhoto() {
        val user = requireSignedInUser()
        val reference = myProfile(user)
        val previous = firestore.runTransaction {
            val snapshot = get(reference)
            val current = if (snapshot.exists) snapshot.data(ProfileDocument.serializer()) else null
            val document = ProfileDocument(
                uid = user.id,
                name = user.displayName,
                photoUrl = user.photoUrl,
                customPhotoId = null,
                updatedAt = now()
            )
            set(reference, ProfileDocument.serializer(), document) { encodeDefaults = true }
            current?.customPhotoId
        }
        deleteCustomPhoto(user, previous)
    }

    /**
     * Deletes a photo the profile no longer uses. Best effort: nothing points at it any more,
     * so a file that can't be deleted right now is only wasted space.
     */
    private suspend fun deleteCustomPhoto(user: User, photoId: String?) {
        photoId ?: return
        resultOf { photoStorage.delete(profilePhotoPath(user.id, photoId)) }
    }

    private fun myProfile(user: User) =
        profiles.document(user.email?.lowercase()?.takeIf { it.isUsableId() } ?: error("Your account has no email address."))

    private suspend fun requireSignedInUser(): User =
        authRepository.currentUser.first() ?: error("You need to be signed in to do that.")

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    @OptIn(ExperimentalUuidApi::class)
    private fun newPhotoId(): String = Uuid.random().toHexString()

    private companion object {
        const val PROFILES = PROFILES_COLLECTION
    }
}

internal const val PROFILES_COLLECTION = "profiles"

/**
 * Where an uploaded profile photo lives in Storage. The user's id is part of the path, so
 * storage.rules can let people write and delete only their own.
 */
internal fun profilePhotoPath(uid: String, photoId: String) = "profiles/$uid/$photoId.jpg"

/**
 * The user id of whoever signed in with [email], or null if nobody has yet. Trustworthy: only
 * that account can write its profile, and the rules require the profile's uid to be its own.
 */
internal suspend fun FirebaseFirestore.uidForEmail(email: String): String? {
    val id = email.lowercase().takeIf { it.isUsableId() } ?: return null
    val snapshot = collection(PROFILES_COLLECTION).document(id).get()
    return if (snapshot.exists) snapshot.data<ProfileDocument>().uid.ifBlank { null } else null
}

/** A Firestore document id can't contain "/". Real email addresses don't. */
private fun String.isUsableId() = isNotBlank() && '/' !in this
