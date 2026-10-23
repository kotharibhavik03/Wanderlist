package com.rizz.wanderlist.data.photo

import com.rizz.wanderlist.domain.repository.PhotoCompressor
import com.rizz.wanderlist.domain.repository.PhotoCompressor.Companion.JPEG_QUALITY
import com.rizz.wanderlist.domain.repository.PhotoCompressor.Companion.MAX_EDGE_PX
import kotlin.math.max
import kotlin.math.min
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.writeToFile
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage

/**
 * Compresses picked photos (files copied by the iOS photo picker, often HEIC) into JPEGs in
 * the app's cache. UIKit reads HEIC and applies the photo's orientation while drawing.
 */
class IosPhotoCompressor : PhotoCompressor {

    @OptIn(ExperimentalForeignApi::class, ExperimentalUuidApi::class)
    override suspend fun compress(source: String): String = withContext(Dispatchers.Default) {
        try {
            val image = UIImage.imageWithContentsOfFile(source) ?: error("This photo couldn't be read.")

            // UIImage's size is already upright (orientation applied), in pixels for a file image.
            val (width, height) = image.size.useContents { width to height }
            val scale = min(1.0, MAX_EDGE_PX / max(width, height))
            val targetWidth = width * scale
            val targetHeight = height * scale

            // One pixel per point, and opaque: photos have no transparency, and JPEG can't store it.
            val format = UIGraphicsImageRendererFormat().apply {
                this.scale = 1.0
                opaque = true
            }
            val renderer = UIGraphicsImageRenderer(size = CGSizeMake(targetWidth, targetHeight), format = format)
            // UIGraphicsImageRenderer is safe to use off the main thread.
            val jpeg = renderer.JPEGDataWithCompressionQuality(JPEG_QUALITY / 100.0) {
                image.drawInRect(CGRectMake(0.0, 0.0, targetWidth, targetHeight))
            }

            val path = PhotoFiles.compressedDirectory + "/${Uuid.random().toHexString()}.jpg"
            check(jpeg.writeToFile(path, atomically = true)) { "This photo couldn't be saved." }
            path
        } finally {
            // The picker's copy has served its purpose, whether or not compressing worked.
            if (source.startsWith(PhotoFiles.pickedDirectory)) PhotoFiles.delete(source)
        }
    }

    override fun discard(compressedPath: String) = PhotoFiles.delete(compressedPath)
}
