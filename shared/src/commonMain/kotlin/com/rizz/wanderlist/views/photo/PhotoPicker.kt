package com.rizz.wanderlist.views.photo

import androidx.compose.runtime.Composable

/** Opens the system photo picker. No permission is needed on either platform. */
fun interface PhotoPicker {
    fun launch()
}

/**
 * The platform's photo picker, letting the user choose up to [maxItems] photos. [onPicked]
 * receives what they chose (nothing if they cancelled): Android content uris, or on iOS paths
 * of copies in a temporary folder. Either can be handed to the app's PhotoCompressor.
 */
@Composable
expect fun rememberPhotoPicker(maxItems: Int, onPicked: (List<String>) -> Unit): PhotoPicker
