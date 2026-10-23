package com.rizz.wanderlist.views.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlin.math.roundToInt

/**
 * A person's photo in a circle, over their initials: while the photo loads, if it fails, or
 * when there is none, the initials show. Decorative: the name is always written next to it.
 */
@Composable
internal fun UserAvatar(name: String?, email: String?, photoUrl: String?, size: Dp, modifier: Modifier = Modifier) {
    val sizePx = with(LocalDensity.current) { size.toPx() }.roundToInt()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center
    ) {
        Text(
            initialsOf(name, email),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontSize = (size.value * 0.38f).sp,
            fontWeight = FontWeight.SemiBold
        )
        if (photoUrl != null) {
            AsyncImage(
                model = sizedPhotoUrl(photoUrl, sizePx),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** "Ada Lovelace" → "AL", "Ada" → "A", no name → first letter of the email, else "?". */
internal fun initialsOf(name: String?, email: String?): String {
    val fromName = name.orEmpty()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
    return fromName.ifEmpty { email?.firstOrNull()?.uppercase() ?: "?" }
}

/** Google account photos end in a size, e.g. "=s96-c" (96 px, cropped). */
private val GooglePhotoSize = Regex("=s\\d+-c$")

/**
 * Google account photo URLs are 96 px by default, blurry in a large avatar: ask for the size
 * drawn instead. Other URLs (photos people uploaded) are returned as they are.
 */
private fun sizedPhotoUrl(url: String, sizePx: Int): String =
    if ("googleusercontent.com" in url && GooglePhotoSize.containsMatchIn(url)) {
        url.replace(GooglePhotoSize, "=s$sizePx-c")
    } else {
        url
    }
