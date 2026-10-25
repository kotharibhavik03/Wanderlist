package com.rizz.wanderlist.views.image

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Dispatcher

// OkHttp runs at most 5 requests per server at a time by default, so a screen of photo tiles
// loaded in waves of five. Every photo comes from the same server over one HTTP/2 connection,
// which carries many downloads side by side; 16 covers a full screen of tiles.
private const val MAX_PHOTO_DOWNLOADS = 16

internal actual fun photoHttpEngine(): HttpClientEngine = OkHttp.create {
    config {
        dispatcher(Dispatcher().apply { maxRequestsPerHost = MAX_PHOTO_DOWNLOADS })
    }
}
