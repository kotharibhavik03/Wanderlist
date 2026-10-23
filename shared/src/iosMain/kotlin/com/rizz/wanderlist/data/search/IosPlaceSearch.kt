package com.rizz.wanderlist.data.search

import com.rizz.wanderlist.domain.model.SelectedLocation
import com.rizz.wanderlist.domain.repository.PlaceSearch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLGeocoder
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLPlacemark
import platform.CoreLocation.kCLErrorDomain
import platform.CoreLocation.kCLErrorGeocodeFoundNoResult
import platform.MapKit.MKErrorDomain
import platform.MapKit.MKErrorPlacemarkNotFound
import platform.MapKit.MKLocalSearch
import platform.MapKit.MKLocalSearchRequest
import platform.MapKit.MKMapItem

/**
 * Apple's free services, no API key: MKLocalSearch finds places, businesses and addresses;
 * CLGeocoder names a point on the map.
 */
class IosPlaceSearch : PlaceSearch {

    override suspend fun search(query: String): List<SelectedLocation> =
        suspendCancellableCoroutine { continuation ->
            val request = MKLocalSearchRequest().apply { naturalLanguageQuery = query }
            val search = MKLocalSearch(request)
            search.startWithCompletionHandler { response, error ->
                when {
                    // "Nothing found" is an empty result, not a failure.
                    error != null && error.domain == MKErrorDomain && error.code == MKErrorPlacemarkNotFound.toLong() ->
                        continuation.resume(emptyList())
                    error != null ->
                        continuation.resumeWithException(IllegalStateException(error.localizedDescription))
                    else -> continuation.resume(
                        response?.mapItems.orEmpty()
                            .filterIsInstance<MKMapItem>()
                            .take(MAX_RESULTS)
                            .map { it.toLocation() }
                    )
                }
            }
            continuation.invokeOnCancellation { search.cancel() }
        }

    override suspend fun reverseGeocode(latitude: Double, longitude: Double): SelectedLocation? =
        suspendCancellableCoroutine { continuation ->
            // CLGeocoder is deprecated from iOS 26 in favour of MapKit's reverse geocoding request,
            // which doesn't exist on the iOS 16 the app supports.
            @Suppress("DEPRECATION")
            val geocoder = CLGeocoder()
            @Suppress("DEPRECATION")
            geocoder.reverseGeocodeLocation(CLLocation(latitude = latitude, longitude = longitude)) { placemarks, error ->
                when {
                    // A point with no address (open sea, wilderness) is not a failure.
                    error != null && error.domain == kCLErrorDomain && error.code == kCLErrorGeocodeFoundNoResult ->
                        continuation.resume(null)
                    error != null ->
                        continuation.resumeWithException(IllegalStateException(error.localizedDescription))
                    else -> continuation.resume(
                        placemarks?.firstOrNull()
                            ?.let { it as? CLPlacemark }
                            ?.toLocation(latitude, longitude)
                    )
                }
            }
            continuation.invokeOnCancellation { geocoder.cancelGeocode() }
        }

    /** Name from the most specific part ("12 MG Road", a landmark, the area), then the full address. */
    private fun CLPlacemark.toLocation(latitude: Double, longitude: Double): SelectedLocation {
        val name = name ?: thoroughfare ?: subLocality ?: locality ?: "Selected location"
        val address = listOfNotNull(name, subLocality, locality, administrativeArea, postalCode, country)
            .distinct()
            .joinToString(", ")
        return SelectedLocation(
            latitude = latitude,
            longitude = longitude,
            actualName = name,
            address = address.takeIf { it != name && it.isNotBlank() }
        )
    }

    // placemark is deprecated from iOS 26 in favour of location/address, which don't exist on
    // the iOS 16 the app supports.
    @Suppress("DEPRECATION")
    @OptIn(ExperimentalForeignApi::class)
    private fun MKMapItem.toLocation(): SelectedLocation {
        val placemark = placemark
        val (latitude, longitude) = placemark.coordinate.useContents { latitude to longitude }
        val name = name ?: placemark.title ?: "Selected location"
        return SelectedLocation(
            latitude = latitude,
            longitude = longitude,
            actualName = name,
            address = placemark.title?.takeIf { it != name }
        )
    }

    private companion object {
        const val MAX_RESULTS = 5
    }
}
