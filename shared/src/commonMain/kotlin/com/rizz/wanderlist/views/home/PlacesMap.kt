package com.rizz.wanderlist.views.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.rizz.wanderlist.domain.model.GeoPoint
import com.rizz.wanderlist.domain.model.SelectedLocation
import com.rizz.wanderlist.views.image.rememberLoadedPhoto
import com.rizz.wanderlist.views.map.AppMapUiSettings
import com.rizz.wanderlist.views.map.WorldView
import com.rizz.wanderlist.views.map.animateOrJump
import com.rizz.wanderlist.views.map.rememberMapProperties
import com.rizz.wanderlist.views.place.PlaceMarker
import eu.buney.maps.CameraUpdate
import eu.buney.maps.CameraUpdateFactory
import eu.buney.maps.GoogleMap
import eu.buney.maps.LatLng
import eu.buney.maps.LatLngBounds
import eu.buney.maps.Marker
import eu.buney.maps.MarkerComposable
import eu.buney.maps.rememberCameraPositionState
import eu.buney.maps.rememberUpdatedMarkerState

/** Zoom used when every place is at the same point, where bounds would zoom in all the way. */
private const val SinglePlaceZoom = 12f

/** Selecting a place zooms in to at least this level, so its surroundings are recognisable. */
private const val SelectedPlaceMinZoom = 12f

/** "My location" on Home shows the neighbourhood around you. */
private const val MyLocationMinZoom = 14f

/**
 * The places the filter chips select, as markers. Tapping a marker reports that place's id;
 * the selected place's marker is drawn larger and the camera moves to it.
 */
@Composable
internal fun PlacesMap(
    places: List<HomePlace>,
    selectedPlaceId: String?,
    /** A searched location to show with a temporary pin, or null. */
    pin: SelectedLocation?,
    /** Fly here once ("my location"), then report it with [onMyLocationShown]. */
    myLocation: GeoPoint?,
    /** Whether the user's position is shown at start; the first framing of the places waits for it. */
    startPosition: StartPosition,
    onMyLocationShown: () -> Unit,
    /** Draw the blue "you are here" dot (only once location access is granted). */
    showMyLocation: Boolean,
    onMarkerClick: (placeId: String) -> Unit,
    /** A tap on the map itself, not on a marker. */
    onMapClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Space taken by controls drawn over the map. Framing and Google's logo stay clear of it. */
    contentPadding: PaddingValues = PaddingValues()
) {
    val cameraState = rememberCameraPositionState { position = WorldView }
    var mapLoaded by remember { mutableStateOf(false) }
    val properties = rememberMapProperties(showMyLocation)

    // Frame the visible places whenever the set changes (a filter chip, a new place). The first
    // set waits until the start position settles, and isn't framed if the map started on the
    // user's position. Only after the map has loaded: framing bounds on a map with no size
    // crashes on Android.
    // The last set handled is saved, like the camera: Home leaves composition while another tab
    // is open, and coming back must not count as a change (it used to reframe the places,
    // pulling the camera away from where the user left it).
    val placesKey = places.map { it.place.id }.sorted().joinToString(",")
    var handledPlacesKey by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(mapLoaded, placesKey, startPosition) {
        if (!mapLoaded || places.isEmpty() || startPosition == StartPosition.Locating) return@LaunchedEffect
        if (placesKey == handledPlacesKey) return@LaunchedEffect
        val isFirstSet = handledPlacesKey == null
        handledPlacesKey = placesKey
        if (isFirstSet && startPosition == StartPosition.Located) return@LaunchedEffect
        cameraState.animate(cameraUpdateFor(places))
    }

    // Bring the selected place into view, centred in the area the overlays leave visible.
    LaunchedEffect(mapLoaded, selectedPlaceId) {
        val selected = places.firstOrNull { it.place.id == selectedPlaceId } ?: return@LaunchedEffect
        if (!mapLoaded) return@LaunchedEffect
        val zoom = maxOf(cameraState.position.zoom, SelectedPlaceMinZoom)
        cameraState.animateOrJump(
            CameraUpdateFactory.newLatLngZoom(LatLng(selected.place.latitude, selected.place.longitude), zoom)
        )
    }

    // Fly to the device's position after "my location".
    LaunchedEffect(mapLoaded, myLocation) {
        if (!mapLoaded || myLocation == null) return@LaunchedEffect
        val zoom = maxOf(cameraState.position.zoom, MyLocationMinZoom)
        cameraState.animateOrJump(CameraUpdateFactory.newLatLngZoom(LatLng(myLocation.latitude, myLocation.longitude), zoom))
        onMyLocationShown()
    }

    // Fly to a searched location.
    LaunchedEffect(mapLoaded, pin) {
        if (!mapLoaded || pin == null) return@LaunchedEffect
        val zoom = maxOf(cameraState.position.zoom, SelectedPlaceMinZoom)
        cameraState.animateOrJump(CameraUpdateFactory.newLatLngZoom(LatLng(pin.latitude, pin.longitude), zoom))
    }

    Box(modifier = modifier) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
            properties = properties,
            contentPadding = contentPadding,
            onMapClick = { onMapClick() },
            uiSettings = AppMapUiSettings,
            onMapLoaded = { mapLoaded = true }
        ) {
            places.forEach { item ->
                key(item.place.id) {
                    val place = item.place
                    val isSelected = place.id == selectedPlaceId
                    // Loaded here, not inside the marker: the marker is drawn to a bitmap once,
                    // and redrawn when its keys change, so it's redrawn when the photo arrives.
                    val photo = rememberLoadedPhoto(place.markerPhotoUrl, SelectedBadgeSize)
                    MarkerComposable(
                        // Re-render the marker image only when its look changes.
                        place.markerStyle, item.collection?.icon.orEmpty(), item.collection?.colors.orEmpty(), isSelected, photo != null,
                        state = rememberUpdatedMarkerState(LatLng(place.latitude, place.longitude)),
                        title = place.displayName,
                        // Centred on the spot: the badge is a circle, not a pin with a point.
                        anchor = Offset(0.5f, 0.5f),
                        // The selected marker draws on top of its neighbours.
                        zIndex = if (isSelected) 1f else 0f,
                        onClick = {
                            onMarkerClick(place.id)
                            true // handled: don't also show the default info window
                        }
                    ) {
                        MapBadge(item, isSelected, photo)
                    }
                }
            }

            // A searched location: Google's standard red pin, so it reads as "not saved yet".
            pin?.let { location ->
                key(location) {
                    Marker(
                        state = rememberUpdatedMarkerState(LatLng(location.latitude, location.longitude)),
                        title = location.actualName,
                        zIndex = 2f,
                        onClick = { true } // its sheet is already open
                    )
                }
            }
        }

    }
}

/**
 * The place's marker on a ring, so it stands out from any map background. The selected one is
 * larger with a primary-coloured ring.
 */
@Composable
private fun MapBadge(item: HomePlace, isSelected: Boolean, photo: Painter?) {
    val ringColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val badgeSize = if (isSelected) SelectedBadgeSize else 40.dp
    Box(
        modifier = Modifier
            .size(badgeSize + 6.dp)
            .background(ringColor, CircleShape)
            .padding(3.dp),
        contentAlignment = Alignment.Center
    ) {
        // No URL here, only the loaded photo: until it arrives the marker shows the collection badge.
        PlaceMarker(item.place.markerStyle, item.collection, photoUrl = null, size = badgeSize, loadedPhoto = photo)
    }
}

/** One place: centre on it. Several: fit them all, with room so edge markers aren't cut off. */
private fun cameraUpdateFor(places: List<HomePlace>): CameraUpdate {
    val points = places.map { LatLng(it.place.latitude, it.place.longitude) }
    val south = points.minOf { it.latitude }
    val north = points.maxOf { it.latitude }
    val west = points.minOf { it.longitude }
    val east = points.maxOf { it.longitude }
    return if (south == north && west == east) {
        CameraUpdateFactory.newLatLngZoom(points.first(), SinglePlaceZoom)
    } else {
        CameraUpdateFactory.newLatLngBounds(LatLngBounds(LatLng(south, west), LatLng(north, east)), BoundsPaddingPx)
    }
}

/** The selected marker's size, the largest a marker gets; photos are loaded at this size. */
private val SelectedBadgeSize = 52.dp

/** Space around framed markers, in pixels (the API takes pixels). */
private const val BoundsPaddingPx = 160
