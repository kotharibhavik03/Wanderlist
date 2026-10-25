package com.rizz.wanderlist.views.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rizz.wanderlist.domain.model.Journey
import com.rizz.wanderlist.domain.model.JourneyStop
import com.rizz.wanderlist.util.formatDate
import com.rizz.wanderlist.util.formatLocalDate
import com.rizz.wanderlist.util.pluralize
import com.rizz.wanderlist.views.photo.PhotoTile
import com.rizz.wanderlist.views.photo.PhotoViewer
import com.rizz.wanderlist.views.photo.ViewerPhoto
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt

@Composable
fun JourneyReplayRoute(
    collectionId: String,
    onBack: () -> Unit,
    onPlaceClick: (placeId: String) -> Unit
) {
    val viewModel: JourneyReplayViewModel = koinViewModel(parameters = { parametersOf(collectionId) })
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    JourneyReplayScreen(
        uiState = uiState,
        onBack = onBack,
        onStart = viewModel::onStart,
        onNext = viewModel::onNext,
        onPrevious = viewModel::onPrevious,
        onStopClick = viewModel::onStopSelected,
        onPlaceClick = onPlaceClick
    )
}

/**
 * A full-screen map of the journey with a card at the bottom: the whole trip first, then one
 * stop at a time (previous / next, or tap a numbered marker), then the end.
 */
@Composable
fun JourneyReplayScreen(
    uiState: JourneyReplayUiState,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onStopClick: (index: Int) -> Unit,
    onPlaceClick: (placeId: String) -> Unit
) {
    val journey = uiState.journey
    val density = LocalDensity.current
    // The panels' measured heights become the map's padding, so the camera frames the route in
    // the part of the map they leave visible. Both panels keep one height whatever they show.
    var topPanelHeight by remember { mutableIntStateOf(0) }
    var bottomPanelHeight by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            uiState.collection == null -> Message("This collection no longer exists.")
            !journey.canReplay -> Message(
                "A journey needs at least ${Journey.MIN_STOPS} visited places. " +
                    "Mark places in this collection as visited to replay it."
            )
            else -> {
                JourneyMap(
                    journey = journey,
                    collection = uiState.collection,
                    position = uiState.position,
                    reachedStops = uiState.reachedStops,
                    onStopClick = onStopClick,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = with(density) {
                        PaddingValues(top = topPanelHeight.toDp(), bottom = bottomPanelHeight.toDp())
                    }
                )
                ReplayCard(
                    uiState = uiState,
                    onStart = onStart,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onPlaceClick = onPlaceClick,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { bottomPanelHeight = it.height }
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(12.dp)
                )
            }
        }

        // Drawn last, so it's on top of the map and usable in every state.
        TitleBar(
            title = uiState.collection?.name ?: "Journey",
            onBack = onBack,
            modifier = Modifier
                .onSizeChanged { topPanelHeight = it.height }
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(12.dp)
        )
    }
}

/** Back, and the collection's name, floating over the map. */
@Composable
private fun TitleBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, shadowElevation = 4.dp) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
        Spacer(Modifier.width(8.dp))
        Surface(shape = MaterialTheme.shapes.large, shadowElevation = 4.dp) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

/**
 * The card under the map. Every state has the same rows (progress, label, title, subtitle, a
 * 72dp media row, the controls), each one line or a fixed height, so the card never changes
 * height: its height is the map's padding, and a change would move the camera.
 */
@Composable
private fun ReplayCard(
    uiState: JourneyReplayUiState,
    onStart: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onPlaceClick: (placeId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val journey = uiState.journey
    val stop = uiState.currentStop
    val position = uiState.position
    var viewingPhoto by remember { mutableStateOf<Int?>(null) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            LinearProgressIndicator(
                progress = { uiState.reachedStops / journey.stops.size.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            // Label, title and subtitle. On a stop, tapping them opens the place.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (stop != null) Modifier.clickable { onPlaceClick(stop.place.id) } else Modifier)
            ) {
                Text(
                    text = when (position) {
                        JourneyPosition.Overview -> "Your journey"
                        is JourneyPosition.AtStop -> stop?.let { stopLabel(journey, it) }.orEmpty()
                        JourneyPosition.Finished -> "Journey complete"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stop?.place?.displayName ?: uiState.collection?.name.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (stop != null) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Open place",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = if (stop != null) stopSubtitle(journey, stop) else journeySummary(journey),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(12.dp))

            Box(modifier = Modifier.fillMaxWidth().height(MediaRowHeight)) {
                if (stop != null) {
                    StopPhotos(stop, onPhotoClick = { viewingPhoto = it })
                } else {
                    JourneyDates(journey)
                }
            }
            Spacer(Modifier.height(8.dp))

            Controls(
                position = position,
                reachedStops = uiState.reachedStops,
                totalStops = journey.stops.size,
                onStart = onStart,
                onNext = onNext,
                onPrevious = onPrevious
            )
        }
    }

    val viewing = viewingPhoto
    if (stop != null && viewing != null) {
        PhotoViewer(
            photos = stop.place.photos.map { ViewerPhoto(it, caption = stop.place.displayName) },
            initialIndex = viewing,
            onDismiss = { viewingPhoto = null }
        )
    }
}

/** The stop's photos in a row; tap one to see it full screen. */
@Composable
private fun StopPhotos(stop: JourneyStop, onPhotoClick: (index: Int) -> Unit) {
    val photos = stop.place.photos
    if (photos.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No photos at this stop",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(photos, key = { _, photo -> photo.id }) { index, photo ->
                PhotoTile(
                    photo = photo,
                    description = "Photo ${index + 1} of ${photos.size} at ${stop.place.displayName}",
                    onClick = { onPhotoClick(index) },
                    modifier = Modifier.size(MediaRowHeight)
                )
            }
        }
    }
}

/**
 * When the journey happened (its visit dates), and how stops without one are placed. With no
 * visit dates at all, it says the order is the saving order.
 */
@Composable
private fun JourneyDates(journey: Journey) {
    val visits = journey.stops.filter { it.hasVisitDate }.map { it.date }
    val undated = journey.stopsWithoutVisitDate
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            when {
                visits.isEmpty() -> "In the order you saved them"
                visits.first() == visits.last() -> formatDate(visits.first())
                else -> "${formatDate(visits.first())} – ${formatDate(visits.last())}"
            },
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (undated > 0) {
            Text(
                if (visits.isEmpty()) "Add visit dates to put them in travel order"
                else "${pluralize(undated, "stop")} without a visit date, placed by when saved",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Previous and next either side of the main action: "Start journey" on the overview, "Replay"
 * at the end, and how far along the replay is in between.
 */
@Composable
private fun Controls(
    position: JourneyPosition,
    reachedStops: Int,
    totalStops: Int,
    onStart: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, enabled = position != JourneyPosition.Overview) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous stop")
        }
        Box(modifier = Modifier.weight(1f).height(48.dp), contentAlignment = Alignment.Center) {
            when (position) {
                JourneyPosition.Overview -> Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Text("Start journey", maxLines = 1)
                }
                is JourneyPosition.AtStop -> Text(
                    "Stop $reachedStops of $totalStops",
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center
                )
                JourneyPosition.Finished -> Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Replay", maxLines = 1)
                }
            }
        }
        IconButton(onClick = onNext, enabled = position != JourneyPosition.Finished) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next stop")
        }
    }
}

/** A message in the middle of the screen, for when there's no journey to show. */
@Composable
private fun Message(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

/** "8 stops · 1,240 km · 23 days" (the days only when every stop has a visit date). */
private fun journeySummary(journey: Journey): String = listOfNotNull(
    pluralize(journey.stops.size, "stop"),
    formatKm(journey.distanceKm),
    journey.days?.let { pluralize(it, "day") }
).joinToString(" · ")

/**
 * "Day 5 · 24 Oct 2026" when the whole journey has visit dates, "Visited 24 Oct 2026" for a
 * dated stop among undated ones, and "Saved 12 Mar 2026" for a stop placed by when it was saved
 * (never passed off as a visit date).
 */
private fun stopLabel(journey: Journey, stop: JourneyStop): String {
    val day = journey.dayOf(stop)
    return when {
        day != null -> "Day $day · ${formatDate(stop.date)}"
        stop.hasVisitDate -> "Visited ${formatDate(stop.date)}"
        else -> "Saved ${formatLocalDate(stop.date)}"
    }
}

/** How far this stop was from the one before, or that it's where the journey began. */
private fun stopSubtitle(journey: Journey, stop: JourneyStop): String {
    val km = stop.kmFromPrevious ?: return "Where the journey began"
    val index = journey.stops.indexOf(stop)
    val previous = journey.stops.getOrNull(index - 1)?.place?.displayName ?: return formatKm(km)
    return "${formatKm(km)} from $previous"
}

/** "650 m", "4.2 km", "42 km", "1,240 km". */
private fun formatKm(km: Double): String = when {
    km < 1 -> "${(km * 1000).roundToInt()} m"
    km < 10 -> "${(km * 10).roundToInt() / 10.0} km"
    else -> "${groupThousands(km.roundToInt())} km"
}

private fun groupThousands(value: Int): String =
    value.toString().reversed().chunked(3).joinToString(",").reversed()

/** Photos and the dates box share this height, so the card is the same height either way. */
private val MediaRowHeight = 72.dp
