package com.rizz.wanderlist.data.location

import com.rizz.wanderlist.domain.model.GeoPoint
import com.rizz.wanderlist.domain.repository.LocationService
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSError
import platform.darwin.NSObject

/** Apple's CLLocationManager: a single fresh fix per request. */
class IosLocationService : LocationService {

    // CLLocationManager holds its delegate weakly: requests in flight keep theirs here.
    private val inFlight = mutableSetOf<Any>()

    override fun hasPermission(): Boolean {
        val status = CLLocationManager().authorizationStatus
        return status == kCLAuthorizationStatusAuthorizedWhenInUse || status == kCLAuthorizationStatusAuthorizedAlways
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun currentLocation(): GeoPoint? {
        if (!hasPermission()) return null
        return suspendCancellableCoroutine { continuation ->
            val manager = CLLocationManager()
            lateinit var request: Any

            fun finish(point: GeoPoint?) {
                inFlight.remove(request)
                manager.delegate = null
                if (continuation.isActive) continuation.resume(point)
            }

            val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
                override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
                    val location = didUpdateLocations.lastOrNull() as? CLLocation
                    finish(location?.coordinate?.useContents { GeoPoint(latitude, longitude) })
                }

                // E.g. location services switched off: report "no position", not a crash.
                override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
                    finish(null)
                }
            }
            request = manager to delegate
            inFlight.add(request)
            manager.delegate = delegate
            manager.desiredAccuracy = kCLLocationAccuracyBest
            manager.requestLocation()
            continuation.invokeOnCancellation {
                manager.stopUpdatingLocation()
                inFlight.remove(request)
            }
        }
    }
}
