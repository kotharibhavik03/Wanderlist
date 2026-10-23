@file:OptIn(ExperimentalMaterial3Api::class)

package com.rizz.wanderlist.views.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rizz.wanderlist.domain.model.Place
import com.rizz.wanderlist.domain.model.PlaceCollection
import com.rizz.wanderlist.domain.model.PlaceStatus
import com.rizz.wanderlist.util.formatCoordinates
import com.rizz.wanderlist.util.formatDate
import com.rizz.wanderlist.util.pluralize
import com.rizz.wanderlist.views.place.MarkerBadge
import com.rizz.wanderlist.views.place.PlaceMarker
import com.rizz.wanderlist.views.place.collectionBrush
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CollectionDetailRoute(
    collectionId: String,
    onBack: () -> Unit,
    onAddPlace: () -> Unit
) {
    val viewModel: CollectionDetailViewModel =
        koinViewModel(parameters = { parametersOf(collectionId) })
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CollectionDetailScreen(uiState, onBack, onAddPlace)
}

@Composable
fun CollectionDetailScreen(
    uiState: CollectionDetailUiState,
    onBack: () -> Unit,
    onAddPlace: () -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val collection = uiState.collection

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(collection?.name ?: "Collection") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (collection != null) {
                ExtendedFloatingActionButton(
                    onClick = onAddPlace,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add place") }
                )
            }
        }
    ) { padding ->
        when {
            uiState.isLoading -> Centered(padding) { CircularProgressIndicator() }
            collection == null -> Centered(padding) { Text("This collection no longer exists.") }
            else -> Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                Header(collection, uiState.places.size, uiState.photos.size)
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        text = { Text("Places (${uiState.places.size})") }
                    )
                    Tab(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        text = { Text("Photos (${uiState.photos.size})") }
                    )
                }
                if (tab == 0) {
                    PlacesTab(collection, uiState.places)
                } else {
                    PhotosTab(uiState.photos)
                }
            }
        }
    }
}

@Composable
private fun Centered(padding: PaddingValues, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun Header(collection: PlaceCollection, placeCount: Int, photoCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        MarkerBadge(
            emoji = collection.icon,
            brush = collectionBrush(collection.colors, MaterialTheme.colorScheme.primary),
            size = 56.dp
        )
        Column {
            Text(collection.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "${pluralize(placeCount, "place")} · ${pluralize(photoCount, "photo")}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlacesTab(collection: PlaceCollection, places: List<Place>) {
    if (places.isEmpty()) {
        EmptyState("No places yet", "Tap Add place to save your first one here.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(places, key = { it.id }) { PlaceCard(it, collection) }
    }
}

@Composable
private fun PlaceCard(place: Place, collection: PlaceCollection) {
    val meta = buildList {
        add(if (place.status == PlaceStatus.VISITED) "Visited" else "Want to visit")
        place.visitedOnEpochMillis?.let { add(formatDate(it)) }
        place.rating?.let { add("★ $it") }
        if (place.photos.isNotEmpty()) add(pluralize(place.photos.size, "photo"))
        if (place.members.isNotEmpty()) add(pluralize(place.members.size, "person", "people"))
    }.joinToString(" · ")

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PlaceMarker(place.markerStyle, collection)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    place.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    place.address ?: formatCoordinates(place.latitude, place.longitude),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun PhotosTab(photos: List<CollectionPhoto>) {
    if (photos.isEmpty()) {
        EmptyState("No photos yet", "Photos you add to places in this collection appear here.")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(110.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(photos, key = { "${it.placeId}/${it.photo.id}" }) { item ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .size(110.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .semantics { contentDescription = "Photo from ${item.placeName}" },
                contentAlignment = Alignment.Center
            ) {
                // Real image loading (Coil) arrives with the photo step.
                Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.photo.isPrivate) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Private",
                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
