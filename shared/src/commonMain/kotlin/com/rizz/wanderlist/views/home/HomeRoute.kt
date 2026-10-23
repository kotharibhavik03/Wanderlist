package com.rizz.wanderlist.views.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rizz.wanderlist.util.formatCoordinates
import com.rizz.wanderlist.util.pluralize
import com.rizz.wanderlist.views.place.PlaceMarker
import org.koin.compose.viewmodel.koinViewModel

/** Space at the bottom of the map kept clear for the add button, so framing doesn't hide markers under it. */
private val AddButtonClearance = 88.dp

@Composable
fun HomeRoute(
    /** Height of the floating bottom bar. The map runs underneath it; controls stay above it. */
    bottomBarPadding: Dp,
    onAddClick: () -> Unit,
    onPlaceClick: (placeId: String) -> Unit,
    viewModel: HomeViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        bottomBarPadding = bottomBarPadding,
        onAddClick = onAddClick,
        onPlaceClick = onPlaceClick,
        onQueryChange = viewModel::onQueryChange,
        onAllSelected = viewModel::onAllSelected,
        onCollectionToggled = viewModel::onCollectionToggled,
        onSharedToggled = viewModel::onSharedToggled
    )
}

/**
 * The map fills the screen, behind the status bar. The app name, search and filters float on top of it,
 * as in Google Maps; search results open in a panel over the map.
 */
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    bottomBarPadding: Dp,
    onAddClick: () -> Unit,
    onPlaceClick: (placeId: String) -> Unit,
    onQueryChange: (String) -> Unit,
    onAllSelected: () -> Unit,
    onCollectionToggled: (String) -> Unit,
    onSharedToggled: () -> Unit
) {
    val density = LocalDensity.current
    // Height of the floating controls, measured from the top of the screen. The map keeps its
    // framing and Google's logo clear of them, and search results open just below them.
    var controlsHeight by remember { mutableStateOf(0.dp) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        PlacesMap(
            places = uiState.places,
            onMarkerClick = onPlaceClick,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = controlsHeight, bottom = bottomBarPadding + AddButtonClearance)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Measured before the status-bar padding, so it includes it.
                .onSizeChanged { controlsHeight = with(density) { it.height.toDp() } }
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 8.dp, bottom = 8.dp)
        ) {
            TopCard(
                query = uiState.query,
                placeCount = uiState.places.size,
                isFiltered = !uiState.showingAll,
                onQueryChange = onQueryChange
            )
            FilterChips(uiState, onAllSelected, onCollectionToggled, onSharedToggled)
        }

        if (uiState.isSearching) {
            SearchResults(
                uiState = uiState,
                onPlaceClick = onPlaceClick,
                topOffset = controlsHeight,
                bottomOffset = bottomBarPadding
            )
        }

        FloatingActionButton(
            onClick = onAddClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = bottomBarPadding)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add a place")
        }
    }
}

/** The app name and how many places are on the map, with the search field below, in one floating card. */
@Composable
private fun TopCard(
    query: String,
    placeCount: Int,
    isFiltered: Boolean,
    onQueryChange: (String) -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Wanderlist", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Your world, your memories",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                PlaceCountPill(placeCount, isFiltered)
            }
            TextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = {
                    Text(
                        "Search places, addresses, collections",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                // The card provides the background and shape.
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** "12 places", following the filter chips. Read as one phrase, e.g. "3 places, filtered". */
@Composable
private fun PlaceCountPill(placeCount: Int, isFiltered: Boolean) {
    val label = pluralize(placeCount, "place")
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = label + if (isFiltered) ", filtered" else ""
        }
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/** All · each collection · Shared with me. Several can be on; none means All. */
@Composable
private fun FilterChips(
    uiState: HomeUiState,
    onAllSelected: () -> Unit,
    onCollectionToggled: (String) -> Unit,
    onSharedToggled: () -> Unit
) {
    // Solid and slightly raised, so chips stay readable over any part of the map.
    val chipColors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface)
    val chipElevation = FilterChipDefaults.filterChipElevation(elevation = 2.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = uiState.showingAll,
            onClick = onAllSelected,
            label = { Text("All") },
            colors = chipColors,
            elevation = chipElevation
        )
        uiState.collections.forEach { summary ->
            val collection = summary.collection
            FilterChip(
                selected = collection.id in uiState.selectedCollectionIds,
                onClick = { onCollectionToggled(collection.id) },
                label = { Text("${collection.name} (${summary.placeCount})") },
                // The emoji is decorative; the chip is read by its name.
                leadingIcon = { Text(collection.icon, modifier = Modifier.clearAndSetSemantics { }) },
                colors = chipColors,
                elevation = chipElevation
            )
        }
        if (uiState.sharedCount > 0) {
            FilterChip(
                selected = uiState.sharedSelected,
                onClick = onSharedToggled,
                label = { Text("Shared with me (${uiState.sharedCount})") },
                leadingIcon = { Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(18.dp)) },
                colors = chipColors,
                elevation = chipElevation
            )
        }
    }
}

/** Results in a rounded panel over the map, starting just below the floating controls. */
@Composable
private fun SearchResults(
    uiState: HomeUiState,
    onPlaceClick: (placeId: String) -> Unit,
    topOffset: Dp,
    bottomOffset: Dp
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topOffset, bottom = bottomOffset)
            .imePadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        if (uiState.searchResults.isEmpty()) {
            CenteredMessage(
                title = "No matches",
                message = "No places match \"${uiState.query.trim()}\"" +
                        if (uiState.showingAll) "." else " in the selected filters."
            )
            return@Surface
        }
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
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
