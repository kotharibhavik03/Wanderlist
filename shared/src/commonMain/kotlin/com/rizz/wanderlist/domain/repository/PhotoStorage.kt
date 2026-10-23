package com.rizz.wanderlist.domain.repository

/**
 * Where photo files live. Firebase Storage today; kept behind this interface so the
 * provider can change without touching the rest of the app.
 */
interface PhotoStorage {
    /**
     * Uploads the JPEG at [localPath] to [remotePath] and returns a URL the image can be
     * downloaded from. Anyone holding the URL can download it, so only store it where the
     * right people can read it.
     */
    suspend fun upload(remotePath: String, localPath: String): String

    /** Deletes the file at [remotePath]. */
    suspend fun delete(remotePath: String)
}
