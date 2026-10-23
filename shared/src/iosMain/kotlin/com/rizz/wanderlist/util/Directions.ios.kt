package com.rizz.wanderlist.util

// Apple Maps is built in. The Google Maps link opens its app if installed, otherwise Safari.
actual val directionsApps: List<DirectionsApp> = listOf(DirectionsApp.APPLE_MAPS, DirectionsApp.GOOGLE_MAPS)
