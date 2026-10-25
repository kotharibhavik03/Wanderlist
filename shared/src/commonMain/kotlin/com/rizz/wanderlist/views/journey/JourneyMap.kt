package com.rizz.wanderlist.views.journey

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rizz.wanderlist.domain.model.Journey
import com.rizz.wanderlist.domain.model.MarkerStyle
import com.rizz.wanderlist.domain.model.PlaceCollection
import com.rizz.wanderlist.views.image.rememberLoadedPhoto
import com.rizz.wanderlist.views.map.AppMapUiSettings
import com.rizz.wanderlist.views.map.GoogleLogoTapBlocker
import com.rizz.wanderlist.views.map.WorldView
import com.rizz.wanderlist.views.map.animateOrJump
import com.rizz.wanderlist.views.map.rememberMapProperties
import com.rizz.wanderlist.views.place.PlaceMarker
import eu.buney.maps.Cap
import eu.buney.maps.CameraUpdate
import eu.buney.maps.CameraUpdateFactory
import eu.buney.maps.GoogleMap
import eu.buney.maps.JointType
import eu.buney.maps.LatLng
import eu.buney.maps.LatLngBounds
import eu.buney.maps.MarkerComposable
import eu.buney.maps.PatternItem
import eu.buney.maps.Polyline
import eu.buney.maps.rememberCameraPositionState
import eu.buney.maps.rememberUpdatedMarkerState
import kotlinx.coroutines.delay

/** How close the camera comes to a stop: the place and its surroundings. */
private const val StopZoom = 12f

/**
 * The journey on a map: a numbered marker per stop and the route between them, solid where the
 * replay has been ([reachedStops]) and dashed where it's still going. The current stop is drawn
 * large with its own marker, and the leg that led to it is drawn thicker. The camera follows
 * [position]: the whole route on the overview and at the end, the current stop otherwise. Arriving
 * at a stop first frames the leg from the previous stop, so you see where you came from.
 */
@Composable
internal fun JourneyMap(
    journey: Journey,
    collection: PlaceCollection?,
    position: JourneyPosition,
    reachedStops: Int,
    onStopClick: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Space taken by the panels drawn over the map; framing and Google's logo stay clear of it. */
    contentPadding: PaddingValues = PaddingValues()
) {
    val cameraState = rememberCameraPositionState { this.position = WorldView }
    var mapLoaded by remember { mutableStateOf(false) }
    val properties = rememberMapProperties()
    val points = remember(journey) { journey.stops.map { LatLng(it.place.latitude, it.place.longitude) } }
    val currentIndex = (position as? JourneyPosition.AtStop)?.index
    val legPaddingPx = with(LocalDensity.current) { LegPadding.roundToPx() }
    // The stop the camera last arrived at. Saved, so turning the phone (or the panels' padding
    // arriving) settles on the stop again instead of replaying the leg.
    var arrivedAt by rememberSaveable { mutableStateOf<Int?>(null) }

    // Follow the replay. Only after the map has loaded (framing bounds on a map with no size
    // crashes on Android), and again when the panels' padding is known, so the framing uses it.
    LaunchedEffect(mapLoaded, position, points, contentPadding) {
        if (!mapLoaded || points.isEmpty()) return@LaunchedEffect
        if (currentIndex == null) {
            arrivedAt = null
            cameraState.animateOrJump(wholeRoute(points))
            return@LaunchedEffect
        }
        // A new stop: first the leg from the previous one, held for a moment. Skipped for the
        // first stop, and for a short leg, which the stop's own view already shows.
        val previous = points.getOrNull(currentIndex - 1)
        val legKm = journey.stops[currentIndex].kmFromPrevious ?: 0.0
        if (arrivedAt != currentIndex && previous != null && legKm >= MinLegKm) {
            val leg = CameraUpdateFactory.newLatLngBounds(legBounds(previous, points[currentIndex]), legPaddingPx)
            val completed = cameraState.animateOrJump(leg, LegFlightMs)
            arrivedAt = currentIndex
            if (!completed) return@LaunchedEffect // the user took over the map
            delay(LegHoldMs)
        }
        arrivedAt = currentIndex
        cameraState.animateOrJump(CameraUpdateFactory.newLatLngZoom(points[currentIndex], StopZoom), StopFlightMs)
    }

    // On iOS a marker is drawn in a Compose scene of its own, which doesn't see the app's theme.
    val colorScheme = MaterialTheme.colorScheme
    val lineWidth = with(LocalDensity.current) { RouteWidth.toPx() }
    val currentLegWidth = with(LocalDensity.current) { CurrentLegWidth.toPx() }
    val dash = with(LocalDensity.current) { listOf(PatternItem.Dash(12.dp.toPx()), PatternItem.Gap(8.dp.toPx())) }

    Box(modifier = modifier) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
            properties = properties,
            contentPadding = contentPadding,
            uiSettings = AppMapUiSettings,
            onMapLoaded = { mapLoaded = true }
        ) {
            // Still to go: dashed, from the current stop to the end.
            val ahead = points.drop((reachedStops - 1).coerceAtLeast(0))
            if (ahead.size >= 2) {
                Polyline(
                    points = ahead,
                    color = colorScheme.outline,
                    width = lineWidth,
                    pattern = dash,
                    geodesic = true,
                    jointType = JointType.Round
                )
            }
            // Travelled: solid, from the first stop to the current one, on top of the dashes.
            val travelled = points.take(reachedStops)
            if (travelled.size >= 2) {
                Polyline(
                    points = travelled,
                    color = colorScheme.primary,
                    width = lineWidth,
                    geodesic = true,
                    jointType = JointType.Round,
                    startCap = Cap.Round,
                    endCap = Cap.Round,
                    zIndex = 1f
                )
            }
            // The leg that led to the current stop: thicker, on top of the rest of the route.
            if (currentIndex != null && currentIndex > 0) {
                Polyline(
                    points = listOf(points[currentIndex - 1], points[currentIndex]),
                    color = colorScheme.primary,
                    width = currentLegWidth,
                    geodesic = true,
                    startCap = Cap.Round,
                    endCap = Cap.Round,
                    zIndex = 2f
                )
            }

            journey.stops.forEachIndexed { index, stop ->
                key(stop.place.id) {
                    val isCurrent = index == currentIndex
                    val isReached = index < reachedStops
                    // Loaded out here: the marker is drawn to a bitmap once, and redrawn when its
                    // keys change, so it's redrawn when the photo arrives.
                    val photo = if (isCurrent) rememberLoadedPhoto(stop.place.markerPhoto, CurrentStopSize) else null
                    MarkerComposable(
                        index, isCurrent, isReached, photo != null, stop.place.markerStyle,
                        collection?.icon.orEmpty(), collection?.colors.orEmpty(), colorScheme,
                        state = rememberUpdatedMarkerState(points[index]),
                        title = stop.place.displayName,
                        contentDescription = "Stop ${index + 1}: ${stop.place.displayName}",
                        // The current stop's tail points at the spot; a numbered dot sits on it.
                        anchor = if (isCurrent) Offset(0.5f, 1f) else Offset(0.5f, 0.5f),
                        zIndex = if (isCurrent) 3f else 2f,
                        onClick = {
                            onStopClick(index)
                            true // handled: don't also show the default info window
                        }
                    ) {
                        MaterialTheme(colorScheme = colorScheme) {
                            if (isCurrent) {
                                CurrentStopMarker(stop.place.markerStyle, collection, photo)
                            } else {
                                NumberedStop(number = index + 1, isReached = isReached)
                            }
                        }
                    }
                }
            }
        }

        GoogleLogoTapBlocker(contentPadding)
    }
}

/** A stop the replay isn't at: its number, filled once the replay has passed it. */
@Composable
private fun NumberedStop(number: Int, isReached: Boolean) {
    val background = if (isReached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val content = if (isReached) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .size(NumberedStopSize)
            .background(background, CircleShape)
            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            number.toString(),
            color = content,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * The stop the replay is at: the place's own marker (its photo or the collection's badge) on a
 * primary ring, with a tail pointing at the spot, like a selected place on Home.
 */
@Composable
private fun CurrentStopMarker(style: MarkerStyle, collection: PlaceCollection?, photo: Painter?) {
    val ring = MaterialTheme.colorScheme.primary
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(CurrentStopSize + RingWidth * 2)
                .background(ring, CircleShape)
                .padding(RingWidth),
            contentAlignment = Alignment.Center
        ) {
            // Until the photo arrives (or with no photo marker) the collection's badge shows.
            PlaceMarker(style, collection, photo = null, size = CurrentStopSize, loadedPhoto = photo)
        }
        Canvas(modifier = Modifier.size(width = 14.dp, height = 10.dp)) {
            val overlap = 2.5.dp.toPx() // reaches into the ring, so there's no seam
            drawPath(
                Path().apply {
                    moveTo(0f, -overlap)
                    lineTo(size.width, -overlap)
                    lineTo(size.width / 2f, size.height)
                    close()
                },
                ring
            )
        }
    }
}

/** Every stop in view, with room so the markers at the edges aren't cut off. */
private fun wholeRoute(points: List<LatLng>): CameraUpdate {
    val south = points.minOf { it.latitude }
    val north = points.maxOf { it.latitude }
    val west = points.minOf { it.longitude }
    val east = points.maxOf { it.longitude }
    return if (south == north && west == east) {
        CameraUpdateFactory.newLatLngZoom(points.first(), StopZoom)
    } else {
        CameraUpdateFactory.newLatLngBounds(LatLngBounds(LatLng(south, west), LatLng(north, east)), BoundsPaddingPx)
    }
}

/** Both ends of a leg; the camera fits it with [LegPadding] around it. */
private fun legBounds(from: LatLng, to: LatLng) = LatLngBounds(
    LatLng(minOf(from.latitude, to.latitude), minOf(from.longitude, to.longitude)),
    LatLng(maxOf(from.latitude, to.latitude), maxOf(from.longitude, to.longitude))
)

private val RouteWidth = 4.dp
private val CurrentLegWidth = 7.dp

/** Room around a framed leg: enough for the current stop's marker, which stands above its spot. */
private val LegPadding = 72.dp

/** Legs shorter than this are already in view at [StopZoom], so the camera goes straight to the stop. */
private const val MinLegKm = 10.0

/** How long the camera takes to frame a leg, how long it holds there, then how long to reach the stop. */
private const val LegFlightMs = 1_200
private const val LegHoldMs = 700L
private const val StopFlightMs = 1_200
private val NumberedStopSize = 28.dp
private val CurrentStopSize = 52.dp
private val RingWidth = 3.dp

/** Space around the framed route, in pixels (the API takes pixels). */
private const val BoundsPaddingPx = 120
