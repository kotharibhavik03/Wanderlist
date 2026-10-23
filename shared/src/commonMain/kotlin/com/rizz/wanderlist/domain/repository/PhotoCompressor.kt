package com.rizz.wanderlist.domain.repository

/**
 * Turns a photo the user picked into the small JPEG the app uploads: at most
 * [MAX_EDGE_PX] on its longest side, [JPEG_QUALITY]% quality, upright (the camera's
 * rotation is applied to the pixels). The result is a file in the app's cache.
 */
interface PhotoCompressor {
    /**
     * Compresses the photo at [source] (what the platform's photo picker returned) and
     * returns the path of the new JPEG file. Throws if the photo can't be read.
     */
    suspend fun compress(source: String): String

    /** Deletes a file returned by [compress]. Does nothing if it's already gone. */
    fun discard(compressedPath: String)

    companion object {
        const val MAX_EDGE_PX = 1600
        const val JPEG_QUALITY = 80
    }
}
