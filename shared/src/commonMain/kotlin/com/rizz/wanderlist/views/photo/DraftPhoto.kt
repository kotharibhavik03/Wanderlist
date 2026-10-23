package com.rizz.wanderlist.views.photo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * A photo in a form: either picked and not uploaded yet, or already on the place being edited.
 *
 * New: [localPath] is null while it's being compressed, then it's the small JPEG that's shown
 * and uploaded. [id] becomes the saved photo's id, so a photo marker chosen before saving still
 * points at it afterwards.
 *
 * Already saved: [url] is where it's stored. [canRemove] and [canChangePrivacy] say what this
 * user may do with it (someone else's photo can't be locked, and only the owner may remove it).
 */
data class DraftPhoto(
    val id: String,
    val localPath: String? = null,
    val isPrivate: Boolean = false,
    val url: String? = null,
    val canRemove: Boolean = true,
    val canChangePrivacy: Boolean = true
) {
    val isSaved: Boolean get() = url != null
    val isReady: Boolean get() = isSaved || localPath != null

    /** What to draw: the stored photo, or the compressed file. Null while compressing. */
    val image: String? get() = url ?: localPath
}

/** How far the photo uploads have got while saving. */
data class PhotoUploadProgress(val uploaded: Int, val total: Int)

/**
 * A form photo's thumbnail: a spinner while it's compressed, then the photo, with a remove
 * button and a lock to keep it private (each only when allowed). "Shared" / "Only me" /
 * "Someone else's" underneath.
 */
@Composable
internal fun DraftPhotoThumbnail(
    photo: DraftPhoto,
    onRemove: () -> Unit,
    onTogglePrivate: () -> Unit,
    enabled: Boolean = true
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (photo.image == null) {
                // Still being compressed.
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                AsyncImage(
                    model = photo.image,
                    contentDescription = if (photo.isPrivate) "Private photo" else "Shared photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (photo.canRemove) {
                ThumbnailButton(
                    onClick = onRemove,
                    enabled = enabled,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Remove photo", modifier = Modifier.size(18.dp))
                }
            }
            if (photo.canChangePrivacy) {
                ThumbnailButton(
                    onClick = onTogglePrivate,
                    enabled = enabled,
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    Icon(
                        imageVector = if (photo.isPrivate) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = if (photo.isPrivate) "Private. Tap to share" else "Shared. Tap to make private",
                        tint = if (photo.isPrivate) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Text(
            text = when {
                !photo.canChangePrivacy -> "Someone else's"
                photo.isPrivate -> "Only me"
                else -> "Shared"
            },
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/** A 36dp icon button on a small translucent disc, so it stays visible over any photo. */
@Composable
private fun ThumbnailButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier,
    icon: @Composable () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier.size(36.dp)) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) { icon() }
    }
}
