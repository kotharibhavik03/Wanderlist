package com.rizz.wanderlist.data.storage

import com.rizz.wanderlist.domain.repository.PhotoStorage
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.storage.File
import dev.gitlive.firebase.storage.FirebaseStorage
import dev.gitlive.firebase.storage.storageMetadata

/** Photo files in Firebase Storage. Access is checked by storage.rules. */
class FirebasePhotoStorage(
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth
) : PhotoStorage {

    /** Storage's REST address for objects in the app's bucket. */
    private val objectsUrl by lazy { "https://firebasestorage.googleapis.com/v0/b/${storage.reference.bucket}/o/" }

    override suspend fun upload(remotePath: String, localPath: String) {
        // storage.rules only accept JPEGs, and the content type is what it checks.
        storage.reference(remotePath).putFile(storageFile(localPath), storageMetadata { contentType = "image/jpeg" })
    }

    // Downloads go over plain HTTPS rather than the SDK: the SDK queues downloads on a few
    // threads, so a grid of photos loaded a handful at a time. Storage reads the user from the
    // "Authorization: Firebase <ID token>" header (as Firebase's own web SDK sends it) and checks
    // storage.rules for every download.
    override fun downloadUrl(remotePath: String): String = objectsUrl + percentEncoded(remotePath) + "?alt=media"

    override suspend fun downloadHeaders(url: String): Map<String, String> {
        // Public links (profile photos) carry their own token and need nothing.
        if (!url.startsWith(objectsUrl) || "token=" in url) return emptyMap()
        // The SDK's cached ID token, refreshed only when it's about to expire.
        val idToken = auth.currentUser?.getIdToken(false) ?: return emptyMap()
        return mapOf("Authorization" to "Firebase $idToken")
    }

    override suspend fun publicUrl(remotePath: String): String =
        withoutDefaultPort(storage.reference(remotePath).getDownloadUrl())

    override suspend fun delete(remotePath: String) {
        storage.reference(remotePath).delete()
    }
}

/** [path] as one URL path segment: Storage wants the object's whole path there, slashes encoded. */
private fun percentEncoded(path: String): String = buildString {
    path.encodeToByteArray().forEach { byte ->
        val char = (byte.toInt() and 0xFF).toChar()
        if (char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in "-_.~") {
            append(char)
        } else {
            append('%').append((byte.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0'))
        }
    }
}

/**
 * The iOS SDK writes the HTTPS port into download URLs ("https://firebasestorage.googleapis.com:443/...");
 * Android doesn't. Same address either way, but firestore.rules only accept the form without a port,
 * so every stored URL uses that one.
 */
private fun withoutDefaultPort(url: String): String =
    url.replaceFirst("https://firebasestorage.googleapis.com:443/", "https://firebasestorage.googleapis.com/")

/** Firebase's platform file handle (an Android Uri, an iOS NSURL) for a local [path]. */
internal expect fun storageFile(path: String): File
