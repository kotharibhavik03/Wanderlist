package com.rizz.wanderlist.domain.repository

/**
 * Where photo files live. Firebase Storage today; kept behind this interface so the
 * provider can change without touching the rest of the app.
 */
interface PhotoStorage {
    /** Uploads the JPEG at [localPath] to [remotePath]. */
    suspend fun upload(remotePath: String, localPath: String)

    /**
     * The HTTPS address of the file at [remotePath], to download it as the signed-in user with
     * [downloadHeaders]. It holds no access token: on its own it downloads nothing.
     */
    fun downloadUrl(remotePath: String): String

    /**
     * The headers that make a request to [url] the signed-in user's, when it's one of
     * [downloadUrl]'s addresses (none otherwise). The storage's access rules then decide, on
     * every download, whether they may see the file.
     */
    suspend fun downloadHeaders(url: String): Map<String, String>

    /**
     * A public link to the file at [remotePath]: anyone holding it can download the file, with
     * no access check. Only for files meant for every signed-in user (profile photos).
     */
    suspend fun publicUrl(remotePath: String): String

    /** Deletes the file at [remotePath]. */
    suspend fun delete(remotePath: String)
}
