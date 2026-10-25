package com.rizz.wanderlist.domain.repository

import com.rizz.wanderlist.domain.model.CompressedPhoto

/**
 * Turns a photo the user picked into the JPEGs the app uploads, both upright (the camera's
 * rotation is applied to the pixels) and at [JPEG_QUALITY]% quality, in the app's cache:
 * - the photo: at most [MAX_EDGE_PX] on its longest side, for the full-screen viewer;
 * - its thumbnail: [THUMBNAIL_SHORT_EDGE_PX] on its shortest side (never larger than the photo),
 *   for tiles and markers. Tiles crop to a square, so the short side is what has to be sharp.
 */
interface PhotoCompressor {
    /**
     * Compresses the photo at [source] (what the platform's photo picker returned) and
     * returns the new files. Throws if the photo can't be read.
     */
    suspend fun compress(source: String): CompressedPhoto

    /** Deletes the files returned by [compress]. Does nothing for files already gone. */
    fun discard(photo: CompressedPhoto)

    companion object {
        const val MAX_EDGE_PX = 1600
        const val THUMBNAIL_SHORT_EDGE_PX = 480
        const val JPEG_QUALITY = 80
    }
}
