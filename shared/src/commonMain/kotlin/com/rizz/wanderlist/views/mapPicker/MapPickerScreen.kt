package com.rizz.wanderlist.views.mapPicker

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rizz.wanderlist.domain.model.SelectedLocation
import com.rizz.wanderlist.domain.usecase.SearchLocationsUseCase
import com.rizz.wanderlist.util.formatCoordinates
import com.rizz.wanderlist.views.location.MyLocationButton
import com.rizz.wanderlist.views.location.rememberMyLocationClick
import com.rizz.wanderlist.views.map.AppMapUiSettings
import com.rizz.wanderlist.views.map.GoogleLogoTapBlocker
import com.rizz.wanderlist.views.map.WorldView
import com.rizz.wanderlist.views.map.animateOrJump
import com.rizz.wanderlist.views.map.rememberMapProperties
import eu.buney.maps.CameraMoveStartedReason
import eu.buney.maps.CameraPosition
import eu.buney.maps.CameraUpdateFactory
import eu.buney.maps.GoogleMap
import eu.buney.maps.LatLng
import eu.buney.maps.rememberCameraPositionState
import org.koin.compose.viewmodel.koinViewModel

/** Close enough to pick out a single building. */
private const val PinZoom = 17f

@Composable
fun MapPickerRoute(
    /** Where to start: the place's current location, or null for a new place. */
    initial: SelectedLocation?,
    onConfirm: (SelectedLocation) -> Unit,
    onBack: () -> Unit,
    viewModel: MapPickerViewModel = koinViewModel()
) {
    LaunchedEffect(viewModel) { viewModel.start(initial) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MapPickerScreen(
        uiState = uiState,
        initial = initial,
        onBack = onBack,
        onConfirm = onConfirm,
        onQueryChange = viewModel::onQueryChange,
        onResultPicked = viewModel::onResultPicked,
        onCameraIdle = viewModel::onCameraIdle,
        onFlyToHandled = viewModel::onFlyToHandled,
        hasLocationPermission = viewModel::hasLocationPermission,
        onPermissionAnswered = viewModel::onPermissionAnswered,
        onPermissionRefused = viewModel::onPermissionRefused,
        onMyLocation = viewModel::goToMyLocation,
        onMessageShown = viewModel::onMessageShown
    )
}

/**
 * Search to get close, then move the map: the pin stays in the centre and marks the exact spot.
 * The pin sits in the middle of the map area left visible between the top and bottom panels,
 * which is also where the map puts its camera target (same padding), so they always agree.
 */
@Composable
fun MapPickerScreen(
    uiState: MapPickerUiState,
    initial: SelectedLocation?,
    onBack: () -> Unit,
    onConfirm: (SelectedLocation) -> Unit,
    onQueryChange: (String) -> Unit,
    onResultPicked: (SelectedLocation) -> Unit,
    onCameraIdle: (latitude: Double, longitude: Double) -> Unit,
    onFlyToHandled: () -> Unit,
    hasLocationPermission: () -> Boolean,
    onPermissionAnswered: (granted: Boolean) -> Unit,
    onPermissionRefused: () -> Unit,
    onMyLocation: () -> Unit,
    onMessageShown: () -> Unit
) {
    val density = LocalDensity.current
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
    val focusManager = LocalFocusManager.current
    val cameraState = rememberCameraPositionState {
        position = initial?.let { CameraPosition(LatLng(it.latitude, it.longitude), PinZoom, 0f, 0f) } ?: WorldView
    }
    var mapLoaded by remember { mutableStateOf(false) }
    var topHeight by remember { mutableStateOf(0.dp) }
    var bottomHeight by remember { mutableStateOf(0.dp) }

    // Set while one of our own fly-to animations (search result, my location) is running.
    val flying = remember { FlyingFlag() }

    // Each time the map comes to rest after the user moved it, or after we flew it somewhere,
    // report the point under the pin. Other camera "moves" are ignored: the map also reports
    // them when its padding is applied (on opening the picker, too), and treating those as a
    // choice would pin whatever was under the pin, even the middle of the world view.
    LaunchedEffect(cameraState) {
        snapshotFlow { cameraState.isMoving }.collect { moving ->
            if (moving) return@collect
            val chosen = cameraState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE || flying.value
            flying.value = false
            if (chosen) {
                val target = cameraState.position.target
                onCameraIdle(target.latitude, target.longitude)
            }
        }
    }

    // Fly to a picked search result or the device's position; landing there reports the point.
    LaunchedEffect(uiState.flyTo, mapLoaded) {
        val target = uiState.flyTo ?: return@LaunchedEffect
        if (!mapLoaded) return@LaunchedEffect
        flying.value = true
        cameraState.animateOrJump(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(target.latitude, target.longitude),
                maxOf(cameraState.position.zoom, target.zoom)
            )
        )
        onFlyToHandled()
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
            properties = rememberMapProperties(showMyLocation = uiState.hasLocationPermission),
            uiSettings = AppMapUiSettings,
            contentPadding = PaddingValues(top = topHeight, bottom = bottomHeight),
            onMapClick = { focusManager.clearFocus() },
            onMapLoaded = { mapLoaded = true }
        )
        GoogleLogoTapBlocker(PaddingValues(top = topHeight, bottom = bottomHeight))

        // The pin, centred in the visible map area. It lifts while the map moves.
        val lift by animateDpAsState(if (cameraState.isMoving) 12.dp else 0.dp, label = "pin lift")
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topHeight, bottom = bottomHeight),
            contentAlignment = Alignment.Center
        ) {
            // Marks the exact point.
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), CircleShape)
            )
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                // The icon's tip is at its bottom centre: raise it by half its height to put the tip on the point.
                modifier = Modifier
                    .size(PinSize)
                    .offset(y = -(PinSize / 2) - lift)
            )
        }

        TopPanel(
            query = uiState.query,
            onQueryChange = onQueryChange,
            onBack = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { topHeight = with(density) { it.height.toDp() } }
        )

        if (uiState.query.isNotBlank()) {
            SearchResults(
                uiState = uiState,
                onResultPicked = { location ->
                    focusManager.clearFocus()
                    onResultPicked(location)
                },
                modifier = Modifier
                    .padding(top = topHeight)
                    .imePadding()
                    .padding(horizontal = 12.dp)
            )
        }

        MyLocationButton(
            isLocating = uiState.isLocating,
            onClick = onMyLocationClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = bottomHeight + 12.dp)
        )

        SnackbarHost(
            snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomHeight)
        )

        BottomPanel(
            pinned = uiState.pinned,
            isResolving = uiState.isResolving,
            onConfirm = onConfirm,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { bottomHeight = with(density) { it.height.toDp() } }
        )
    }
}

private val PinSize = 48.dp

/** A plain holder (not Compose state): read and written only inside the camera effects. */
private class FlyingFlag {
    var value = false
}

@Composable
private fun TopPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    Row(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
            modifier = Modifier.weight(1f)
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text("Search a city, area or address", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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

@Composable
private fun SearchResults(
    uiState: MapPickerUiState,
    onResultPicked: (SelectedLocation) -> Unit,
    modifier: Modifier = Modifier
) {
    val search = uiState.search
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        modifier = modifier.fillMaxWidth().heightIn(max = 360.dp)
    ) {
        when {
            search.isLoading -> StatusText("Searching…", showProgress = true)
            search.failed -> StatusText("Couldn't search. Check your connection.")
            search.results.isEmpty() -> StatusText(
                if (uiState.query.trim().length < SearchLocationsUseCase.MIN_QUERY_LENGTH) "Keep typing to search."
                else "Nothing found. Try a nearby town, then move the map to the spot."
            )
            else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(search.results, key = { "${it.latitude},${it.longitude},${it.actualName}" }) { location ->
                    ListItem(
                        headlineContent = { Text(location.actualName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                        modifier = Modifier.clickable { onResultPicked(location) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusText(text: String, showProgress: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showProgress) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * What's under the pin, and the button to use it. Its height never changes (one-line title,
 * two-line subtitle in every state): the map's padding follows this panel, and a padding change
 * makes the map report a camera stop, which would look the address up again and loop.
 */
@Composable
private fun BottomPanel(
    pinned: SelectedLocation?,
    isResolving: Boolean,
    onConfirm: (SelectedLocation) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Move the map to put the pin exactly on the spot.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        when {
                            pinned == null -> "Search, or move the map"
                            isResolving -> "Finding address…"
                            else -> pinned.actualName
                        },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        pinned?.let { it.address ?: formatCoordinates(it.latitude, it.longitude) }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isResolving) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            Button(
                onClick = { pinned?.let(onConfirm) },
                enabled = pinned != null,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Use this location") }
        }
    }
}
