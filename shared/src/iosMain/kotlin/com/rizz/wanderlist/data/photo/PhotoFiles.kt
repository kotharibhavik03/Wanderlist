package com.rizz.wanderlist.data.photo

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDomainMask

/** The app's folders for photos on their way to being uploaded. */
internal object PhotoFiles {

    /**
     * Copies of the originals the photo picker hands over (its own files are deleted as soon
     * as its callback returns). Each copy is removed once it has been compressed.
     */
    val pickedDirectory: String get() = directory(NSTemporaryDirectory() + "picked-photos")

    /** Compressed JPEGs, shown as thumbnails and uploaded on save. */
    val compressedDirectory: String
        get() = directory(
            (NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String) +
                "/photos"
        )

    @OptIn(ExperimentalForeignApi::class)
    fun delete(path: String) {
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun directory(path: String): String {
        NSFileManager.defaultManager.createDirectoryAtPath(
            path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
        return path
    }
}
