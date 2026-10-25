package com.rizz.wanderlist.data.photo

import com.rizz.wanderlist.domain.model.CompressedPhoto
import com.rizz.wanderlist.domain.repository.PhotoCompressor
import com.rizz.wanderlist.domain.repository.PhotoCompressor.Companion.JPEG_QUALITY
import com.rizz.wanderlist.domain.repository.PhotoCompressor.Companion.MAX_EDGE_PX
import com.rizz.wanderlist.domain.repository.PhotoCompressor.Companion.THUMBNAIL_SHORT_EDGE_PX
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
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
    override suspend fun compress(source: String): CompressedPhoto = withContext(Dispatchers.Default) {
        try {
            val image = UIImage.imageWithContentsOfFile(source) ?: error("This photo couldn't be read.")

            // UIImage's size is already upright (orientation applied), in pixels for a file image.
            val (width, height) = image.size.useContents { width to height }
            val scale = min(1.0, MAX_EDGE_PX / max(width, height))
            val photoWidth = (width * scale).roundToInt()
            val photoHeight = (height * scale).roundToInt()
            // The thumbnail is drawn from the original too, so it's as sharp as it can be.
            val thumbnailScale = min(1.0, THUMBNAIL_SHORT_EDGE_PX / min(photoWidth, photoHeight).toDouble())
            val thumbnailWidth = (photoWidth * thumbnailScale).roundToInt().coerceAtLeast(1)
            val thumbnailHeight = (photoHeight * thumbnailScale).roundToInt().coerceAtLeast(1)

            val name = Uuid.random().toHexString()
            val path = PhotoFiles.compressedDirectory + "/$name.jpg"
            val thumbnailPath = PhotoFiles.compressedDirectory + "/${name}_thumb.jpg"
            try {
                check(jpeg(image, photoWidth, photoHeight).writeToFile(path, atomically = true)) {
                    "This photo couldn't be saved."
                }
                check(jpeg(image, thumbnailWidth, thumbnailHeight).writeToFile(thumbnailPath, atomically = true)) {
                    "This photo couldn't be saved."
                }
            } catch (e: Exception) {
                PhotoFiles.delete(path)
                PhotoFiles.delete(thumbnailPath)
                throw e
            }
            CompressedPhoto(path = path, thumbnailPath = thumbnailPath)
        } finally {
            // The picker's copy has served its purpose, whether or not compressing worked.
            if (source.startsWith(PhotoFiles.pickedDirectory)) PhotoFiles.delete(source)
        }
    }

    override fun discard(photo: CompressedPhoto) {
        PhotoFiles.delete(photo.path)
        PhotoFiles.delete(photo.thumbnailPath)
    }

    /** [image] drawn at [width] x [height] pixels, as a JPEG. */
    @OptIn(ExperimentalForeignApi::class)
    private fun jpeg(image: UIImage, width: Int, height: Int): NSData {
        // One pixel per point, and opaque: photos have no transparency, and JPEG can't store it.
        val format = UIGraphicsImageRendererFormat().apply {
            this.scale = 1.0
            opaque = true
        }
        val size = CGSizeMake(width.toDouble(), height.toDouble())
        // UIGraphicsImageRenderer is safe to use off the main thread.
        return UIGraphicsImageRenderer(size = size, format = format).JPEGDataWithCompressionQuality(JPEG_QUALITY / 100.0) {
            image.drawInRect(CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))
        }
    }
}
