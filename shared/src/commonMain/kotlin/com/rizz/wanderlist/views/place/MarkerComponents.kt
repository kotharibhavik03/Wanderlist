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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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

/** The circular map marker: an emoji on the collection's colours. */
@Composable
internal fun MarkerBadge(emoji: String, brush: Brush, size: Dp = 44.dp) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(brush),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = (size.value * 0.5f).sp)
    }
}

internal fun markerEmoji(key: String): String =
    markerIcons.firstOrNull { it.first == key }?.second ?: "📍"

/** One marker renderer, reused by the list now and by the map later. */
@Composable
internal fun PlaceMarker(style: MarkerStyle, collection: PlaceCollection?, size: Dp = 44.dp) {
    val brush = collectionBrush(collection?.colors.orEmpty(), MaterialTheme.colorScheme.primary)
    when (style) {
        MarkerStyle.CollectionDefault -> MarkerBadge(collection?.icon ?: "📍", brush, size)
        is MarkerStyle.Icon -> MarkerBadge(markerEmoji(style.iconKey), brush, size)
        is MarkerStyle.Photo -> Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Image, contentDescription = null) }
    }
}