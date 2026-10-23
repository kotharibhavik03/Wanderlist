package com.rizz.wanderlist.views.image

import coil3.request.ImageRequest
import coil3.request.allowHardware

// Coil decodes to hardware bitmaps by default, which only a hardware canvas can draw.
internal actual fun ImageRequest.Builder.softwareBitmap(): ImageRequest.Builder = allowHardware(false)
