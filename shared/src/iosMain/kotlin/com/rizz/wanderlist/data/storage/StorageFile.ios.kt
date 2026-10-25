package com.rizz.wanderlist.data.storage

import dev.gitlive.firebase.storage.Data
import dev.gitlive.firebase.storage.File
import dev.gitlive.firebase.storage.FirebaseStorageException
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSURL
import platform.posix.memcpy

internal actual fun storageFile(path: String): File = File(NSURL.fileURLWithPath(path))

@OptIn(ExperimentalForeignApi::class)
internal actual fun Data.toByteArray(): ByteArray {
    val bytes = ByteArray(data.length.toInt())
    if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
    return bytes
}

/**
 * The iOS wrapper only keeps the NSError's description ("Error Domain=FIRStorageErrorDomain
 * Code=-13010 ..."), so the "object not found" code is read from the text.
 */
internal actual fun FirebaseStorageException.isObjectNotFound(): Boolean =
    message?.contains("Code=$OBJECT_NOT_FOUND") == true

/** FIRStorageErrorCodeObjectNotFound. */
private const val OBJECT_NOT_FOUND = -13010
