package com.rizz.wanderlist.data.storage

import android.net.Uri
import com.google.firebase.storage.StorageException
import dev.gitlive.firebase.storage.Data
import dev.gitlive.firebase.storage.File
import dev.gitlive.firebase.storage.FirebaseStorageException

internal actual fun storageFile(path: String): File = File(Uri.fromFile(java.io.File(path)))

internal actual fun Data.toByteArray(): ByteArray = data

// On Android, FirebaseStorageException is the SDK's own StorageException, with its error code.
internal actual fun FirebaseStorageException.isObjectNotFound(): Boolean =
    errorCode == StorageException.ERROR_OBJECT_NOT_FOUND
