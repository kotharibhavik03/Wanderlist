package com.rizz.wanderlist.views.photo

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import kotlin.math.min

private const val MaxZoom = 4f
private const val DoubleTapZoom = 2.5f

/**
 * A full-screen photo that zooms: pinch with two fingers (around the point between them, panning
 * at the same time), or double-tap to zoom in at that spot and back out. While zoomed, one finger
 * pans, and the photo can't be dragged past its edges.
 *
 * At normal size a one-finger drag is left alone, so the pager around it can swipe to the next
 * photo; [onZoomedChange] tells it when to stop swiping. The zoom resets when [isCurrentPage]
 * turns false (the user swiped away).
 *
 * [preview] (a smaller copy of the same photo, usually already cached) shows at once, so the
 * viewer never opens on black; [model] fades in over it once downloaded.
 */
@Composable
internal fun ZoomableImage(
    model: Any,
    preview: Any?,
    contentDescription: String,
    isCurrentPage: Boolean,
    onZoomedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    // Translation of the zoomed photo from its centred position, in pixels.
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    // The photo's own size once loaded; it's drawn fitted inside [size], often with bars.
    var photoSize by remember { mutableStateOf(Size.Unspecified) }
    // Once the full photo is shown, the preview underneath stops being drawn.
    var isLoaded by remember { mutableStateOf(false) }
    val currentOnZoomedChange by rememberUpdatedState(onZoomedChange)

    // The furthest the photo may move at [atScale] before an edge of the photo (not of the
    // screen) comes into view. Along a side where the zoomed photo is still smaller than the
    // screen, it stays centred.
    fun clamp(value: Offset, atScale: Float): Offset {
        val fitted = fittedSize(photoSize, size)
        val maxX = ((fitted.width * atScale - size.width) / 2f).coerceAtLeast(0f)
        val maxY = ((fitted.height * atScale - size.height) / 2f).coerceAtLeast(0f)
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    // Zooms to [newScale] keeping the screen point [focus] (relative to the centre) under the finger.
    fun zoomTo(newScale: Float, focus: Offset, pan: Offset = Offset.Zero) {
        val target = newScale.coerceIn(1f, MaxZoom)
        val factor = target / scale
        offset = clamp(focus - (focus - offset) * factor + pan, target)
        scale = target
    }

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    LaunchedEffect(scale > 1f) { currentOnZoomedChange(scale > 1f) }
    LaunchedEffect(isCurrentPage) { if (!isCurrentPage) reset() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        if (scale > 1f) reset() else zoomTo(DoubleTapZoom, focus = tap - center())
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        // Two fingers always zoom; one finger only pans a photo that's zoomed in.
                        // Otherwise the event is left for the pager. The event of the last finger
                        // lifting has no fingers down and no centroid (NaN): skip it.
                        val centroid = event.calculateCentroid(useCurrent = true)
                        if (fingers > 0 && centroid.isSpecified && (fingers > 1 || scale > 1f)) {
                            zoomTo(scale * event.calculateZoom(), centroid - center(), event.calculatePan())
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    // Pinched (almost) back to normal: snap to it, so swiping works again.
                    if (scale < 1.05f) reset()
                }
            }
    ) {
        // Both images zoom and pan together; they're the same photo, so they line up exactly.
        val zoomed = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
        if (preview != null && !isLoaded) {
            AsyncImage(
                model = preview,
                // The full photo below carries the description; this is only its stand-in.
                contentDescription = null,
                contentScale = ContentScale.Fit,
                // Same shape as the full photo, so zoom limits work before it arrives.
                onSuccess = { state -> if (photoSize.isUnspecified) photoSize = state.painter.intrinsicSize },
                modifier = zoomed
            )
        }
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            onSuccess = { state ->
                photoSize = state.painter.intrinsicSize
                isLoaded = true
            },
            modifier = zoomed
        )
    }
}

/** How big a photo of [photo] size is drawn when fitted inside [box]; the box itself if unknown. */
private fun fittedSize(photo: Size, box: IntSize): Size {
    if (photo.isUnspecified || photo.width <= 0f || photo.height <= 0f) {
        return Size(box.width.toFloat(), box.height.toFloat())
    }
    val fit = min(box.width / photo.width, box.height / photo.height)
    return Size(photo.width * fit, photo.height * fit)
}

/** The centre of the box, as an offset from its top left. */
private fun PointerInputScope.center() =
    Offset(size.width / 2f, size.height / 2f)
