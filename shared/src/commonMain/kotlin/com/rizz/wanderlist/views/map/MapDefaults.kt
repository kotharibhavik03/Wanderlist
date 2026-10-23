package com.rizz.wanderlist.views.map

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
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
 * Catches taps on Google's logo, which would otherwise open the Google Maps app. Draw it over the
 * map, filling the same area, with the map's own [contentPadding]: the map keeps its logo in the
 * bottom-start corner of the padded area, so this follows it. The logo itself stays visible and
 * unchanged (the Maps terms require that); only taps on it do nothing. Compose doesn't pass
 * touches through to what's underneath, so the map never sees them.
 */
@Composable
internal fun GoogleLogoTapBlocker(contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(contentPadding)) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(width = LogoTapAreaWidth, height = LogoTapAreaHeight)
                .pointerInput(Unit) { detectTapGestures { } }
        )
    }
}

/** Covers the logo and its margin on both platforms (Android's is a little larger than iOS's). */
private val LogoTapAreaWidth = 110.dp
private val LogoTapAreaHeight = 72.dp

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
