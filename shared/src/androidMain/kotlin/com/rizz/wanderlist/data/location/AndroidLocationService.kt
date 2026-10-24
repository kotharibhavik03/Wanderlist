package com.rizz.wanderlist.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.rizz.wanderlist.domain.model.GeoPoint
import com.rizz.wanderlist.domain.repository.LocationService
import kotlin.time.Duration.Companion.nanoseconds
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Android's Fused Location provider (GPS, Wi-Fi and cell data). Uses the position the device
 * already has when it's recent, otherwise the first live fix that's good enough (see [firstGoodFix]).
 */
class AndroidLocationService(private val context: Context) : LocationService {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    override fun hasPermission(): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    override suspend fun currentLocation(): GeoPoint? {
        if (!hasPermission()) return null
        return fixes().firstGoodFix()
    }

    /** The recent cached position (if any), then live updates until the collector stops. */
    @SuppressLint("MissingPermission") // checked by hasPermission() before collecting
    private fun fixes(): Flow<LocationFix> = callbackFlow {
        // The last position any app got. Instant; often fresh, e.g. while the map shows the blue dot.
        client.lastLocation.addOnSuccessListener { location ->
            if (location != null && location.age() <= MAX_CACHED_FIX_AGE) trySend(location.toFix())
        }
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { trySend(it.toFix()) }
            }
        }
        // Every second, and the first fix as soon as there is one, however rough.
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
            .setMinUpdateIntervalMillis(0L)
            .setWaitForAccurateLocation(false)
            .build()
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            // E.g. location switched off: end the stream, which reads as "no position".
            .addOnFailureListener { close() }
        awaitClose { client.removeLocationUpdates(callback) }
    }

    private fun Location.age() = (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos).nanoseconds

    private fun Location.toFix() = LocationFix(
        point = GeoPoint(latitude, longitude),
        // A fix without an accuracy estimate is treated as rough.
        accuracyMeters = if (hasAccuracy()) accuracy.toDouble() else Double.MAX_VALUE
    )
}
