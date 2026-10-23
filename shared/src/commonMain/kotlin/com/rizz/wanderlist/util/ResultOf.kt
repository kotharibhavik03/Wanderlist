package com.rizz.wanderlist.util

import kotlin.coroutines.cancellation.CancellationException

/**
 * Like runCatching, but lets coroutine cancellation through instead of reporting it as a failure.
 * Swallowing CancellationException would keep a cancelled coroutine running.
 */
suspend fun <T> resultOf(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
