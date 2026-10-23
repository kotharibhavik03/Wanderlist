package com.rizz.wanderlist.views.home

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.rizz.wanderlist.domain.model.SelectedLocation
import com.rizz.wanderlist.util.formatCoordinates
import com.rizz.wanderlist.util.pluralize
import com.rizz.wanderlist.views.location.MyLocationButton
import com.rizz.wanderlist.views.location.rememberMyLocationClick
import com.rizz.wanderlist.views.place.PlaceMarker
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/** Space at the bottom of the map kept clear for the add button, so framing doesn't hide markers under it. */
private val AddButtonClearance = 88.dp

/** Puts the my-location button just above the + button (16dp margin + 56dp button + 12dp gap). */
private val MyLocationAboveAddButton = 84.dp

/** Height of Material's default sheet drag handle: a 4dp bar with 22dp above and below. */
private val SheetDragHandleHeight = 48.dp

@Composable
fun HomeRoute(
    /** Height of the floating bottom bar. The map runs underneath it; controls stay above it. */
    bottomBarPadding: Dp,
    onAddClick: () -> Unit,
    onPlaceClick: (placeId: String) -> Unit,
    /** Save a searched location: opens Add Place with it filled in. */
    onSaveLocation: (SelectedLocation) -> Unit,
    viewModel: HomeViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedPlacePhotos by viewModel.selectedPlacePhotos.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        selectedPlacePhotos = selectedPlacePhotos,
        onSelectedPlaceChanged = viewModel::onSelectedPlaceChanged,
        bottomBarPadding = bottomBarPadding,
        onAddClick = onAddClick,
        onPlaceClick = onPlaceClick,
        onSaveLocation = onSaveLocation,
        onQueryChange = viewModel::onQueryChange,
        onAllSelected = viewModel::onAllSelected,
        onCollectionToggled = viewModel::onCollectionToggled,
        onSharedToggled = viewModel::onSharedToggled,
        onLocationPicked = viewModel::onLocationPicked,
        onPickedLocationCleared = viewModel::onPickedLocationCleared,
        hasLocationPermission = viewModel::hasLocationPermission,
        onPermissionAnswered = viewModel::onPermissionAnswered,
        onPermissionRefused = viewModel::onPermissionRefused,
        onMyLocation = viewModel::goToMyLocation,
        claimStartLocation = viewModel::claimStartLocation,
        onLocateAtStart = viewModel::locateAtStart,
        onStartLocationUnavailable = viewModel::onStartLocationUnavailable,
        onMyLocationShown = viewModel::onMyLocationShown,
        onMessageShown = viewModel::onMessageShown
    )
}

/** What the bottom sheet is about: one of the user's places, or a location found by search. */
private sealed interface SheetSubject {
    /** Identifies the subject, so a new one gets fresh sheet content and is measured before opening. */
    val key: String

    data class SavedPlace(val item: HomePlace) : SheetSubject {
        override val key get() = "place:${item.place.id}"
    }

    data class FoundLocation(val location: SelectedLocation) : SheetSubject {
        override val key get() = "location:${location.latitude},${location.longitude},${location.actualName}"
    }
}

/**
 * The map fills the screen, behind the status bar. The app name, search and filters float on top of it,
 * as in Google Maps; search results open in a panel over the map. Tapping a marker or a search
 * result opens a non-modal bottom sheet for it: the map stays usable behind it. At most one thing
 * is selected: a saved place or a searched location.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    selectedPlacePhotos: SelectedPlacePhotos,
    onSelectedPlaceChanged: (placeId: String?) -> Unit,
    bottomBarPadding: Dp,
    onAddClick: () -> Unit,
    onPlaceClick: (placeId: String) -> Unit,
    onSaveLocation: (SelectedLocation) -> Unit,
    onQueryChange: (String) -> Unit,
    onAllSelected: () -> Unit,
    onCollectionToggled: (String) -> Unit,
    onSharedToggled: () -> Unit,
    onLocationPicked: (SelectedLocation) -> Unit,
    onPickedLocationCleared: () -> Unit,
    hasLocationPermission: () -> Boolean,
    onPermissionAnswered: (granted: Boolean) -> Unit,
    onPermissionRefused: () -> Unit,
    onMyLocation: () -> Unit,
    /** True the first time Home appears in a session: then show the user's position. */
    claimStartLocation: () -> Boolean,
    onLocateAtStart: () -> Unit,
    onStartLocationUnavailable: () -> Unit,
    onMyLocationShown: () -> Unit,
    onMessageShown: () -> Unit
) {
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }
    val onMyLocationClick = rememberMyLocationClick(
        hasPermission = hasLocationPermission,
        onPermissionAnswered = onPermissionAnswered,
        onAllowed = onMyLocation,
        onRefused = onPermissionRefused
    )
    // Start on the user's position, once per session, asking for location access if needed.
    // A refusal isn't an error here: the map frames the places instead.
    val locateAtStart = rememberMyLocationClick(
        hasPermission = hasLocationPermission,
        onPermissionAnswered = onPermissionAnswered,
        onAllowed = onLocateAtStart,
        onRefused = onStartLocationUnavailable
    )
    LaunchedEffect(Unit) { if (claimStartLocation()) locateAtStart() }

    // Height of the floating controls, measured from the top of the screen. The map keeps its
    // framing and Google's logo clear of them, and search results open just below them.
    var controlsHeight by remember { mutableStateOf(0.dp) }
    // The measured summary height, with the subject it was measured for. The sheet peeks exactly
    // that far (plus its handle and the bar), and only opens once the current subject is measured.
    var measuredSummary by remember { mutableStateOf<Pair<String, Dp>?>(null) }
    val summaryHeight = measuredSummary?.second ?: 0.dp

    var selectedPlaceId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = uiState.places.firstOrNull { it.place.id == selectedPlaceId }
    LaunchedEffect(selected?.place?.id) { onSelectedPlaceChanged(selected?.place?.id) }
    val picked = uiState.pickedLocation
    val subject: SheetSubject? = when {
        selected != null -> SheetSubject.SavedPlace(selected)
        picked != null -> SheetSubject.FoundLocation(picked)
        else -> null
    }
    // The sheet shows the current subject straight away, and keeps showing the last one while it
    // slides away instead of collapsing empty.
    var lastSubject by remember { mutableStateOf<SheetSubject?>(null) }
    SideEffect { if (subject != null) lastSubject = subject }
    val shownSubject = subject ?: lastSubject
    // A selected place that disappears (filtered out, deleted, unshared) closes the sheet.
    LaunchedEffect(uiState.isLoading, selected == null) {
        if (!uiState.isLoading && selected == null) selectedPlaceId = null
    }

    fun selectPlace(placeId: String) {
        onPickedLocationCleared()
        selectedPlaceId = placeId
    }

    fun clearSelection() {
        selectedPlaceId = null
        onPickedLocationCleared()
    }

    // Starts hidden; Hidden must be an allowed state, so swiping down can close the sheet.
    val sheetState = rememberBottomSheetState(
        SheetValue.Hidden,
        setOf(SheetValue.Hidden, SheetValue.PartiallyExpanded, SheetValue.Expanded)
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    // Selection drives the sheet. It opens only once the new subject's summary is measured: if the
    // peek height changed mid-animation, the sheet would recompute its resting positions and snap
    // back to the nearest one, Hidden.
    val subjectKey = subject?.key
    LaunchedEffect(subjectKey) {
        if (subjectKey == null) {
            sheetState.hide()
        } else {
            snapshotFlow { measuredSummary?.first }.first { it == subjectKey }
            sheetState.partialExpand()
        }
    }
    // Swiping the sheet away clears the selection. currentValue only changes once the sheet has
    // settled, so animations in progress don't count. drop(1) skips the initial Hidden, so a
    // selection restored after rotation isn't cleared before the sheet reopens.
    LaunchedEffect(sheetState) {
        snapshotFlow { sheetState.currentValue }
            .drop(1)
            .collect { if (it == SheetValue.Hidden) clearSelection() }
    }

    // Back closes the sheet first, as in Google Maps, instead of leaving Home.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = subject != null,
        onBackCompleted = { clearSelection() }
    )

    // What's below the summary stays invisible until the sheet is pulled up: otherwise its top
    // shows through the gaps around the floating bottom bar. Alpha only, so the layout (and the
    // sheet's resting positions) never change.
    val detailsAlpha by animateFloatAsState(
        targetValue = if (sheetState.targetValue == SheetValue.Expanded) 1f else 0f,
        label = "sheet details"
    )

    val peekHeight = SheetDragHandleHeight + summaryHeight + bottomBarPadding
    val mapBottomPadding = if (subject != null) peekHeight else bottomBarPadding + AddButtonClearance
    val showError: (String) -> Unit = { message -> scope.launch { snackbarHostState.showSnackbar(message) } }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = peekHeight,
        snackbarHost = { SnackbarHost(snackbarHostState, Modifier.padding(bottom = bottomBarPadding)) },
        sheetContent = {
            val shown = shownSubject
            // A new key builds the content fresh, so its size is always reported, even when it
            // happens to match the previous subject's.
            if (shown != null) key(shown.key) {
                val onMeasured: (Dp) -> Unit = { height -> measuredSummary = shown.key to height }
                when (shown) {
                    is SheetSubject.SavedPlace -> PlaceSheetContent(
                        item = shown.item,
                        photos = selectedPlacePhotos.photos.takeIf { selectedPlacePhotos.placeId == shown.item.place.id }.orEmpty(),
                        bottomPadding = bottomBarPadding,
                        onSummaryHeightChanged = onMeasured,
                        detailsAlpha = { detailsAlpha },
                        onOpenDetails = { onPlaceClick(shown.item.place.id) },
                        onDirectionsError = showError
                    )
                    is SheetSubject.FoundLocation -> LocationSheetContent(
                        location = shown.location,
                        bottomPadding = bottomBarPadding,
                        onSummaryHeightChanged = onMeasured,
                        detailsAlpha = { detailsAlpha },
                        onSave = {
                            // Saved, it'll have its own marker; drop the temporary pin.
                            onPickedLocationCleared()
                            onSaveLocation(shown.location)
                        },
                        onDirectionsError = showError
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            PlacesMap(
                places = uiState.places,
                selectedPlaceId = selectedPlaceId,
                pin = picked,
                myLocation = uiState.myLocation,
                startPosition = uiState.startPosition,
                onMyLocationShown = onMyLocationShown,
                showMyLocation = uiState.hasLocationPermission,
                onMarkerClick = ::selectPlace,
                onMapClick = {
                    focusManager.clearFocus()
                    clearSelection()
                },
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = controlsHeight, bottom = mapBottomPadding)
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
                    // Like Google Maps: a result is shown on the map, with its sheet.
                    onPlaceClick = { id ->
                        focusManager.clearFocus()
                        onQueryChange("")
                        selectPlace(id)
                    },
                    onLocationClick = { location ->
                        focusManager.clearFocus()
                        selectedPlaceId = null
                        onLocationPicked(location)
                    },
                    topOffset = controlsHeight,
                    bottomOffset = bottomBarPadding
                )
            }

            // The sheet takes the bottom of the screen while something is selected.
            if (subject == null) {
                MyLocationButton(
                    isLocating = uiState.isLocating,
                    onClick = onMyLocationClick,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = bottomBarPadding)
                        .padding(end = 20.dp, bottom = MyLocationAboveAddButton)
                )
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
    val focusManager = LocalFocusManager.current
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
                // Place names are rarely dictionary words; autocorrect would rewrite them.
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
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

/** All · each collection · Shared with me. Several can be on; none means All. Hidden with nothing to filter. */
@Composable
private fun FilterChips(
    uiState: HomeUiState,
    onAllSelected: () -> Unit,
    onCollectionToggled: (String) -> Unit,
    onSharedToggled: () -> Unit
) {
    // Nothing to filter by yet: no row at all, rather than a lone "All" chip.
    if (uiState.collections.isEmpty() && uiState.sharedCount == 0) return

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

/**
 * Results in a rounded panel over the map, starting just below the floating controls:
 * the user's own places first, then locations anywhere from the phone's search.
 */
@Composable
private fun SearchResults(
    uiState: HomeUiState,
    onPlaceClick: (placeId: String) -> Unit,
    onLocationClick: (SelectedLocation) -> Unit,
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
        val nothingFound = uiState.searchResults.isEmpty() &&
                uiState.otherLocations.isEmpty() &&
                !uiState.isSearchingLocations &&
                !uiState.locationSearchFailed
        if (nothingFound) {
            CenteredMessage(
                title = "No matches",
                message = "Nothing matches \"${uiState.query.trim()}\"."
            )
            return@Surface
        }
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            if (uiState.searchResults.isNotEmpty()) {
                item(key = "header-places") { SectionHeader("Your places") }
                items(uiState.searchResults, key = { "place-${it.place.id}" }) { item ->
                    val place = item.place
                    ListItem(
                        headlineContent = {
                            Text(place.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        // The collection on its own line, not beside the address: there it took
                        // the address's room, and with a large font left only "Manali, Hi…".
                        supportingContent = {
                            Column {
                                Text(
                                    place.address ?: formatCoordinates(place.latitude, place.longitude),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    item.collection?.name ?: "Shared with you",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        leadingContent = { PlaceMarker(place.markerStyle, item.collection, place.markerPhotoUrl) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onPlaceClick(place.id) }
                    )
                }
            }

            item(key = "header-locations") { SectionHeader("Other locations") }
            when {
                uiState.isSearchingLocations -> item(key = "locations-loading") {
                    StatusRow { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) }
                }
                uiState.locationSearchFailed -> item(key = "locations-failed") {
                    StatusRow(text = "Couldn't search other locations. Check your connection.")
                }
                uiState.otherLocations.isEmpty() -> item(key = "locations-empty") {
                    StatusRow(
                        text = if (uiState.query.trim().length < 3) "Keep typing to search everywhere."
                        else "No other locations found."
                    )
                }
                else -> items(
                    uiState.otherLocations,
                    key = { "location-${it.latitude},${it.longitude},${it.actualName}" }
                ) { location ->
                    ListItem(
                        headlineContent = {
                            Text(location.actualName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = location.address?.let {
                            { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onLocationClick(location) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { heading() }
    )
}

/** A short note in the results, e.g. "Searching…" or why nothing is shown. */
@Composable
private fun StatusRow(text: String? = null, leading: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        leading?.invoke()
        Text(
            text ?: "Searching…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
