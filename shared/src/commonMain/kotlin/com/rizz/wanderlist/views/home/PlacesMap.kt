package com.rizz.wanderlist.views.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.rizz.wanderlist.views.place.PlaceMarker
import eu.buney.maps.CameraPosition
import eu.buney.maps.CameraUpdate
import eu.buney.maps.CameraUpdateFactory
import eu.buney.maps.GoogleMap
import eu.buney.maps.LatLng
import eu.buney.maps.LatLngBounds
import eu.buney.maps.MapUiSettings
import eu.buney.maps.MarkerComposable
import eu.buney.maps.rememberCameraPositionState
import eu.buney.maps.rememberUpdatedMarkerState

/** Zoomed out far enough to show most of the world when there's nothing to frame yet. */
private val WorldView = CameraPosition(LatLng(20.0, 0.0), 1.5f, 0f, 0f)

/** Zoom used when every place is at the same point, where bounds would zoom in all the way. */
private const val SinglePlaceZoom = 12f

/** The places the filter chips select, as markers. Tapping a marker reports that place's id. */
@Composable
internal fun PlacesMap(
    places: List<HomePlace>,
    onMarkerClick: (placeId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val cameraState = rememberCameraPositionState { position = WorldView }
    var mapLoaded by remember { mutableStateOf(false) }

    // Frame the visible places whenever the set changes (first load, a filter chip, a new place).
    // Only after the map has loaded: framing bounds on a map with no size crashes on Android.
    val placeIds = places.map { it.place.id }
    LaunchedEffect(mapLoaded, placeIds) {
        if (mapLoaded && places.isNotEmpty()) cameraState.animate(cameraUpdateFor(places))
    }

    Box(modifier = modifier.clip(MaterialTheme.shapes.large)) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
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
                    MarkerComposable(
                        // Re-render the marker image only when its look changes.
                        place.markerStyle, item.collection?.icon.orEmpty(), item.collection?.colors.orEmpty(),
                        state = rememberUpdatedMarkerState(LatLng(place.latitude, place.longitude)),
                        title = place.displayName,
                        // Centred on the spot: the badge is a circle, not a pin with a point.
                        anchor = Offset(0.5f, 0.5f),
                        onClick = {
                            onMarkerClick(place.id)
                            true // handled: don't also show the default info window
                        }
                    ) {
                        MapBadge(item)
                    }
                }
            }
        }

        if (places.isEmpty()) {
            EmptyMapHint(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
    }
}

/** The place's marker on a ring in the surface colour, so it stands out from any map background. */
@Composable
private fun MapBadge(item: HomePlace) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .padding(3.dp),
        contentAlignment = Alignment.Center
    ) {
        PlaceMarker(item.place.markerStyle, item.collection, size = 40.dp)
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
