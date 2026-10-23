package com.rizz.wanderlist.views.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rizz.wanderlist.util.formatCoordinates
import com.rizz.wanderlist.util.pluralize
import com.rizz.wanderlist.views.place.PlaceMarker
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeRoute(
    onAddClick: () -> Unit,
    onPlaceClick: (placeId: String) -> Unit,
    viewModel: HomeViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        onAddClick = onAddClick,
        onPlaceClick = onPlaceClick,
        onQueryChange = viewModel::onQueryChange,
        onAllSelected = viewModel::onAllSelected,
        onCollectionToggled = viewModel::onCollectionToggled,
        onSharedToggled = viewModel::onSharedToggled
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onAddClick: () -> Unit,
    onPlaceClick: (placeId: String) -> Unit,
    onQueryChange: (String) -> Unit,
    onAllSelected: () -> Unit,
    onCollectionToggled: (String) -> Unit,
    onSharedToggled: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Scaffold(
        containerColor = colors.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add a place")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(bottom = 8.dp)
        ) {
            Header(placeCount = uiState.places.size, isFiltered = !uiState.showingAll)
            SearchField(query = uiState.query, onQueryChange = onQueryChange)
            Spacer(Modifier.height(8.dp))
            FilterChips(uiState, onAllSelected, onCollectionToggled, onSharedToggled)
            Spacer(Modifier.height(8.dp))

            when {
                uiState.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                uiState.isSearching -> SearchResults(uiState, onPlaceClick)

                else -> PlacesMap(
                    places = uiState.places,
                    onMarkerClick = onPlaceClick,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun Header(placeCount: Int, isFiltered: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Wanderlist",
                style = MaterialTheme.typography.titleLarge,
                color = colors.onBackground
            )
            Text(
                text = "Your world, your memories",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }

        // Read as one phrase, e.g. "3 places, filtered".
        val description = pluralize(placeCount, "place") + if (isFiltered) ", filtered" else ""
        Surface(
            color = colors.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.clearAndSetSemantics { contentDescription = description }
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = placeCount.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.primary
                )
                Text(
                    text = if (placeCount == 1) "Place" else "Places",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurface
                )
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        placeholder = { Text("Search places, addresses, collections") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                }
            }
        }
    )
}

/** All · each collection · Shared with me. Several can be on; none means All. */
@Composable
private fun FilterChips(
    uiState: HomeUiState,
    onAllSelected: () -> Unit,
    onCollectionToggled: (String) -> Unit,
    onSharedToggled: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = uiState.showingAll,
            onClick = onAllSelected,
            label = { Text("All") }
        )
        uiState.collections.forEach { summary ->
            val collection = summary.collection
            FilterChip(
                selected = collection.id in uiState.selectedCollectionIds,
                onClick = { onCollectionToggled(collection.id) },
                label = { Text("${collection.name} (${summary.placeCount})") },
                // The emoji is decorative; the chip is read by its name.
                leadingIcon = { Text(collection.icon, modifier = Modifier.clearAndSetSemantics { }) }
            )
        }
        if (uiState.sharedCount > 0) {
            FilterChip(
                selected = uiState.sharedSelected,
                onClick = onSharedToggled,
                label = { Text("Shared with me (${uiState.sharedCount})") },
                leadingIcon = { Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }
    }
}

@Composable
private fun SearchResults(uiState: HomeUiState, onPlaceClick: (placeId: String) -> Unit) {
    if (uiState.searchResults.isEmpty()) {
        CenteredMessage(
            title = "No matches",
            message = "No places match \"${uiState.query.trim()}\"" +
                    if (uiState.showingAll) "." else " in the selected filters."
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
        items(uiState.searchResults, key = { it.place.id }) { item ->
            val place = item.place
            ListItem(
                headlineContent = {
                    Text(place.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = {
                    Text(
                        place.address ?: formatCoordinates(place.latitude, place.longitude),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingContent = { PlaceMarker(place.markerStyle, item.collection) },
                trailingContent = {
                    Text(
                        item.collection?.name ?: "Shared",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onPlaceClick(place.id) }
            )
        }
    }
}

@Composable
private fun CenteredMessage(title: String, message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
