package com.rizz.wanderlist.data.storage

import dev.gitlive.firebase.storage.File
import platform.Foundation.NSURL

internal actual fun storageFile(path: String): File = File(NSURL.fileURLWithPath(path))
