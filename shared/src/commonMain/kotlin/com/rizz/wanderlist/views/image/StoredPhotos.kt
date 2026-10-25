package com.rizz.wanderlist.views.image

import coil3.Uri
import coil3.map.Mapper
import coil3.request.Options
import coil3.toUri
import com.rizz.wanderlist.domain.model.PhotoImage
import com.rizz.wanderlist.domain.repository.PhotoStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.api.createClientPlugin

/** A file in photo storage, as something Coil can load: `AsyncImage(model = photo.image.thumbnail)`. */
internal data class StoredPhoto(val path: String)

/** The photo's small copy, for tiles and markers. */
internal val PhotoImage.thumbnail: StoredPhoto get() = StoredPhoto(thumbnailPath)

/** The full photo, for the viewer. */
internal val PhotoImage.full: StoredPhoto get() = StoredPhoto(path)

/**
 * Turns a [StoredPhoto] into its download address, which Coil's network loader then fetches
 * and keeps in its disk cache (the address holds no token, so it's the same every time).
 */
internal class StoredPhotoMapper(private val storage: PhotoStorage) : Mapper<StoredPhoto, Uri> {
    override fun map(data: StoredPhoto, options: Options): Uri = storage.downloadUrl(data.path).toUri()
}

/**
 * The HTTP client Coil downloads with. Requests for stored photos get the signed-in user's
 * headers from [storage] (added just before sending, so they're always current), and the
 * storage's rules decide whether that user may see the photo.
 */
internal fun photoHttpClient(storage: PhotoStorage): HttpClient = HttpClient(photoHttpEngine()) {
    install(
        createClientPlugin("PhotoStorageAuth") {
            onRequest { request, _ ->
                storage.downloadHeaders(request.url.buildString()).forEach { (name, value) ->
                    request.headers[name] = value
                }
            }
        }
    )
}

/** The platform's HTTP engine, set up to download a screen of photos at once. */
internal expect fun photoHttpEngine(): HttpClientEngine
