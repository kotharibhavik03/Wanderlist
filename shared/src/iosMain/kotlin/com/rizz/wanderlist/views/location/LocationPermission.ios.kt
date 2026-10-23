package com.rizz.wanderlist.views.location

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.darwin.NSObject

@Composable
actual fun rememberLocationPermissionRequester(): LocationPermissionRequester =
    remember { IosLocationPermissionRequester() }

private class IosLocationPermissionRequester : LocationPermissionRequester {
    private val manager = CLLocationManager()

    // CLLocationManager holds its delegate weakly; this keeps it alive until the user answers.
    private var delegate: CLLocationManagerDelegateProtocol? = null

    override suspend fun request(): Boolean {
        val status = manager.authorizationStatus
        // iOS only shows the prompt once; after that the answer can only change in Settings.
        if (status != kCLAuthorizationStatusNotDetermined) return status.isGranted()

        return suspendCancellableCoroutine { continuation ->
            val answer = object : NSObject(), CLLocationManagerDelegateProtocol {
                // Also called right after the delegate is set, while still undetermined: wait for the answer.
                override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
                    val newStatus = manager.authorizationStatus
                    if (newStatus != kCLAuthorizationStatusNotDetermined && continuation.isActive) {
                        delegate = null
                        continuation.resume(newStatus.isGranted())
                    }
                }
            }
            delegate = answer
            manager.delegate = answer
            manager.requestWhenInUseAuthorization()
            continuation.invokeOnCancellation { delegate = null }
        }
    }
}

private fun CLAuthorizationStatus.isGranted() =
    this == kCLAuthorizationStatusAuthorizedWhenInUse || this == kCLAuthorizationStatusAuthorizedAlways
