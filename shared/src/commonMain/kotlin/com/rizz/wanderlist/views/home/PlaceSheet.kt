package com.rizz.wanderlist.views.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rizz.wanderlist.domain.model.PlacePhoto
import com.rizz.wanderlist.domain.model.SelectedLocation
import com.rizz.wanderlist.util.formatCoordinates
import com.rizz.wanderlist.util.formatDate
import com.rizz.wanderlist.views.place.DetailsCard
import com.rizz.wanderlist.views.place.DirectionsButton
import com.rizz.wanderlist.views.place.PhotosSection
import com.rizz.wanderlist.views.place.PlaceMarker
import com.rizz.wanderlist.views.place.PlaceStatusBadge
import com.rizz.wanderlist.views.place.RatingStars

/**
 * What the bottom sheet shows for the selected marker, Google Maps style: a summary that fits the
 * peek height, then the rest of the place's details when the sheet is dragged up.
 */
@Composable
internal fun PlaceSheetContent(
    item: HomePlace,
    /** The place's photos (the map's places don't carry them). */
    photos: List<PlacePhoto>,
    /** Space at the bottom kept clear for the floating bottom bar. */
    bottomPadding: Dp,
    /** Reports the summary's height, which sets how far the sheet peeks. */
    onSummaryHeightChanged: (Dp) -> Unit,
    /** Opacity of everything below the summary: 0 while peeking, 1 when pulled up. */
    detailsAlpha: () -> Float,
    onOpenDetails: () -> Unit,
    onDirectionsError: (String) -> Unit
) {
    val density = LocalDensity.current
    val place = item.place

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = bottomPadding)
    ) {
        // Summary: what the peeking sheet shows.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { onSummaryHeightChanged(with(density) { it.height.toDp() }) }
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PlaceMarker(place.markerStyle, item.collection, place.markerPhoto, size = 48.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        place.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        place.address ?: formatCoordinates(place.latitude, place.longitude),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PlaceStatusBadge(place.status)
                place.rating?.let { RatingStars(it) }
                place.visitedOnEpochMillis?.let {
                    Text(
                        formatDate(it),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            SideBySideOrStacked {
                DirectionsButton(
                    latitude = place.latitude,
                    longitude = place.longitude,
                    onError = onDirectionsError
                )
                OutlinedButton(onClick = onOpenDetails) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Details", maxLines = 1, softWrap = false)
                }
            }
        }

        HorizontalDivider()

        // The rest, revealed by dragging the sheet up. Same components as the Place detail screen.
        Column(
            modifier = Modifier
                .graphicsLayer { alpha = detailsAlpha() }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            PhotosSection(photos)
            DetailsCard(place, item.collection)
        }
    }
}

/**
 * The sheet for a searched location that isn't one of the user's places yet: what it is,
 * Directions, and Save this place (opens Add Place with this location filled in).
 */
@Composable
internal fun LocationSheetContent(
    location: SelectedLocation,
    /** Space at the bottom kept clear for the floating bottom bar. */
    bottomPadding: Dp,
    /** Reports the summary's height, which sets how far the sheet peeks. */
    onSummaryHeightChanged: (Dp) -> Unit,
    /** Opacity of everything below the summary: 0 while peeking, 1 when pulled up. */
    detailsAlpha: () -> Float,
    onSave: () -> Unit,
    onDirectionsError: (String) -> Unit
) {
    val density = LocalDensity.current
    // Same structure as PlaceSheetContent: a measured summary (the peek), then more below it. The
    // section below matters beyond its content: the sheet must be taller than its peek, or Material
    // drops the PartiallyExpanded position and a sheet animating to it crashes on completion.
    // The bottom-bar space is outside the measured summary, since the caller adds it to the peek.
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = bottomPadding)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { onSummaryHeightChanged(with(density) { it.height.toDp() }) }
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        location.actualName,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        location.address ?: formatCoordinates(location.latitude, location.longitude),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            SideBySideOrStacked {
                DirectionsButton(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    onError = onDirectionsError
                )
                FilledTonalButton(onClick = onSave) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save this place", maxLines = 1, softWrap = false)
                }
            }
        }

        HorizontalDivider()

        // Revealed by dragging the sheet up.
        Column(
            modifier = Modifier
                .graphicsLayer { alpha = detailsAlpha() }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Coordinates",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    formatCoordinates(location.latitude, location.longitude),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                "Not saved yet. Save it to a collection to add a visit date, rating, notes and " +
                        "people, and to see it on your map.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Buttons in a row of equal widths when every label fits, else stacked at full width. With a
 * large font, half the sheet can't hold "Directions" or "Save this place", and a label must
 * never break mid-word. Decided by measuring the buttons, so it works at any font size or width.
 */
@Composable
private fun SideBySideOrStacked(content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val gap = 12.dp.roundToPx()
        val count = measurables.size
        val width = constraints.maxWidth
        val share = (width - gap * (count - 1)) / count
        val fits = measurables.all { it.maxIntrinsicWidth(Constraints.Infinity) <= share }
        val itemWidth = if (fits) share else width
        val placeables = measurables.map { it.measure(Constraints.fixedWidth(itemWidth)) }
        if (fits) {
            val height = placeables.maxOf { it.height }
            layout(width, height) {
                placeables.forEachIndexed { index, placeable ->
                    placeable.placeRelative(index * (share + gap), (height - placeable.height) / 2)
                }
            }
        } else {
            val height = placeables.sumOf { it.height } + gap * (count - 1)
            layout(width, height) {
                var y = 0
                placeables.forEach { placeable ->
                    placeable.placeRelative(0, y)
                    y += placeable.height + gap
                }
            }
        }
    }
}
