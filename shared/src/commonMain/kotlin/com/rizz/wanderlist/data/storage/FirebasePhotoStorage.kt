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
        return reference.getDownloadUrl()
    }

    override suspend fun delete(remotePath: String) {
        storage.reference(remotePath).delete()
    }
}

/** Firebase's platform file handle (an Android Uri, an iOS NSURL) for a local [path]. */
internal expect fun storageFile(path: String): File
