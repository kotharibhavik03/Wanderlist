package com.rizz.wanderlist.views.image

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.rizz.wanderlist.domain.model.PhotoImage
import com.rizz.wanderlist.domain.repository.PhotoStorage
import okio.Buffer

/**
 * A file in photo storage, as something Coil can load: `AsyncImage(model = photo.image.thumbnail)`.
 * If there's no file at [path], [fallbackPath] is loaded instead.
 */
internal data class StoredPhoto(val path: String, val fallbackPath: String? = null)

/** The photo's small copy, for tiles and markers. Photos saved before thumbnails existed fall back to the photo. */
internal val PhotoImage.thumbnail: StoredPhoto get() = StoredPhoto(thumbnailPath, fallbackPath = path)

/** The full photo, for the viewer. */
internal val PhotoImage.full: StoredPhoto get() = StoredPhoto(path)

/**
 * Loads a [StoredPhoto] through [PhotoStorage], which downloads as the signed-in user, so the
 * storage's rules decide every time whether they may see it. Downloads are kept in Coil's disk
 * cache under their path, so a photo seen once loads from the device after that, offline too.
 */
internal class StoredPhotoFetcher(
    private val photo: StoredPhoto,
    private val options: Options,
    private val imageLoader: ImageLoader,
    private val storage: PhotoStorage
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val (source, dataSource) = load(photo.path)
            ?: photo.fallbackPath?.let { load(it) }
            ?: error("There's no photo at ${photo.path}.")
        return SourceFetchResult(source, mimeType = "image/jpeg", dataSource = dataSource)
    }

    /** The file at [path], from the disk cache if it's there, else downloaded (and cached). Null if there's no such file. */
    private suspend fun load(path: String): Pair<ImageSource, DataSource>? {
        val diskCache = imageLoader.diskCache
        if (diskCache != null && options.diskCachePolicy.readEnabled) {
            diskCache.openSnapshot(path)?.let { snapshot ->
                return ImageSource(snapshot.data, diskCache.fileSystem, path, snapshot) to DataSource.DISK
            }
        }

        val bytes = storage.download(path) ?: return null

        if (diskCache != null && options.diskCachePolicy.writeEnabled) {
            diskCache.openEditor(path)?.let { editor ->
                try {
                    diskCache.fileSystem.write(editor.data) { write(bytes) }
                    editor.commitAndOpenSnapshot()?.let { snapshot ->
                        return ImageSource(snapshot.data, diskCache.fileSystem, path, snapshot) to DataSource.NETWORK
                    }
                } catch (e: Exception) {
                    // Not cached this time; the photo is still shown from memory below.
                    runCatching { editor.abort() }
                }
            }
        }
        return ImageSource(Buffer().write(bytes), options.fileSystem) to DataSource.NETWORK
    }

    class Factory(private val storage: PhotoStorage) : Fetcher.Factory<StoredPhoto> {
        override fun create(data: StoredPhoto, options: Options, imageLoader: ImageLoader): Fetcher =
            StoredPhotoFetcher(data, options, imageLoader, storage)
    }
}

/** Coil's memory cache key for a [StoredPhoto]: its path (Coil adds the requested size). */
internal object StoredPhotoKeyer : Keyer<StoredPhoto> {
    override fun key(data: StoredPhoto, options: Options): String = data.path
}
