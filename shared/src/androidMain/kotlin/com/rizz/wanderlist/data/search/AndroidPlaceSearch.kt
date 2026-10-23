package com.rizz.wanderlist.data.search

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.rizz.wanderlist.domain.model.SelectedLocation
import com.rizz.wanderlist.domain.repository.PlaceSearch
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Android's built-in Geocoder: free, no API key. Finds towns, landmarks and addresses; no autocomplete. */
class AndroidPlaceSearch(private val context: Context) : PlaceSearch {

    override suspend fun search(query: String): List<SelectedLocation> =
        geocode(
            asynchronous = { geocoder, listener -> geocoder.getFromLocationName(query, MAX_RESULTS, listener) },
            blocking = { geocoder ->
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(query, MAX_RESULTS)
            }
        ).map { it.toLocation() }

    override suspend fun reverseGeocode(latitude: Double, longitude: Double): SelectedLocation? =
        geocode(
            asynchronous = { geocoder, listener -> geocoder.getFromLocation(latitude, longitude, 1, listener) },
            blocking = { geocoder ->
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1)
            }
        ).firstOrNull()
            // Keep the pin's exact point; the address's own coordinates can be the building's centre.
            ?.toLocation()?.copy(latitude = latitude, longitude = longitude)

    /** Runs a Geocoder request: the asynchronous API on Android 13+, the blocking one off the main thread below. */
    private suspend fun geocode(
        asynchronous: (Geocoder, Geocoder.GeocodeListener) -> Unit,
        blocking: (Geocoder) -> List<Address>?
    ): List<Address> {
        if (!Geocoder.isPresent()) throw IOException("Location search isn't available on this device.")
        val geocoder = Geocoder(context)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                asynchronous(geocoder, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        continuation.resume(addresses)
                    }

                    override fun onError(errorMessage: String?) {
                        continuation.resumeWithException(IOException(errorMessage ?: "Location search failed."))
                    }
                })
            }
        } else {
            // The blocking call does network I/O, so keep it off the main thread.
            withContext(Dispatchers.IO) { blocking(geocoder).orEmpty() }
        }
    }

    private fun Address.toLocation(): SelectedLocation {
        val fullAddress = getAddressLine(0)
        // featureName is the place itself ("Spiti Valley"), but for a street address it's often
        // just the house number, so only use it when it reads as a name; then the street, the
        // neighbourhood, the town.
        val name = featureName?.takeIf { name -> name.any { it.isLetter() } }
            ?: thoroughfare
            ?: subLocality
            ?: locality
            ?: fullAddress
            ?: "Selected location"
        return SelectedLocation(
            latitude = latitude,
            longitude = longitude,
            actualName = name,
            address = fullAddress?.takeIf { it != name }
        )
    }

    private companion object {
        const val MAX_RESULTS = 5
    }
}
