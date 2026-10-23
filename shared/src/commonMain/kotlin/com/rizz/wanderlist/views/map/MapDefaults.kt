package com.rizz.wanderlist.views.map

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.rizz.wanderlist.views.theme.DarkMapStyleJson
import com.rizz.wanderlist.views.theme.isDark
import eu.buney.maps.CameraMoveStartedReason
import eu.buney.maps.CameraPosition
import eu.buney.maps.CameraPositionState
import eu.buney.maps.CameraUpdate
import eu.buney.maps.LatLng
import eu.buney.maps.MapProperties
import eu.buney.maps.MapStyleOptions
import eu.buney.maps.MapUiSettings
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Shared setup for every map in the app (Home, the location picker). */

/** Zoomed out far enough to show most of the world when there's nothing to frame yet. */
internal val WorldView = CameraPosition(LatLng(20.0, 0.0), 1.5f, 0f, 0f)

/**
 * The map's look: Google's default in light mode, the Night style in dark mode (follows the app
 * theme). [showMyLocation] draws the blue "you are here" dot; only pass true once location access
 * is granted (Android refuses to enable it otherwise).
 */
@Composable
internal fun rememberMapProperties(showMyLocation: Boolean = false): MapProperties {
    val isDark = MaterialTheme.isDark
    return remember(isDark, showMyLocation) {
        MapProperties(
            isMyLocationEnabled = showMyLocation,
            mapStyleOptions = if (isDark) MapStyleOptions.fromJson(DarkMapStyleJson) else null
        )
    }
}

/** No toolbar, zoom buttons or built-in location button: the app draws its own controls. */
internal val AppMapUiSettings = MapUiSettings(
    mapToolbarEnabled = false,
    zoomControlsEnabled = false,
    myLocationButtonEnabled = false
)

/**
 * Animates to [update]. If something other than the user's finger interrupts the animation (for
 * example the keyboard closing as a search result is picked), jumps there instead, so the camera
 * never stops halfway. A user dragging the map is left alone.
 */
internal suspend fun CameraPositionState.animateOrJump(update: CameraUpdate) {
    try {
        animate(update)
    } catch (e: CancellationException) {
        // Our own coroutine was cancelled (e.g. a newer selection): stop here.
        currentCoroutineContext().ensureActive()
        if (cameraMoveStartedReason != CameraMoveStartedReason.GESTURE) move(update)
    }
}
