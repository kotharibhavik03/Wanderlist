package com.rizz.wanderlist.views.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    /** Space at the bottom kept clear for the floating bottom bar. */
    bottomPadding: Dp,
    /** Reports the summary's height, which sets how far the sheet peeks. */
    onSummaryHeightChanged: (Dp) -> Unit,
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
                PlaceMarker(place.markerStyle, item.collection, size = 48.dp)
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DirectionsButton(
                    place = place,
                    onError = onDirectionsError,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = onOpenDetails, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Details")
                }
            }
        }

        HorizontalDivider()

        // The rest, revealed by dragging the sheet up. Same components as the Place detail screen.
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            PhotosSection(place.photos)
            DetailsCard(place, item.collection)
        }
    }
}
