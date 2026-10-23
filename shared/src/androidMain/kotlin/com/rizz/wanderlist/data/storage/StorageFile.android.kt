package com.rizz.wanderlist.data.storage

import android.net.Uri
import dev.gitlive.firebase.storage.File

internal actual fun storageFile(path: String): File = File(Uri.fromFile(java.io.File(path)))
