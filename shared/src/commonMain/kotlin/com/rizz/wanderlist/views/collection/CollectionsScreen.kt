package com.rizz.wanderlist.views.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rizz.wanderlist.domain.model.CollectionSummary
import com.rizz.wanderlist.domain.model.PlaceCollection
import com.rizz.wanderlist.domain.model.SharedPlace
import com.rizz.wanderlist.util.formatCoordinates
import com.rizz.wanderlist.util.pluralize
import com.rizz.wanderlist.views.place.MarkerBadge
import com.rizz.wanderlist.views.place.PlaceMarker
import com.rizz.wanderlist.views.place.collectionBrush
import com.rizz.wanderlist.views.place.title
import org.koin.compose.viewmodel.koinViewModel

/** The two halves of the Collections tab. */
private enum class CollectionsSegment(val label: String) {
    MINE("My collections"),
    SHARED("Shared with me")
}

@Composable
fun CollectionsRoute(
    onAddClick: () -> Unit,
    onCollectionClick: (collectionId: String) -> Unit,
    onSharedPlaceClick: (placeId: String) -> Unit,
    viewModel: CollectionsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CollectionsScreen(
        uiState = uiState,
        onAddClick = onAddClick,
        onCollectionClick = onCollectionClick,
        onSharedPlaceClick = onSharedPlaceClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    uiState: CollectionsUiState,
    onAddClick: () -> Unit,
    onCollectionClick: (collectionId: String) -> Unit,
    onSharedPlaceClick: (placeId: String) -> Unit,
) {
    var segment by rememberSaveable { mutableStateOf(CollectionsSegment.MINE) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Collections") })
        },
        floatingActionButton = {
            // You can only create collections of your own.
            if (segment == CollectionsSegment.MINE) {
                FloatingActionButton(onClick = onAddClick) {
                    Icon(Icons.Default.Add, contentDescription = "New collection")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SegmentSwitcher(
                selected = segment,
                onSelect = { segment = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            when (uiState) {
                CollectionsUiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                is CollectionsUiState.Content -> when (segment) {
                    CollectionsSegment.MINE -> MyCollections(uiState.collections, onCollectionClick)
                    CollectionsSegment.SHARED -> SharedWithMe(uiState.sharedPlaces, onSharedPlaceClick)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SegmentSwitcher(
    selected: CollectionsSegment,
    onSelect: (CollectionsSegment) -> Unit,
    modifier: Modifier = Modifier
) {
    val segments = CollectionsSegment.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        segments.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = segments.size)
            ) { Text(option.label) }
        }
    }
}

@Composable
private fun MyCollections(
    collections: List<CollectionSummary>,
    onCollectionClick: (collectionId: String) -> Unit
) {
    if (collections.isEmpty()) {
        EmptyMessage("No collections yet. Tap the add button to create one.")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(collections, key = { it.collection.id }) { summary ->
            CollectionCard(
                collection = summary.collection,
                placeCount = summary.placeCount,
                onClick = { onCollectionClick(summary.collection.id) }
            )
        }
    }
}

@Composable
private fun SharedWithMe(
    sharedPlaces: List<SharedPlace>,
    onPlaceClick: (placeId: String) -> Unit
) {
    if (sharedPlaces.isEmpty()) {
        EmptyMessage(
            "Nothing shared with you yet. When someone adds your Google email to a place, it appears here."
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(sharedPlaces, key = { it.place.id }) { shared ->
            SharedPlaceCard(shared, onClick = { onPlaceClick(shared.place.id) })
        }
    }
}

/** A place shared with me. Its collection is private to the owner, so the marker falls back to a pin. */
@Composable
private fun SharedPlaceCard(shared: SharedPlace, onClick: () -> Unit) {
    val place = shared.place
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PlaceMarker(place.markerStyle, collection = null)
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
                Text(
                    "You: ${shared.myRole.title()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * One collection row. Also used as the live preview in Create Collection,
 * where [onClick] is null so the card is not tappable.
 */
@Composable
fun CollectionCard(
    collection: PlaceCollection,
    placeCount: Int,
    onClick: (() -> Unit)? = null
) {
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MarkerBadge(
                emoji = collection.icon,
                brush = collectionBrush(collection.colors, MaterialTheme.colorScheme.primary),
                size = 56.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    collection.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    pluralize(placeCount, "place"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onClick != null) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
    }

    if (onClick != null) {
        Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) { content() }
    } else {
        Card(modifier = Modifier.fillMaxWidth()) { content() }
    }
}
