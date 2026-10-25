package com.rizz.wanderlist.views.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.asPainter
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.rizz.wanderlist.domain.model.PhotoImage

/**
 * Loads [photo]'s thumbnail at [size] and returns it once it's ready; null while loading, on
 * failure, or without a photo. For images drawn off screen, such as map markers (rendered to a
 * bitmap once), where an AsyncImage would be captured before it had loaded. The image is a
 * software bitmap: map markers are drawn on a software canvas, which crashes on hardware ones.
 */
@Composable
internal fun rememberLoadedPhoto(photo: PhotoImage?, size: Dp): Painter? {
    val context = LocalPlatformContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    var painter by remember(photo) { mutableStateOf<Painter?>(null) }
    LaunchedEffect(photo, sizePx) {
        if (photo == null) return@LaunchedEffect
        val request = ImageRequest.Builder(context).data(photo.thumbnail).size(sizePx).softwareBitmap().build()
        val result = SingletonImageLoader.get(context).execute(request)
        painter = (result as? SuccessResult)?.image?.asPainter(context)
    }
    return painter
}

/** Asks for a bitmap any canvas can draw (Android's default hardware bitmaps can't go on a software one). */
internal expect fun ImageRequest.Builder.softwareBitmap(): ImageRequest.Builder
