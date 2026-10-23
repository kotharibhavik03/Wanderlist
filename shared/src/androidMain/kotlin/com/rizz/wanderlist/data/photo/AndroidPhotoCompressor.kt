package com.rizz.wanderlist.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.rizz.wanderlist.domain.repository.PhotoCompressor
import com.rizz.wanderlist.domain.repository.PhotoCompressor.Companion.JPEG_QUALITY
import com.rizz.wanderlist.domain.repository.PhotoCompressor.Companion.MAX_EDGE_PX
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Compresses picked photos (content:// uris from the photo picker) into the app's cache. */
class AndroidPhotoCompressor(private val context: Context) : PhotoCompressor {

    private val directory get() = File(context.cacheDir, "photos").apply { mkdirs() }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun compress(source: String): String = withContext(Dispatchers.IO) {
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

        val file = File(directory, "${Uuid.random().toHexString()}.jpg")
        try {
            file.outputStream().use { result.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        } finally {
            if (result !== decoded) result.recycle()
            decoded.recycle()
        }
        file.absolutePath
    }

    override fun discard(compressedPath: String) {
        File(compressedPath).delete()
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
