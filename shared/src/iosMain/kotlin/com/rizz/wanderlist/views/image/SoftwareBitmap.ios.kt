package com.rizz.wanderlist.views.image

import coil3.request.ImageRequest

// iOS images are ordinary Skia bitmaps, drawable on any canvas.
internal actual fun ImageRequest.Builder.softwareBitmap(): ImageRequest.Builder = this
