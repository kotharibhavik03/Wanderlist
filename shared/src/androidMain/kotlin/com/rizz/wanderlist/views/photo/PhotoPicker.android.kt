package com.rizz.wanderlist.views.photo

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

@Composable
actual fun rememberPhotoPicker(multiple: Boolean, onPicked: (List<String>) -> Unit): PhotoPicker {
    val currentOnPicked by rememberUpdatedState(onPicked)
    // Android's photo picker (or the system file picker on old devices). The uris it returns
    // stay readable by this app while it's running, long enough to compress them.
    // One photo uses the single picker: the multi-select one always allows at least 2.
    val single = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        currentOnPicked(listOfNotNull(uri?.toString()))
    }
    // No limit of our own: the system's (MediaStore.getPickImagesMaxLimit, usually 100) applies.
    val several = rememberLauncherForActivityResult(PickMultipleVisualMedia()) { uris ->
        currentOnPicked(uris.map { it.toString() })
    }
    return remember(single, several, multiple) {
        PhotoPicker {
            val request = PickVisualMediaRequest(PickVisualMedia.ImageOnly)
            if (multiple) several.launch(request) else single.launch(request)
        }
    }
}
