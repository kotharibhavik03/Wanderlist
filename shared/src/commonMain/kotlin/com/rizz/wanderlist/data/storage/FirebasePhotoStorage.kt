package com.rizz.wanderlist.data.storage

import com.rizz.wanderlist.domain.repository.PhotoStorage
import dev.gitlive.firebase.storage.File
import dev.gitlive.firebase.storage.FirebaseStorage
import dev.gitlive.firebase.storage.storageMetadata

/** Photo files in Firebase Storage. Access is checked by storage.rules. */
class FirebasePhotoStorage(private val storage: FirebaseStorage) : PhotoStorage {

    override suspend fun upload(remotePath: String, localPath: String): String {
        val reference = storage.reference(remotePath)
        // storage.rules only accept JPEGs, and the content type is what it checks.
        reference.putFile(storageFile(localPath), storageMetadata { contentType = "image/jpeg" })
        return withoutDefaultPort(reference.getDownloadUrl())
    }

    override suspend fun delete(remotePath: String) {
        storage.reference(remotePath).delete()
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
