package com.rizz.wanderlist.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.rizz.wanderlist.domain.model.CompressedPhoto
import com.rizz.wanderlist.domain.repository.PhotoCompressor
import com.rizz.wanderlist.domain.repository.PhotoCompressor.Companion.JPEG_QUALITY
import com.rizz.wanderlist.domain.repository.PhotoCompressor.Companion.MAX_EDGE_PX
import com.rizz.wanderlist.domain.repository.PhotoCompressor.Companion.THUMBNAIL_SHORT_EDGE_PX
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Compresses picked photos (content:// uris from the photo picker) into the app's cache. */
class AndroidPhotoCompressor(private val context: Context) : PhotoCompressor {

    private val directory get() = File(context.cacheDir, "photos").apply { mkdirs() }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun compress(source: String): CompressedPhoto = withContext(Dispatchers.IO) {
        val uri = Uri.parse(source)

        // 1. Read only the dimensions, so the full-size photo is never held in memory.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "This photo couldn't be read." }

        // 2. Decode at a power-of-two fraction of the size that's still at least the target.
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(max(bounds.outWidth, bounds.outHeight))
        }
        val decoded = open(uri).use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("This photo couldn't be read.")

        // 3. Scale to the exact target and apply the camera's orientation in one pass.
        val scale = min(1f, MAX_EDGE_PX.toFloat() / max(decoded.width, decoded.height))
        val matrix = orientation(uri).apply { postScale(scale, scale) }
        val result = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

        // 4. The thumbnail, scaled down from the upright photo.
        val thumbnailScale = min(1f, THUMBNAIL_SHORT_EDGE_PX.toFloat() / min(result.width, result.height))
        val thumbnail = Bitmap.createScaledBitmap(
            result,
            (result.width * thumbnailScale).roundToInt().coerceAtLeast(1),
            (result.height * thumbnailScale).roundToInt().coerceAtLeast(1),
            true
        )

        val name = Uuid.random().toHexString()
        val file = File(directory, "$name.jpg")
        val thumbnailFile = File(directory, "${name}_thumb.jpg")
        try {
            file.outputStream().use { result.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            thumbnailFile.outputStream().use { thumbnail.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        } catch (e: Exception) {
            file.delete()
            thumbnailFile.delete()
            throw e
        } finally {
            // createBitmap / createScaledBitmap return their input when nothing changes.
            if (thumbnail !== result) thumbnail.recycle()
            if (result !== decoded) result.recycle()
            decoded.recycle()
        }
        CompressedPhoto(path = file.absolutePath, thumbnailPath = thumbnailFile.absolutePath)
    }

    override fun discard(photo: CompressedPhoto) {
        File(photo.path).delete()
        File(photo.thumbnailPath).delete()
    }

    private fun open(uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri) ?: error("This photo couldn't be opened.")

    /** The largest power of two that keeps the longest edge at or above the target. */
    private fun sampleSize(longestEdge: Int): Int {
        var sample = 1
        while (longestEdge / (sample * 2) >= MAX_EDGE_PX) sample *= 2
        return sample
    }

    /**
     * Cameras save photos sideways and record which way is up in EXIF. This turns that into
     * a rotation (and a mirror flip for the rare mirrored orientations).
     */
    private fun orientation(uri: Uri): Matrix {
        val exif = runCatching { open(uri).use { ExifInterface(it) } }.getOrNull()
        return Matrix().apply {
            if (exif != null) {
                postRotate(exif.rotationDegrees.toFloat())
                if (exif.isFlipped) postScale(-1f, 1f)
            }
        }
    }
}
