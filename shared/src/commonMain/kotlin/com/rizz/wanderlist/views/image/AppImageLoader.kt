package com.rizz.wanderlist.views.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.serviceLoaderEnabled

/**
 * The one image loader every photo in the app goes through. Coil keeps decoded photos in memory
 * and the downloaded files on disk, so a photo seen once doesn't download again.
 *
 * Downloads use Ktor, which picks the platform's own HTTP engine (OkHttp on Android, NSURLSession
 * on iOS). The fetcher is added here by hand rather than discovered automatically, so iOS and
 * Android are set up the same, visible way.
 */
internal fun newAppImageLoader(context: PlatformContext): ImageLoader =
    ImageLoader.Builder(context)
        .serviceLoaderEnabled(false)
        .components { add(KtorNetworkFetcherFactory()) }
        .crossfade(true)
        .build()
