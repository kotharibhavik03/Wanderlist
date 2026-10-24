package com.rizz.wanderlist.data.location

import com.rizz.wanderlist.domain.model.GeoPoint
import com.rizz.wanderlist.domain.repository.LocationService
import kotlin.time.Duration.Companion.seconds
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLErrorLocationUnknown
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSError
import platform.Foundation.timeIntervalSinceNow
import platform.darwin.NSObject

/**
 * Apple's CLLocationManager. Uses the position the device already has when it's recent,
 * otherwise the first live fix that's good enough (see [firstGoodFix]). Not `requestLocation()`:
 * it keeps refining for up to about ten seconds before it answers.
 */
class IosLocationService : LocationService {

    override fun hasPermission(): Boolean {
        val status = CLLocationManager().authorizationStatus
        return status == kCLAuthorizationStatusAuthorizedWhenInUse || status == kCLAuthorizationStatusAuthorizedAlways
    }

    override suspend fun currentLocation(): GeoPoint? {
        if (!hasPermission()) return null
        return fixes().firstGoodFix()
    }

    /** The recent cached position (if any), then live updates until the collector stops. */
    private fun fixes(): Flow<LocationFix> = callbackFlow {
        val manager = CLLocationManager()
        // The last position the app received (the map's blue dot keeps it fresh).
        manager.location?.takeIf { it.age() <= MAX_CACHED_FIX_AGE }?.let { trySend(it.toFix()) }

        val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                didUpdateLocations.filterIsInstance<CLLocation>().forEach { trySend(it.toFix()) }
            }

            // E.g. location services switched off: end the stream, which reads as "no position".
            // "Location unknown" only means no fix yet; the manager keeps trying.
            override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
                if (didFailWithError.code != kCLErrorLocationUnknown) close()
            }
        }
        // The manager holds its delegate weakly; this flow keeps both alive until awaitClose.
        manager.delegate = delegate
        manager.desiredAccuracy = kCLLocationAccuracyBest
        // Reports a rough fix within about a second, then better ones as they come.
        manager.startUpdatingLocation()
        awaitClose {
            manager.stopUpdatingLocation()
            manager.delegate = null
        }
    }

    private fun CLLocation.age() = (-timestamp.timeIntervalSinceNow).seconds

    @OptIn(ExperimentalForeignApi::class)
    private fun CLLocation.toFix() = LocationFix(
        point = coordinate.useContents { GeoPoint(latitude, longitude) },
        // A negative accuracy means the position is invalid; treat it as rough.
        accuracyMeters = horizontalAccuracy.takeIf { it >= 0 } ?: Double.MAX_VALUE
    )
}
