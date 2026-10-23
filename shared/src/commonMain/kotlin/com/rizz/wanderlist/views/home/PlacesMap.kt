package com.rizz.wanderlist.views.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.rizz.wanderlist.views.place.PlaceMarker
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.rizz.wanderlist.views.theme.DarkMapStyleJson
import com.rizz.wanderlist.views.theme.isDark
import eu.buney.maps.CameraMoveStartedReason
import eu.buney.maps.CameraPosition
import eu.buney.maps.CameraPositionState
import eu.buney.maps.CameraUpdate
import eu.buney.maps.CameraUpdateFactory
import eu.buney.maps.GoogleMap
import eu.buney.maps.LatLng
import eu.buney.maps.LatLngBounds
import eu.buney.maps.MapProperties
import eu.buney.maps.MapStyleOptions
import eu.buney.maps.MapUiSettings
import eu.buney.maps.MarkerComposable
import eu.buney.maps.rememberCameraPositionState
import eu.buney.maps.rememberUpdatedMarkerState

/** Zoomed out far enough to show most of the world when there's nothing to frame yet. */
private val WorldView = CameraPosition(LatLng(20.0, 0.0), 1.5f, 0f, 0f)

/** Zoom used when every place is at the same point, where bounds would zoom in all the way. */
private const val SinglePlaceZoom = 12f

/** Selecting a place zooms in to at least this level, so its surroundings are recognisable. */
private const val SelectedPlaceMinZoom = 12f

/**
 * The places the filter chips select, as markers. Tapping a marker reports that place's id;
 * the selected place's marker is drawn larger and the camera moves to it.
 */
@Composable
internal fun PlacesMap(
    places: List<HomePlace>,
    selectedPlaceId: String?,
    onMarkerClick: (placeId: String) -> Unit,
    /** A tap on the map itself, not on a marker. */
    onMapClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Space taken by controls drawn over the map. Framing and Google's logo stay clear of it. */
    contentPadding: PaddingValues = PaddingValues()
) {
    val cameraState = rememberCameraPositionState { position = WorldView }
    var mapLoaded by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.isDark
    val properties = remember(isDark) {
        MapProperties(mapStyleOptions = if (isDark) MapStyleOptions.fromJson(DarkMapStyleJson) else null)
    }

    // Frame the visible places whenever the set changes (first load, a filter chip, a new place).
    // Only after the map has loaded: framing bounds on a map with no size crashes on Android.
    val placeIds = places.map { it.place.id }
    LaunchedEffect(mapLoaded, placeIds) {
        if (mapLoaded && places.isNotEmpty()) cameraState.animate(cameraUpdateFor(places))
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

    Box(modifier = modifier) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
            properties = properties,
            contentPadding = contentPadding,
            onMapClick = { onMapClick() },
            uiSettings = MapUiSettings(
                mapToolbarEnabled = false,
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false
            ),
            onMapLoaded = { mapLoaded = true }
        ) {
            places.forEach { item ->
                key(item.place.id) {
                    val place = item.place
                    val isSelected = place.id == selectedPlaceId
                    MarkerComposable(
                        // Re-render the marker image only when its look changes.
                        place.markerStyle, item.collection?.icon.orEmpty(), item.collection?.colors.orEmpty(), isSelected,
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
                        MapBadge(item, isSelected)
                    }
                }
            }
        }

        if (places.isEmpty()) {
            EmptyMapHint(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(contentPadding)
                    .padding(16.dp)
            )
        }
    }
}

/**
 * The place's marker on a ring, so it stands out from any map background. The selected one is
 * larger with a primary-coloured ring.
 */
@Composable
private fun MapBadge(item: HomePlace, isSelected: Boolean) {
    val ringColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val badgeSize = if (isSelected) 52.dp else 40.dp
    Box(
        modifier = Modifier
            .size(badgeSize + 6.dp)
            .background(ringColor, CircleShape)
            .padding(3.dp),
        contentAlignment = Alignment.Center
    ) {
        PlaceMarker(item.place.markerStyle, item.collection, size = badgeSize)
    }
}

@Composable
private fun EmptyMapHint(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Your map starts here", style = MaterialTheme.typography.titleMedium)
            Text(
                "Add places to see your memories on the map.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Animates to [update]. If something other than the user's finger interrupts the animation (for
 * example the keyboard closing as a search result is picked), jumps there instead, so the camera
 * never stops halfway. A user dragging the map is left alone.
 */
private suspend fun CameraPositionState.animateOrJump(update: CameraUpdate) {
    try {
        animate(update)
    } catch (e: CancellationException) {
        // Our own coroutine was cancelled (e.g. a newer selection): stop here.
        currentCoroutineContext().ensureActive()
        if (cameraMoveStartedReason != CameraMoveStartedReason.GESTURE) move(update)
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

/** Space around framed markers, in pixels (the API takes pixels). */
private const val BoundsPaddingPx = 160
