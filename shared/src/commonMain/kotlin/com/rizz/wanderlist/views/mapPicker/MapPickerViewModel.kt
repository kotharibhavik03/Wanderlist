package com.rizz.wanderlist.views.mapPicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizz.wanderlist.domain.model.SelectedLocation
import com.rizz.wanderlist.domain.repository.LocationService
import com.rizz.wanderlist.domain.repository.PlaceSearch
import com.rizz.wanderlist.domain.usecase.LocationSearchState
import com.rizz.wanderlist.domain.usecase.SearchLocationsUseCase
import com.rizz.wanderlist.util.distanceMeters
import com.rizz.wanderlist.util.resultOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** A camera move the screen should make, at least to [zoom]. */
data class FlyTo(val latitude: Double, val longitude: Double, val zoom: Float)

data class MapPickerUiState(
    val query: String = "",
    val search: LocationSearchState = LocationSearchState(),
    /** What "Use this location" returns: the point under the centre pin, with its name. Null until chosen. */
    val pinned: SelectedLocation? = null,
    /** Looking up the address under the pin. */
    val isResolving: Boolean = false,
    /** Ask the map to fly here; cleared once the screen has done it. */
    val flyTo: FlyTo? = null,
    /** Shows the blue "you are here" dot once location access is allowed. */
    val hasLocationPermission: Boolean = false,
    /** Finding the device's position after "my location" was tapped. */
    val isLocating: Boolean = false,
    /** A one-off message for the user (e.g. location is off); cleared once shown. */
    val message: String? = null
)

/**
 * The location picker: search to get close, then move the map under a fixed centre pin. When the
 * map stops, the point under the pin is named by reverse geocoding.
 */
class MapPickerViewModel(
    private val placeSearch: PlaceSearch,
    private val locationService: LocationService,
    searchLocations: SearchLocationsUseCase
) : ViewModel() {

    private data class PinState(
        val pinned: SelectedLocation? = null,
        val isResolving: Boolean = false,
        val flyTo: FlyTo? = null,
        val hasLocationPermission: Boolean = false,
        val isLocating: Boolean = false,
        val message: String? = null
    )

    private val query = MutableStateFlow("")
    private val pin = MutableStateFlow(PinState())

    /** The last picked search result: kept as-is while the pin stays on it. */
    private var anchor: SelectedLocation? = null
    /** Where the map last came to rest, to ignore "stops" that didn't really move it. */
    private var lastIdle: Pair<Double, Double>? = null
    private var started = false
    private var resolveJob: Job? = null

    val uiState: StateFlow<MapPickerUiState> = combine(
        query,
        searchLocations(query),
        pin
    ) { q, search, p ->
        MapPickerUiState(
            query = q,
            search = search,
            pinned = p.pinned,
            isResolving = p.isResolving,
            flyTo = p.flyTo,
            hasLocationPermission = p.hasLocationPermission,
            isLocating = p.isLocating,
            message = p.message
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapPickerUiState())

    /** Starts from the place's current location, if it has one. Safe to call again. */
    fun start(initial: SelectedLocation?) {
        if (started) return
        started = true
        anchor = initial
        pin.value = PinState(pinned = initial, hasLocationPermission = locationService.hasPermission())
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    /** A search result was picked: fly there and use its name. */
    fun onResultPicked(location: SelectedLocation) {
        query.value = ""
        anchor = location
        resolveJob?.cancel()
        pin.update {
            it.copy(
                pinned = location,
                isResolving = false,
                flyTo = FlyTo(location.latitude, location.longitude, SEARCH_RESULT_ZOOM)
            )
        }
    }

    fun onFlyToHandled() = pin.update { it.copy(flyTo = null) }

    fun hasLocationPermission(): Boolean = locationService.hasPermission()

    /** The permission dialog was answered: show or hide the "you are here" dot. */
    fun onPermissionAnswered(granted: Boolean) = pin.update { it.copy(hasLocationPermission = granted) }

    fun onPermissionRefused() = pin.update {
        it.copy(message = "Location access is off. Allow it in Settings to use your current location.")
    }

    /** Fly to where the device is; landing there names the spot like any other map stop. */
    fun goToMyLocation() {
        if (pin.value.isLocating) return
        pin.update { it.copy(isLocating = true, hasLocationPermission = true) }
        viewModelScope.launch {
            val here = withTimeoutOrNull(LOCATE_TIMEOUT_MS) {
                resultOf { locationService.currentLocation() }.getOrNull()
            }
            pin.update {
                if (here == null) {
                    it.copy(isLocating = false, message = "Couldn't find your location. Check that location is turned on.")
                } else {
                    it.copy(isLocating = false, flyTo = FlyTo(here.latitude, here.longitude, MY_LOCATION_ZOOM))
                }
            }
        }
    }

    fun onMessageShown() = pin.update { it.copy(message = null) }

    /**
     * The map stopped with the pin at this point. Near the picked result the result is kept;
     * elsewhere the point is named by reverse geocoding. A newer stop cancels the lookup.
     */
    fun onCameraIdle(latitude: Double, longitude: Double) {
        // The map can report a stop without really moving (e.g. when its padding is re-applied).
        // Treating that as a new spot would look the address up again, change what the panel
        // shows, and could start a loop, so a stop at the same point is ignored.
        val previous = lastIdle
        lastIdle = latitude to longitude
        if (previous != null &&
            distanceMeters(previous.first, previous.second, latitude, longitude) < SAME_POINT_METERS
        ) return

        val picked = anchor
        if (picked != null &&
            distanceMeters(picked.latitude, picked.longitude, latitude, longitude) < SAME_SPOT_METERS
        ) {
            resolveJob?.cancel()
            pin.update { it.copy(pinned = picked, isResolving = false) }
            return
        }
        anchor = null
        resolveJob?.cancel()
        // Until the name arrives, the point itself is already usable.
        val unnamed = SelectedLocation(latitude, longitude, actualName = UNNAMED)
        pin.update { it.copy(pinned = unnamed, isResolving = true) }
        resolveJob = viewModelScope.launch {
            delay(RESOLVE_DELAY_MS) // the map often settles in small steps; wait for the last one
            val found = resultOf { placeSearch.reverseGeocode(latitude, longitude) }.getOrNull()
            pin.update { it.copy(pinned = found ?: unnamed, isResolving = false) }
        }
    }

    private companion object {
        /** Closer than this to the picked result counts as still on it. */
        const val SAME_SPOT_METERS = 25.0
        /** Closer than this to the last stop counts as not having moved at all. */
        const val SAME_POINT_METERS = 1.0
        const val RESOLVE_DELAY_MS = 300L
        const val UNNAMED = "Pinned location"
        /** A search result is a town or landmark: show its surroundings. */
        const val SEARCH_RESULT_ZOOM = 15f
        /** The device's position: close enough to pick out the building. */
        const val MY_LOCATION_ZOOM = 17f
        /** A fix normally takes a few seconds; give up rather than spin forever. */
        const val LOCATE_TIMEOUT_MS = 15_000L
    }
}
