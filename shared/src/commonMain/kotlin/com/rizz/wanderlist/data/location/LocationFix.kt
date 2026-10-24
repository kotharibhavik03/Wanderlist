package com.rizz.wanderlist.data.location

import com.rizz.wanderlist.domain.model.GeoPoint
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.withTimeoutOrNull

/** One position report from the platform: where, and how far off it may be (radius in metres). */
internal data class LocationFix(val point: GeoPoint, val accuracyMeters: Double)

/**
 * A fix this accurate is used straight away. 65 m is what iOS reports for a Wi-Fi fix, which
 * usually arrives within a second; waiting for GPS (5 to 10 m) can take many seconds indoors.
 */
internal const val GOOD_ENOUGH_ACCURACY_METERS = 65.0

/** After the first fix, how long to wait for a good enough one before taking the best so far. */
internal val SETTLE_TIME: Duration = 3.seconds

/** A position the device already has is used instead of a new one when it's at most this old. */
internal val MAX_CACHED_FIX_AGE: Duration = 2.minutes

/**
 * Picks a position from a stream of [LocationFix]es (cached first, then live updates): the
 * first one within [GOOD_ENOUGH_ACCURACY_METERS], or, once [SETTLE_TIME] has passed since the
 * first fix, the most accurate so far. Waits as long as it takes for the first fix (callers
 * apply their own timeout). Null if the stream ends without any fix.
 */
internal suspend fun Flow<LocationFix>.firstGoodFix(): GeoPoint? = coroutineScope {
    // Unlimited buffer: platform callbacks never block, and a burst of fixes isn't dropped.
    val fixes = buffer(Channel.UNLIMITED).produceIn(this)
    try {
        val first = fixes.nextFix() ?: return@coroutineScope null
        var best = first
        val deadline = TimeSource.Monotonic.markNow() + SETTLE_TIME
        while (best.accuracyMeters > GOOD_ENOUGH_ACCURACY_METERS) {
            val remaining = -deadline.elapsedNow()
            if (!remaining.isPositive()) break
            val next = withTimeoutOrNull(remaining) { fixes.nextFix() } ?: break
            if (next.accuracyMeters < best.accuracyMeters) best = next
        }
        best.point
    } finally {
        // Stops the platform's updates (the flow's awaitClose runs).
        fixes.cancel()
    }
}

private suspend fun ReceiveChannel<LocationFix>.nextFix(): LocationFix? = receiveCatching().getOrNull()
