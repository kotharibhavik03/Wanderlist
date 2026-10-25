package com.rizz.wanderlist.data.storage

import com.rizz.wanderlist.domain.repository.PhotoStorage
import dev.gitlive.firebase.storage.Data
import dev.gitlive.firebase.storage.File
import dev.gitlive.firebase.storage.FirebaseStorage
import dev.gitlive.firebase.storage.FirebaseStorageException
import dev.gitlive.firebase.storage.storageMetadata
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeoutOrNull

/** Photo files in Firebase Storage. Access is checked by storage.rules. */
class FirebasePhotoStorage(private val storage: FirebaseStorage) : PhotoStorage {

    override suspend fun upload(remotePath: String, localPath: String) {
        // storage.rules only accept JPEGs, and the content type is what it checks.
        storage.reference(remotePath).putFile(storageFile(localPath), storageMetadata { contentType = "image/jpeg" })
    }

    override suspend fun download(remotePath: String): ByteArray? {
        // Through the SDK, which sends the user's sign-in token, so storage.rules checks every
        // download. The SDK keeps retrying for up to 10 minutes while offline; a photo that
        // can't load sooner shows as failed instead.
        val data = try {
            withTimeoutOrNull(DOWNLOAD_TIMEOUT) { storage.reference(remotePath).getData(MAX_DOWNLOAD_BYTES) }
                ?: error("Downloading $remotePath timed out.")
        } catch (e: FirebaseStorageException) {
            if (e.isObjectNotFound()) return null
            throw e
        }
        return data.toByteArray()
    }

    override suspend fun publicUrl(remotePath: String): String =
        withoutDefaultPort(storage.reference(remotePath).getDownloadUrl())

    override suspend fun delete(remotePath: String) {
        storage.reference(remotePath).delete()
    }

    private companion object {
        val DOWNLOAD_TIMEOUT = 30.seconds
        /** storage.rules caps uploads at 5 MB; anything larger isn't one of ours. */
        const val MAX_DOWNLOAD_BYTES = 10L * 1024 * 1024
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

/** Downloaded bytes (an Android ByteArray, an iOS NSData) as a ByteArray. */
internal expect fun Data.toByteArray(): ByteArray

/** Whether the download failed because there's no file at that path. */
internal expect fun FirebaseStorageException.isObjectNotFound(): Boolean
