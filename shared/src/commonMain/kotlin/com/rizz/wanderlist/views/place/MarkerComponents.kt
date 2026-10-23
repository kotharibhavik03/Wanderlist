package com.rizz.wanderlist.views.place

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.rizz.wanderlist.domain.model.MarkerStyle
import com.rizz.wanderlist.domain.model.PlaceCollection

internal val markerIcons = listOf(
    "location" to "📍",
    "camera" to "📷",
    "nature" to "🌲",
    "star" to "⭐",
    "food" to "🍴",
    "heart" to "❤️",
    "mountain" to "⛰️"
)

/** A collection can have several colours: one is solid, two or more become a gradient. */
internal fun collectionBrush(colors: List<Long>, fallback: Color): Brush {
    val list = colors.map { Color(it) }
    return when {
        list.isEmpty() -> SolidColor(fallback)
        list.size == 1 -> SolidColor(list.first())
        else -> Brush.linearGradient(list)
    }
}

/**
 * The circular map marker: an emoji on the collection's colours. Decorative: screen readers
 * skip the emoji ("snow-capped mountain"), because a name is always shown next to it.
 */
@Composable
internal fun MarkerBadge(emoji: String, brush: Brush, size: Dp = 44.dp) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(brush),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = (size.value * 0.5f).sp, modifier = Modifier.clearAndSetSemantics { })
    }
}

internal fun markerEmoji(key: String): String =
    markerIcons.firstOrNull { it.first == key }?.second ?: "📍"

/**
 * A place's marker, used everywhere a place is shown (lists, sheet, detail, map).
 * A photo marker draws [photoUrl] (pass `place.markerPhotoUrl`), or [loadedPhoto] when the
 * caller already loaded it (the map does: its markers are rendered to a bitmap once). Without
 * either it falls back to the collection's badge.
 */
@Composable
internal fun PlaceMarker(
    style: MarkerStyle,
    collection: PlaceCollection?,
    photoUrl: String?,
    size: Dp = 44.dp,
    loadedPhoto: Painter? = null
) {
    val brush = collectionBrush(collection?.colors.orEmpty(), MaterialTheme.colorScheme.primary)
    val photoModifier = Modifier
        .size(size)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceVariant)
    when {
        style is MarkerStyle.Photo && loadedPhoto != null ->
            Image(loadedPhoto, contentDescription = null, contentScale = ContentScale.Crop, modifier = photoModifier)
        style is MarkerStyle.Photo && photoUrl != null ->
            AsyncImage(photoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = photoModifier)
        style is MarkerStyle.Icon -> MarkerBadge(markerEmoji(style.iconKey), brush, size)
        else -> MarkerBadge(collection?.icon ?: "📍", brush, size)
    }
}