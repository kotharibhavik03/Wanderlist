@file:OptIn(ExperimentalMaterial3Api::class)

package com.rizz.wanderlist.views.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rizz.wanderlist.domain.model.Place
import com.rizz.wanderlist.domain.model.PlaceCollection
import com.rizz.wanderlist.util.formatCoordinates
import com.rizz.wanderlist.util.formatDate
import com.rizz.wanderlist.util.pluralize
import com.rizz.wanderlist.views.photo.PhotoTile
import com.rizz.wanderlist.views.photo.PhotoViewer
import com.rizz.wanderlist.views.photo.ViewerPhoto
import com.rizz.wanderlist.views.place.MarkerBadge
import com.rizz.wanderlist.views.place.PlaceMarker
import com.rizz.wanderlist.views.place.PlaceStatusBadge
import com.rizz.wanderlist.views.place.collectionBrush
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CollectionDetailRoute(
    collectionId: String,
    onBack: () -> Unit,
    onPlaceClick: (placeId: String) -> Unit,
    onAddPlace: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit
) {
    val viewModel: CollectionDetailViewModel =
        koinViewModel(parameters = { parametersOf(collectionId) })
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnDeleted by rememberUpdatedState(onDeleted)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                CollectionDetailEvent.Deleted -> currentOnDeleted()
            }
        }
    }

    CollectionDetailScreen(
        uiState = uiState,
        onBack = onBack,
        onPlaceClick = onPlaceClick,
        onAddPlace = onAddPlace,
        onEdit = onEdit,
        onDeleteConfirmed = viewModel::onDeleteConfirmed,
        onErrorShown = viewModel::onErrorShown
    )
}

@Composable
fun CollectionDetailScreen(
    uiState: CollectionDetailUiState,
    onBack: () -> Unit,
    onPlaceClick: (placeId: String) -> Unit,
    onAddPlace: () -> Unit,
    onEdit: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onErrorShown: () -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    val collection = uiState.collection
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onErrorShown()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(collection?.name ?: "Collection") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (collection != null) {
                        Box {
                            IconButton(onClick = { showMenu = true }, enabled = !uiState.isDeleting) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Edit collection") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onEdit()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete collection", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        showDeleteDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (collection != null && !uiState.isDeleting) {
                ExtendedFloatingActionButton(
                    onClick = onAddPlace,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add place") }
                )
            }
        }
    ) { padding ->
        when {
            uiState.isLoading || uiState.isDeleting -> Centered(padding) { CircularProgressIndicator() }
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
                    PlacesTab(collection, uiState.places, onPlaceClick)
                } else {
                    PhotosTab(uiState.photos)
                }
            }
        }
    }

    if (showDeleteDialog && collection != null) {
        val placeCount = uiState.places.size
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    if (placeCount == 0) "Delete \"${collection.name}\"?"
                    else "Delete \"${collection.name}\" and its ${pluralize(placeCount, "place")}?"
                )
            },
            text = {
                Text(
                    if (placeCount == 0) "This can't be undone."
                    else "Its places and their photos will be deleted too, and anyone they were shared " +
                            "with will lose access. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDeleteConfirmed()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
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
            Text(collection.name, style = MaterialTheme.typography.titleLarge)
            Text(
                "${pluralize(placeCount, "place")} · ${pluralize(photoCount, "photo")}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlacesTab(
    collection: PlaceCollection,
    places: List<Place>,
    onPlaceClick: (placeId: String) -> Unit
) {
    if (places.isEmpty()) {
        EmptyState("No places yet", "Tap Add place to save your first one here.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(places, key = { it.id }) { place ->
            PlaceCard(place, collection, onClick = { onPlaceClick(place.id) })
        }
    }
}

/** A place card's details: visit date, rating (written by [rating]), photos and people. */
private fun metaOf(place: Place, rating: (Int) -> String): List<String> = buildList {
    place.visitedOnEpochMillis?.let { add(formatDate(it)) }
    place.rating?.let { add(rating(it)) }
    if (place.photos.isNotEmpty()) add(pluralize(place.photos.size, "photo"))
    if (place.members.isNotEmpty()) add(pluralize(place.members.size, "person", "people"))
}

@Composable
private fun PlaceCard(place: Place, collection: PlaceCollection, onClick: () -> Unit) {
    // Shown with a star; read aloud as words ("black star 4" means nothing). When the line wraps,
    // it wraps between items: non-breaking spaces keep "2 photos" and "24 Oct 2026" whole.
    val meta = metaOf(place, rating = { "★ $it" }).joinToString(" · ") { it.replace(' ', '\u00A0') }
    val spokenMeta = metaOf(place, rating = { "rated $it out of 5" }).joinToString(", ")

    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PlaceMarker(place.markerStyle, collection, place.markerPhoto)
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
                // The details go next to the status badge, or below it when they don't fit
                // there (a large font), instead of being cut off.
                FlowRow(
                    modifier = Modifier.padding(top = 6.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PlaceStatusBadge(place.status)
                    if (meta.isNotEmpty()) {
                        Text(
                            meta,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            // Replaces what's read for this line, so only the spoken version is heard.
                            modifier = Modifier.clearAndSetSemantics { contentDescription = spokenMeta }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Every photo in the collection's places as a gallery: three square, sharp-cornered tiles per
 * row with thin gaps, like a phone's photo grid. Tapping one opens the viewer, captioned with
 * its place.
 */
@Composable
private fun PhotosTab(photos: List<CollectionPhoto>) {
    var viewing by rememberSaveable { mutableStateOf<Int?>(null) }
    if (photos.isEmpty()) {
        EmptyState("No photos yet", "Photos you add to places in this collection appear here.")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(PhotoGridColumns),
        modifier = Modifier.fillMaxSize(),
        // The bottom leaves room to scroll the last row clear of the Add place button.
        contentPadding = PaddingValues(start = PhotoGridGap, end = PhotoGridGap, top = PhotoGridGap, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(PhotoGridGap),
        verticalArrangement = Arrangement.spacedBy(PhotoGridGap)
    ) {
        itemsIndexed(photos, key = { _, item -> "${item.placeId}/${item.photo.id}" }) { index, item ->
            PhotoTile(
                photo = item.photo,
                description = "Photo from ${item.placeName}",
                onClick = { viewing = index },
                // Square tiles that fill their grid cell.
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                shape = RectangleShape
            )
        }
    }
    viewing?.let { index ->
        PhotoViewer(
            photos = photos.map { ViewerPhoto(it.photo, caption = it.placeName) },
            initialIndex = index,
            onDismiss = { viewing = null }
        )
    }
}

private const val PhotoGridColumns = 3

/** Gap between photo tiles, and around the grid. */
private val PhotoGridGap = 3.dp

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
