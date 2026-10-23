package com.rizz.wanderlist.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.rizz.wanderlist.domain.model.GeoPoint
import com.rizz.wanderlist.domain.repository.LocationService
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Android's Fused Location provider: a fresh, accurate fix from GPS, Wi-Fi and cell data. */
class AndroidLocationService(private val context: Context) : LocationService {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    override fun hasPermission(): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    @SuppressLint("MissingPermission") // checked by hasPermission() just above the call
    override suspend fun currentLocation(): GeoPoint? {
        if (!hasPermission()) return null
        val cancellation = CancellationTokenSource()
        return suspendCancellableCoroutine { continuation ->
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
                // null when no fix is possible, e.g. location is switched off.
                .addOnSuccessListener { location ->
                    continuation.resume(location?.let { GeoPoint(it.latitude, it.longitude) })
                }
                .addOnFailureListener { continuation.resumeWithException(it) }
            continuation.invokeOnCancellation { cancellation.cancel() }
        }
    }
}
