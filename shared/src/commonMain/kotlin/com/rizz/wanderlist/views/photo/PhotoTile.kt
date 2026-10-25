package com.rizz.wanderlist.views.photo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.rizz.wanderlist.domain.model.PlacePhoto
import com.rizz.wanderlist.views.image.thumbnail

/** A photo thumbnail, cropped to fill, with a lock when it's private. Tap to open it. */
@Composable
internal fun PhotoTile(
    photo: PlacePhoto,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClickLabel = "Open photo", onClick = onClick)
            .semantics { contentDescription = if (photo.isPrivate) "$description, only you can see it" else description }
    ) {
        AsyncImage(
            model = photo.image.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (photo.isPrivate) {
            PrivateBadge(Modifier.align(Alignment.BottomStart).padding(6.dp))
        }
    }
}

/** The lock on a private photo: on a small disc so it reads over any image. */
@Composable
internal fun PrivateBadge(modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        modifier = modifier
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(4.dp).size(14.dp)
        )
    }
}
