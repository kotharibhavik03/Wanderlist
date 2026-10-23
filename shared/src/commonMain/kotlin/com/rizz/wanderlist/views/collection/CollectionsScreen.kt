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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rizz.wanderlist.domain.model.PlaceCollection
import com.rizz.wanderlist.util.pluralize
import com.rizz.wanderlist.views.place.MarkerBadge
import com.rizz.wanderlist.views.place.collectionBrush
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CollectionsRoute(
    onAddClick: () -> Unit,
    onCollectionClick: (collectionId: String) -> Unit,
    onSignOutClick: () -> Unit,
    viewModel: CollectionsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CollectionsScreen(
        uiState = uiState,
        onAddClick = onAddClick,
        onCollectionClick = onCollectionClick,
        onSignOutClick = onSignOutClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    uiState: CollectionsUiState,
    onAddClick: () -> Unit,
    onCollectionClick: (collectionId: String) -> Unit,
    onSignOutClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wanderlist") },
                actions = { TextButton(onClick = onSignOutClick) { Text("Sign out") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = "New collection")
            }
        }
    ) { padding ->
        when (uiState) {
            CollectionsUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            is CollectionsUiState.Content ->
                if (uiState.collections.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("No collections yet. Tap the add button to create one.") }
                } else {
                    LazyColumn(
                        modifier = Modifier.padding(padding),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.collections, key = { it.collection.id }) { summary ->
                            CollectionCard(
                                collection = summary.collection,
                                placeCount = summary.placeCount,
                                onClick = { onCollectionClick(summary.collection.id) }
                            )
                        }
                    }
                }
        }
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
