@file:OptIn(ExperimentalMaterial3Api::class)

package com.rizz.wanderlist.views.place

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rizz.wanderlist.util.pluralize
import com.rizz.wanderlist.views.photo.DraftPhotoThumbnail

/**
 * The photos picked to add to a place, before they're uploaded: remove one, or lock it to keep
 * it private, then add them all. While uploading it shows progress and can't be dismissed (the
 * upload would be cancelled half-way).
 */
@Composable
internal fun AddPhotosSheet(
    draft: PhotoDraft,
    onPickMore: () -> Unit,
    onRemove: (photoId: String) -> Unit,
    onTogglePrivate: (photoId: String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    // Read inside the sheet's veto callback, which is created once.
    val isUploading by rememberUpdatedState(draft.isUploading)
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        // Always fully open: there's no smaller resting position to fall back to mid-upload.
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        confirmValueChange = { it != SheetValue.Hidden || !isUploading }
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Add photos", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Lock a photo to keep it visible only to you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                draft.photos.forEach { photo ->
                    DraftPhotoThumbnail(
                        photo = photo,
                        onRemove = { onRemove(photo.id) },
                        onTogglePrivate = { onTogglePrivate(photo.id) },
                        enabled = !draft.isUploading
                    )
                }
                if (!draft.isUploading) {
                    OutlinedCard(onClick = onPickMore, modifier = Modifier.size(88.dp)) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                            Text("Add", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            draft.upload?.let { upload ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Uploading photos: ${upload.uploaded} of ${upload.total}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LinearProgressIndicator(
                        progress = { upload.uploaded.toFloat() / upload.total },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (draft.isPreparing) {
                Text(
                    "Getting your photos ready…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismiss, enabled = !draft.isUploading) { Text("Cancel") }
                Button(onClick = onConfirm, enabled = draft.canUpload) {
                    Text(
                        if (draft.photos.isEmpty()) "Add photos"
                        else "Add ${pluralize(draft.photos.size, "photo")}"
                    )
                }
            }
        }
    }
}
