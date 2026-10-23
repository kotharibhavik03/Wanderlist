package com.rizz.wanderlist.views.photo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.rizz.wanderlist.domain.model.PlacePhoto

/**
 * A photo in the viewer, with an optional caption (e.g. the place it belongs to). [canDelete]
 * shows a delete button on it.
 */
internal data class ViewerPhoto(val photo: PlacePhoto, val caption: String? = null, val canDelete: Boolean = false)

/**
 * Full-screen photos on black: swipe between them, close with the button or Back. Shows
 * "2 of 5" and a lock on private photos, and a delete button (after a confirmation) on photos
 * the user may delete. Closes itself if the photos disappear (e.g. the last one was deleted).
 */
@Composable
internal fun PhotoViewer(
    photos: List<ViewerPhoto>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onDelete: (PlacePhoto) -> Unit = {},
    /** Photos being deleted: their button shows a spinner instead. */
    deletingPhotoIds: Set<String> = emptySet()
) {
    LaunchedEffect(photos.isEmpty()) { if (photos.isEmpty()) onDismiss() }
    if (photos.isEmpty()) return
    var confirmingDelete by remember { mutableStateOf<PlacePhoto?>(null) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val pagerState = rememberPagerState(initialPage = initialIndex.coerceIn(photos.indices)) { photos.size }
        val current = photos.getOrNull(pagerState.currentPage) ?: photos.last()

        // A photo viewer is black in both themes, so its controls are always white.
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                state = pagerState,
                key = { photos[it].photo.id },
                modifier = Modifier.fillMaxSize()
            ) { page ->
                AsyncImage(
                    model = photos[page].photo.url,
                    contentDescription = photos[page].caption ?: "Photo ${page + 1} of ${photos.size}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
                if (photos.size > 1) {
                    Text(
                        "${pagerState.currentPage + 1} of ${photos.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Spacer(Modifier.weight(1f))
                if (current.photo.isPrivate) {
                    Row(
                        modifier = Modifier.padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text("Only you", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (current.canDelete) {
                    if (current.photo.id in deletingPhotoIds) {
                        // Same footprint as the button, so the row doesn't shift.
                        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        }
                    } else {
                        IconButton(onClick = { confirmingDelete = current.photo }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete photo", tint = Color.White)
                        }
                    }
                }
            }

            current.caption?.let { caption ->
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(16.dp)
                ) {
                    Text(
                        caption,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        confirmingDelete?.let { photo ->
            AlertDialog(
                onDismissRequest = { confirmingDelete = null },
                title = { Text("Delete this photo?") },
                text = { Text("It will be removed from this place for everyone. This can't be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmingDelete = null
                        onDelete(photo)
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { confirmingDelete = null }) { Text("Cancel") } }
            )
        }
    }
}
