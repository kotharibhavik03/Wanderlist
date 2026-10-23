package com.rizz.wanderlist.views.place

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rizz.wanderlist.domain.model.PlaceStatus
import com.rizz.wanderlist.views.theme.statusColors

internal fun PlaceStatus.label(): String = when (this) {
    PlaceStatus.VISITED -> "Visited"
    PlaceStatus.WISHLIST -> "Want to visit"
}

/** "Visited" in green or "Want to visit" in amber. The text carries the meaning, not just the colour. */
@Composable
internal fun PlaceStatusBadge(status: PlaceStatus) {
    val colors = MaterialTheme.statusColors
    when (status) {
        PlaceStatus.VISITED -> Badge(status.label(), colors.visitedContainer, colors.onVisitedContainer)
        PlaceStatus.WISHLIST -> Badge(status.label(), colors.wishlistContainer, colors.onWishlistContainer)
    }
}

/** A small fully rounded label, e.g. a status or a role. */
@Composable
internal fun Badge(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Surface(shape = CircleShape, color = containerColor, contentColor = contentColor) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
