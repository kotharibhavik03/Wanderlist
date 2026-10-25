package com.rizz.wanderlist.views.image

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

// NSURLSession sends every photo request over one HTTP/2 connection at the same time, with no
// per-server cap to raise, so the defaults are right here.
internal actual fun photoHttpEngine(): HttpClientEngine = Darwin.create()
