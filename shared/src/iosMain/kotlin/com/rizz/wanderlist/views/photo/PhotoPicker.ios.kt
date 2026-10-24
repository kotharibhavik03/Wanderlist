package com.rizz.wanderlist.views.photo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import com.rizz.wanderlist.data.photo.PhotoFiles
import kotlin.coroutines.resume
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSFileManager
import platform.Foundation.NSItemProvider
import platform.Foundation.NSURL
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject

@Composable
actual fun rememberPhotoPicker(multiple: Boolean, onPicked: (List<String>) -> Unit): PhotoPicker {
    val currentOnPicked by rememberUpdatedState(onPicked)
    val viewController = LocalUIViewController.current
    val scope = rememberCoroutineScope()
    return remember(viewController, multiple) {
        IosPhotoPicker(viewController, multiple, scope) { currentOnPicked(it) }
    }
}

/** Apple's PHPicker: runs outside the app, so it needs no photo library permission. */
private class IosPhotoPicker(
    private val host: UIViewController,
    private val multiple: Boolean,
    private val scope: CoroutineScope,
    private val onPicked: (List<String>) -> Unit
) : PhotoPicker {

    // The picker holds its delegate weakly; this keeps it alive until the user is done.
    private var delegate: PHPickerViewControllerDelegateProtocol? = null

    override fun launch() {
        val configuration = PHPickerConfiguration().apply {
            // 0 means no limit.
            selectionLimit = if (multiple) 0L else 1L
            filter = PHPickerFilter.imagesFilter
        }
        val picker = PHPickerViewController(configuration)
        val delegate = object : NSObject(), PHPickerViewControllerDelegateProtocol {
            override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
                picker.dismissViewControllerAnimated(true, completion = null)
                this@IosPhotoPicker.delegate = null
                val providers = didFinishPicking.mapNotNull { (it as? PHPickerResult)?.itemProvider }
                // One at a time, keeping the order the user picked them in.
                scope.launch { onPicked(providers.mapNotNull { copyOriginal(it) }) }
            }
        }
        this.delegate = delegate
        picker.delegate = delegate
        host.topmostPresented().presentViewController(picker, animated = true, completion = null)
    }
}

/**
 * Copies the original photo (HEIC, JPEG, ...) to the app's temporary folder and returns its
 * path, or null if it couldn't be loaded. The picker deletes its own file when the callback
 * returns, so the copy has to happen inside it.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalUuidApi::class)
private suspend fun copyOriginal(provider: NSItemProvider): String? =
    suspendCancellableCoroutine { continuation ->
        provider.loadFileRepresentationForTypeIdentifier("public.image") { url: NSURL?, _ ->
            val copy = url?.let {
                val extension = it.pathExtension?.takeIf { ext -> ext.isNotEmpty() } ?: "jpg"
                val destination = PhotoFiles.pickedDirectory + "/${Uuid.random().toHexString()}.$extension"
                val copied = NSFileManager.defaultManager.copyItemAtURL(
                    it,
                    toURL = NSURL.fileURLWithPath(destination),
                    error = null
                )
                destination.takeIf { copied }
            }
            continuation.resume(copy)
        }
    }

/** The view controller currently on screen; presenting from anything else is ignored by UIKit. */
private fun UIViewController.topmostPresented(): UIViewController {
    var top = this
    while (true) top = top.presentedViewController ?: return top
}
