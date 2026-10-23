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

    override suspend fun search(query: String): List<SelectedLocation> {
        if (!Geocoder.isPresent()) throw IOException("Location search isn't available on this device.")
        val geocoder = Geocoder(context)
        val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: asynchronous API.
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocationName(query, MAX_RESULTS, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        continuation.resume(addresses)
                    }

                    override fun onError(errorMessage: String?) {
                        continuation.resumeWithException(IOException(errorMessage ?: "Location search failed."))
                    }
                })
            }
        } else {
            // Older Android: the blocking call does network I/O, so keep it off the main thread.
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(query, MAX_RESULTS).orEmpty()
            }
        }
        return addresses.map { it.toLocation() }
    }

    private fun Address.toLocation(): SelectedLocation {
        val fullAddress = getAddressLine(0)
        // featureName is the place itself ("Spiti Valley"), but for a street address it's often
        // just the house number, so only use it when it reads as a name.
        val name = featureName?.takeIf { name -> name.any { it.isLetter() } }
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
