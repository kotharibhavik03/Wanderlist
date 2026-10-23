package com.rizz.wanderlist.util

import kotlin.math.abs
import kotlin.math.roundToLong

/** A maps app that can give directions. Opened through a plain https link, so no paid API is involved. */
enum class DirectionsApp(val label: String) {
    GOOGLE_MAPS("Google Maps"),
    APPLE_MAPS("Apple Maps")
}

/** The apps to offer on this platform, preferred first. */
expect val directionsApps: List<DirectionsApp>

/** A link that opens [app] with directions to the given point. */
fun directionsUrl(app: DirectionsApp, latitude: Double, longitude: Double): String {
    val destination = "${latitude.toUrlCoordinate()},${longitude.toUrlCoordinate()}"
    return when (app) {
        DirectionsApp.GOOGLE_MAPS -> "https://www.google.com/maps/dir/?api=1&destination=$destination"
        DirectionsApp.APPLE_MAPS -> "https://maps.apple.com/?daddr=$destination"
    }
}

/**
 * Six decimal places (about 10 cm), never scientific notation.
 * Double.toString() can print 1.0E-4, which map links don't understand.
 */
private fun Double.toUrlCoordinate(): String {
    val micro = (this * 1_000_000).roundToLong()
    val sign = if (micro < 0) "-" else ""
    val whole = abs(micro) / 1_000_000
    val fraction = (abs(micro) % 1_000_000).toString().padStart(6, '0')
    return "$sign$whole.$fraction"
}
