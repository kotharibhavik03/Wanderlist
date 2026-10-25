package com.rizz.wanderlist.domain.repository

/**
 * Where photo files live. Firebase Storage today; kept behind this interface so the
 * provider can change without touching the rest of the app.
 */
interface PhotoStorage {
    /** Uploads the JPEG at [localPath] to [remotePath]. */
    suspend fun upload(remotePath: String, localPath: String)

    /**
     * Downloads the file at [remotePath] as the signed-in user, so the storage's access rules
     * decide whether they may. Null if there's no file there; throws if it can't be downloaded
     * (no access, no connection).
     */
    suspend fun download(remotePath: String): ByteArray?

    /**
     * A public link to the file at [remotePath]: anyone holding it can download the file, with
     * no access check. Only for files meant for every signed-in user (profile photos).
     */
    suspend fun publicUrl(remotePath: String): String

    /** Deletes the file at [remotePath]. */
    suspend fun delete(remotePath: String)
}
