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
actual fun rememberPhotoPicker(maxItems: Int, onPicked: (List<String>) -> Unit): PhotoPicker {
    val currentOnPicked by rememberUpdatedState(onPicked)
    // Android's photo picker (or the system file picker on old devices). The uris it returns
    // stay readable by this app while it's running, long enough to compress them.
    // The multi-select picker needs a limit of at least 2, so one photo uses the single picker.
    val single = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        currentOnPicked(listOfNotNull(uri?.toString()))
    }
    val multipleContract = remember(maxItems) { PickMultipleVisualMedia(maxItems.coerceAtLeast(2)) }
    val multiple = rememberLauncherForActivityResult(multipleContract) { uris ->
        currentOnPicked(uris.map { it.toString() })
    }
    return remember(single, multiple, maxItems) {
        PhotoPicker {
            val request = PickVisualMediaRequest(PickVisualMedia.ImageOnly)
            if (maxItems <= 1) single.launch(request) else multiple.launch(request)
        }
    }
}
