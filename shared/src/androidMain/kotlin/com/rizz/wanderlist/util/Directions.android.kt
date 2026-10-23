package com.rizz.wanderlist.util

// Apple Maps has no Android app; its link would only open a web page.
actual val directionsApps: List<DirectionsApp> = listOf(DirectionsApp.GOOGLE_MAPS)
