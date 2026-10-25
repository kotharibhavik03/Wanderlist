package com.rizz.wanderlist.views.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.serviceLoaderEnabled
import com.rizz.wanderlist.domain.repository.PhotoStorage

/**
 * The one image loader every photo in the app goes through. Coil keeps decoded photos in memory
 * and the downloaded files on disk, so a photo seen once doesn't download again.
 *
 * Place photos ([StoredPhoto]) download through [photoStorage], as the signed-in user. Everything
 * else with a URL (profile photos) uses Ktor, which picks the platform's own HTTP engine (OkHttp
 * on Android, NSURLSession on iOS). Both are added here by hand rather than discovered
 * automatically, so iOS and Android are set up the same, visible way.
 */
internal fun newAppImageLoader(context: PlatformContext, photoStorage: PhotoStorage): ImageLoader =
    ImageLoader.Builder(context)
        .serviceLoaderEnabled(false)
        .components {
            add(StoredPhotoFetcher.Factory(photoStorage))
            add(StoredPhotoKeyer)
            add(KtorNetworkFetcherFactory())
        }
        .crossfade(true)
        .build()
