@file:OptIn(ExperimentalMaterial3Api::class)

package com.rizz.wanderlist.views.place

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.rizz.wanderlist.domain.model.CollectionSummary
import com.rizz.wanderlist.domain.model.MAX_PHOTOS_PER_SAVE
import com.rizz.wanderlist.domain.model.MarkerStyle
import com.rizz.wanderlist.domain.model.MemberRole
import com.rizz.wanderlist.domain.model.PlaceCollection
import com.rizz.wanderlist.domain.model.PlaceMember
import com.rizz.wanderlist.domain.model.PlaceStatus
import com.rizz.wanderlist.domain.model.SelectedLocation
import com.rizz.wanderlist.util.formatCoordinates
import com.rizz.wanderlist.util.formatDate
import com.rizz.wanderlist.util.pluralize

class AddPlaceActions(
    val onBack: () -> Unit,
    val onOpenMapPicker: () -> Unit,
    val onCustomNameChange: (String) -> Unit,
    val onNotesChange: (String) -> Unit,
    val onStatusChange: (PlaceStatus) -> Unit,
    val onVisitedOnChange: (Long?) -> Unit,
    val onRatingChange: (Int?) -> Unit,
    val onAddPhotos: () -> Unit,
    val onPhotoRemoved: (String) -> Unit,
    val onPhotoPrivacyToggled: (String) -> Unit,
    val onMarkerStyleChange: (MarkerStyle) -> Unit,
    val onCollectionSelected: (String) -> Unit,
    val onCreateCollection: () -> Unit,
    val onMemberAdded: (String, MemberRole) -> Unit,
    val onMemberRemoved: (String) -> Unit,
    val onSavePlace: () -> Unit,
    val onErrorShown: () -> Unit
)


@Composable
fun AddPlaceScreen(
    uiState: AddPlaceUiState,
    collections: List<CollectionSummary>,
    actions: AddPlaceActions
) {
    var showCollectionSheet by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showAddPerson by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            actions.onErrorShown()
        }
    }


    val selectedSummary = collections.firstOrNull { it.collection.id == uiState.selectedCollectionId }
    val selectedCollection = selectedSummary?.collection
    val markerBrush = collectionBrush(
        colors = selectedCollection?.colors.orEmpty(),
        fallback = MaterialTheme.colorScheme.primary
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditing) "Edit place" else "Add a place") },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when (uiState.mode) {
            is DraftMode.Loading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            is DraftMode.Unavailable -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center
            ) { Text("This place was deleted or you can no longer edit it.") }

            DraftMode.New, is DraftMode.Editing -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                LocationSection(uiState.selectedLocation, actions.onOpenMapPicker)
                DetailsSection(uiState, actions, onPickDate = { showDatePicker = true })
                // Adding photos to an existing place comes in a later step.
                if (uiState.mode == DraftMode.New) {
                    PhotosSection(
                        photos = uiState.photos,
                        canAdd = uiState.photoRoom > 0 && !uiState.isSaving,
                        onAdd = actions.onAddPhotos,
                        onRemove = actions.onPhotoRemoved,
                        onTogglePrivate = actions.onPhotoPrivacyToggled
                    )
                }
                MarkerSection(uiState, selectedCollection, markerBrush, actions.onMarkerStyleChange)
                if (uiState.canManageCollectionAndPeople) {
                    CollectionSection(selectedSummary, markerBrush) { showCollectionSheet = true }
                    PeopleSection(
                        members = uiState.members,
                        onAdd = { showAddPerson = true },
                        onRemove = actions.onMemberRemoved
                    )
                }
                SaveSection(uiState, onSave = actions.onSavePlace)
            }
        }
    }

    if (showCollectionSheet) {
        CollectionPickerSheet(
            collections = collections,
            selectedId = uiState.selectedCollectionId,
            onSelect = {
                actions.onCollectionSelected(it)
                showCollectionSheet = false
            },
            onCreate = {
                showCollectionSheet = false
                actions.onCreateCollection()
            },
            onDismiss = { showCollectionSheet = false }
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = uiState.visitedOnMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    actions.onVisitedOnChange(pickerState.selectedDateMillis)
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = pickerState) }
    }

    if (showAddPerson) {
        AddPersonDialog(
            onDismiss = { showAddPerson = false },
            onAdd = { email, role ->
                actions.onMemberAdded(email, role)
                showAddPerson = false
            }
        )
    }
}

@Composable
private fun Section(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        content()
    }
}

@Composable
private fun LocationSection(location: SelectedLocation?, onClick: () -> Unit) {
    Section("Location") {
        OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = location?.actualName ?: "Choose on map",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = when {
                            location == null -> "Search or drop a pin on the map"
                            location.address != null -> location.address
                            else -> formatCoordinates(location.latitude, location.longitude)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
    }
}

@Composable
private fun DetailsSection(
    uiState: AddPlaceUiState,
    actions: AddPlaceActions,
    onPickDate: () -> Unit
) {
    Section("Details") {
        OutlinedTextField(
            value = uiState.customName,
            onValueChange = actions.onCustomNameChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Name (optional)") },
            placeholder = { Text(uiState.selectedLocation?.actualName ?: "Give this place a name") },
            supportingText = { Text("Defaults to the name from the map.") }
        )

        val options = listOf(
            PlaceStatus.WISHLIST to "Want to visit",
            PlaceStatus.VISITED to "Visited"
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (status, label) ->
                SegmentedButton(
                    selected = uiState.status == status,
                    onClick = { actions.onStatusChange(status) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    label = { Text(label) }
                )
            }
        }

        if (uiState.status == PlaceStatus.VISITED) {
            OutlinedButton(onClick = onPickDate, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(uiState.visitedOnMillis?.let(::formatDate) ?: "Add visit date")
            }
            RatingRow(uiState.rating, actions.onRatingChange)
        }

        OutlinedTextField(
            value = uiState.notes,
            onValueChange = actions.onNotesChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            label = { Text("Notes and memories (optional)") }
        )
    }
}

@Composable
private fun RatingRow(rating: Int?, onChange: (Int?) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Rating", modifier = Modifier.padding(end = 8.dp))
        (1..5).forEach { star ->
            val filled = rating != null && star <= rating
            IconButton(onClick = { onChange(if (rating == star) null else star) }) {
                Icon(
                    imageVector = if (filled) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (star == 1) "1 star" else "$star stars",
                    tint = if (filled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PhotosSection(
    photos: List<DraftPhoto>,
    canAdd: Boolean,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onTogglePrivate: (String) -> Unit
) {
    Section(
        "Photos",
        "Optional, up to $MAX_PHOTOS_PER_SAVE. Lock a photo to keep it visible only to you."
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            photos.forEach { photo ->
                PhotoThumbnail(
                    photo = photo,
                    onRemove = { onRemove(photo.id) },
                    onTogglePrivate = { onTogglePrivate(photo.id) }
                )
            }
            if (canAdd) {
                OutlinedCard(onClick = onAdd, modifier = Modifier.size(88.dp)) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                        Text("Add", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoThumbnail(photo: DraftPhoto, onRemove: () -> Unit, onTogglePrivate: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (photo.localPath == null) {
                // Still being compressed.
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                AsyncImage(
                    model = photo.localPath,
                    contentDescription = if (photo.isPrivate) "Private photo" else "Shared photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            ThumbnailButton(
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Remove photo", modifier = Modifier.size(18.dp))
            }
            ThumbnailButton(
                onClick = onTogglePrivate,
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                Icon(
                    imageVector = if (photo.isPrivate) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = if (photo.isPrivate) "Private. Tap to share" else "Shared. Tap to make private",
                    tint = if (photo.isPrivate) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(
            text = if (photo.isPrivate) "Only me" else "Shared",
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/** A 36dp icon button on a small translucent disc, so it stays visible over any photo. */
@Composable
private fun ThumbnailButton(onClick: () -> Unit, modifier: Modifier, icon: @Composable () -> Unit) {
    IconButton(onClick = onClick, modifier = modifier.size(36.dp)) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) { icon() }
    }
}

@Composable
private fun MarkerSection(
    uiState: AddPlaceUiState,
    collection: PlaceCollection?,
    brush: Brush,
    onChange: (MarkerStyle) -> Unit
) {
    Section("Map marker", "How this place appears on the map. Defaults to the collection's icon and colours.") {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MarkerOption(
                label = "Collection default",
                selected = uiState.markerStyle is MarkerStyle.CollectionDefault,
                onClick = { onChange(MarkerStyle.CollectionDefault) }
            ) { MarkerBadge(collection?.icon ?: "📍", brush) }

            markerIcons.forEach { (key, emoji) ->
                MarkerOption(
                    label = "$key icon",
                    selected = uiState.markerStyle == MarkerStyle.Icon(key),
                    onClick = { onChange(MarkerStyle.Icon(key)) }
                ) { MarkerBadge(emoji, brush) }
            }

            uiState.photos.filter { it.isReady && !it.isPrivate }.forEach { photo ->
                MarkerOption(
                    label = "Photo marker",
                    selected = uiState.markerStyle == MarkerStyle.Photo(photo.id),
                    onClick = { onChange(MarkerStyle.Photo(photo.id)) }
                ) {
                    AsyncImage(
                        model = photo.localPath,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }
        }
        if (uiState.photos.isNotEmpty() && uiState.photos.all { it.isPrivate }) {
            Text(
                "Only shared photos can be used as a marker.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MarkerOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.semantics {
            contentDescription = label
            this.selected = selected
        }
    ) {
        Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun CollectionSection(
    summary: CollectionSummary?,
    brush: Brush,
    onClick: () -> Unit
) {
    val collection = summary?.collection
    Section("Collection") {
        OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (collection != null) MarkerBadge(collection.icon, brush)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = collection?.name ?: "Choose a collection",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (summary != null) {
                        Text(
                            pluralize(summary.placeCount, "place"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
    }
}

@Composable
private fun PeopleSection(
    members: List<PlaceMember>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit
) {
    Section(
        "People",
        "Add people by Google email. They see only this place, never the rest of your collection."
    ) {
        members.forEach { member ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        member.email.first().uppercase(),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        member.email,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        member.role.description(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onRemove(member.email) }) {
                    Icon(Icons.Default.Close, contentDescription = "Remove ${member.email}")
                }
            }
        }
        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PersonAdd, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add person")
        }
    }
}

@Composable
private fun SaveSection(uiState: AddPlaceUiState, onSave: () -> Unit) {
    val upload = uiState.photoUpload
    val hint = when {
        uiState.selectedLocation == null -> "Choose a location to continue."
        uiState.selectedCollectionId == null -> "Choose a collection to continue."
        uiState.isPreparingPhotos -> "Getting your photos ready…"
        upload != null -> "Uploading photos: ${upload.uploaded} of ${upload.total}"
        else -> null
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onSave,
            enabled = uiState.canSave,
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    if (uiState.isEditing) "Save changes" else "Save place",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}