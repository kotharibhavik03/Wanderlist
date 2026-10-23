@file:OptIn(ExperimentalMaterial3Api::class)

package com.rizz.wanderlist.views.place

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rizz.wanderlist.domain.model.Place
import com.rizz.wanderlist.domain.model.PlaceAccess
import com.rizz.wanderlist.domain.model.PlaceCollection
import com.rizz.wanderlist.domain.model.PlacePhoto
import com.rizz.wanderlist.util.DirectionsApp
import com.rizz.wanderlist.util.directionsApps
import com.rizz.wanderlist.util.directionsUrl
import com.rizz.wanderlist.util.formatCoordinates
import com.rizz.wanderlist.util.formatDate
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun PlaceDetailRoute(
    placeId: String,
    onBack: () -> Unit,
    onEdit: (placeId: String) -> Unit,
    onPlaceRemoved: () -> Unit
) {
    val viewModel: PlaceDetailViewModel = koinViewModel(parameters = { parametersOf(placeId) })
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnPlaceRemoved by rememberUpdatedState(onPlaceRemoved)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                PlaceDetailEvent.PlaceRemoved -> currentOnPlaceRemoved()
            }
        }
    }

    PlaceDetailScreen(
        uiState = uiState,
        onBack = onBack,
        onEdit = { onEdit(placeId) },
        onLeaveConfirmed = viewModel::onLeaveConfirmed,
        onDeleteConfirmed = viewModel::onDeleteConfirmed,
        onErrorShown = viewModel::onErrorShown
    )
}

@Composable
fun PlaceDetailScreen(
    uiState: PlaceDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onLeaveConfirmed: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onErrorShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val content = uiState as? PlaceDetailUiState.Content
    var pendingAction by rememberSaveable { mutableStateOf<RemoveAction?>(null) }

    LaunchedEffect(content?.errorMessage) {
        content?.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onErrorShown()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        content?.place?.displayName ?: "Place",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (content?.canEdit == true) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit place")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when (uiState) {
            PlaceDetailUiState.Loading -> Centered(padding) { CircularProgressIndicator() }
            PlaceDetailUiState.NotFound -> Centered(padding) {
                Text("This place was deleted or is no longer shared with you.")
            }
            is PlaceDetailUiState.Content -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Header(uiState.place, uiState.access, uiState.collection)
                DirectionsButton(uiState.place, snackbarHostState)
                DetailsCard(uiState.place, uiState.collection)
                PhotosSection(uiState.place.photos)
                PeopleSection(uiState.people)
                when {
                    uiState.canDelete -> RemoveButton(RemoveAction.DELETE, uiState.isBusy) {
                        pendingAction = RemoveAction.DELETE
                    }
                    uiState.canLeave -> RemoveButton(RemoveAction.LEAVE, uiState.isBusy) {
                        pendingAction = RemoveAction.LEAVE
                    }
                }
            }
        }
    }

    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(action.dialogTitle) },
            text = { Text(action.dialogMessage) },
            confirmButton = {
                TextButton(onClick = {
                    pendingAction = null
                    when (action) {
                        RemoveAction.LEAVE -> onLeaveConfirmed()
                        RemoveAction.DELETE -> onDeleteConfirmed()
                    }
                }) { Text(action.confirmLabel, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingAction = null }) { Text("Cancel") } }
        )
    }
}

/** The two ways a place leaves this user's app: members leave it, the owner deletes it. */
private enum class RemoveAction(
    val buttonLabel: String,
    val dialogTitle: String,
    val dialogMessage: String,
    val confirmLabel: String
) {
    LEAVE(
        buttonLabel = "Leave place",
        dialogTitle = "Leave this place?",
        dialogMessage = "You'll lose access to it, and any photos you added to it will be removed.",
        confirmLabel = "Leave"
    ),
    DELETE(
        buttonLabel = "Delete place",
        dialogTitle = "Delete this place?",
        dialogMessage = "It will be removed for everyone it's shared with, along with all its photos. " +
                "This can't be undone.",
        confirmLabel = "Delete"
    )
}

@Composable
private fun RemoveButton(action: RemoveAction, isBusy: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = !isBusy,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isBusy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(action.buttonLabel, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun Centered(padding: PaddingValues, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun Header(place: Place, access: PlaceAccess, collection: PlaceCollection?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PlaceMarker(place.markerStyle, collection, size = 64.dp)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(place.displayName, style = MaterialTheme.typography.titleLarge)
            // When the user renamed it, still show what the map calls it.
            if (place.customName.isNotBlank() && place.customName != place.actualName) {
                Text(
                    "Listed as ${place.actualName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaceStatusBadge(place.status)
                when (access) {
                    PlaceAccess.OWNER -> Unit
                    PlaceAccess.COLLABORATOR -> Badge("Shared · Collaborator")
                    PlaceAccess.VISITED_WITH -> Badge("Shared · Visited with")
                }
            }
        }
    }
}

/** One app: open it straight away. Several (iOS): let the user pick. */
@Composable
private fun DirectionsButton(place: Place, snackbarHostState: SnackbarHostState) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var showMenu by rememberSaveable { mutableStateOf(false) }

    fun open(app: DirectionsApp) {
        try {
            uriHandler.openUri(directionsUrl(app, place.latitude, place.longitude))
        } catch (e: IllegalArgumentException) {
            // Thrown when no app on the device can open the link.
            scope.launch { snackbarHostState.showSnackbar("Couldn't open ${app.label}.") }
        }
    }

    Box {
        Button(
            onClick = {
                if (directionsApps.size == 1) open(directionsApps.first()) else showMenu = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Directions, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Directions")
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            directionsApps.forEach { app ->
                DropdownMenuItem(
                    text = { Text("Open in ${app.label}") },
                    onClick = {
                        showMenu = false
                        open(app)
                    }
                )
            }
        }
    }
}

@Composable
private fun DetailsCard(place: Place, collection: PlaceCollection?) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DetailRow(Icons.Default.LocationOn, "Location") {
                place.address?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text(
                    formatCoordinates(place.latitude, place.longitude),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            place.visitedOnEpochMillis?.let { millis ->
                DetailRow(Icons.Default.CalendarMonth, "Visited on") {
                    Text(formatDate(millis), style = MaterialTheme.typography.bodyMedium)
                }
            }
            place.rating?.let { rating ->
                DetailRow(Icons.Default.Star, "Rating") { RatingStars(rating) }
            }
            collection?.let {
                DetailRow(Icons.Default.Folder, "Collection") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MarkerBadge(it.icon, collectionBrush(it.colors, MaterialTheme.colorScheme.primary), 28.dp)
                        Text(it.name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (place.notes.isNotBlank()) {
                DetailRow(Icons.AutoMirrored.Filled.Notes, "Notes") {
                    Text(place.notes, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, content: @Composable ColumnScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            content()
        }
    }
}

/** Read-only stars, announced as one phrase instead of five separate icons. */
@Composable
private fun RatingStars(rating: Int) {
    Row(modifier = Modifier.clearAndSetSemantics { contentDescription = "$rating out of 5 stars" }) {
        (1..5).forEach { star ->
            Icon(
                if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() }
    )
}

@Composable
private fun PhotosSection(photos: List<PlacePhoto>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Photos (${photos.size})")
        if (photos.isEmpty()) {
            Text(
                "No photos yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(photos, key = { it.id }) { photo ->
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .semantics { contentDescription = if (photo.isPrivate) "Private photo" else "Photo" },
                        contentAlignment = Alignment.Center
                    ) {
                        // Real image loading (Coil) arrives with the photo step.
                        Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (photo.isPrivate) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeopleSection(people: List<PlacePerson>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("People (${people.size})")
        people.forEach { PersonRow(it) }
        if (people.size == 1 && people.first().isYou) {
            Text(
                "Not shared with anyone yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Name on top and email underneath; the role as a badge on the right. */
@Composable
private fun PersonRow(person: PlacePerson) {
    // Without a published name (not joined yet), the email is the best label.
    val primary = person.name ?: person.email ?: "Unknown"
    val secondary = when {
        !person.hasJoined -> "Hasn't joined yet"
        person.name != null -> person.email
        else -> null
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (person.isYou) "$primary (you)" else primary,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            secondary?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Badge(person.role.label())
    }
}

private fun PlaceAccess.label(): String = when (this) {
    PlaceAccess.OWNER -> "Owner"
    PlaceAccess.COLLABORATOR -> "Collaborator"
    PlaceAccess.VISITED_WITH -> "Visited with"
}
