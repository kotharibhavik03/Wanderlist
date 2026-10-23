package com.rizz.wanderlist.views.place

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AddPlaceRoute(
    viewModel: AddPlaceViewModel,
    onBack: () -> Unit,
    onOpenMapPicker: () -> Unit,
    onCreateCollection: () -> Unit,
    onSaved: (collectionId: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val currentOnSaved by rememberUpdatedState(onSaved)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AddPlaceEvent.Saved -> currentOnSaved(event.collectionId)
            }
        }
    }

    AddPlaceScreen(
        uiState = uiState,
        collections = collections,
        actions = AddPlaceActions(
            onBack = onBack,
            onOpenMapPicker = onOpenMapPicker,
            onCustomNameChange = viewModel::onCustomNameChange,
            onNotesChange = viewModel::onNotesChange,
            onStatusChange = viewModel::onStatusChange,
            onVisitedOnChange = viewModel::onVisitedOnChange,
            onRatingChange = viewModel::onRatingChange,
            onAddPhotos = { /* the photo picker arrives in the media step */ },
            onPhotoRemoved = viewModel::onPhotoRemoved,
            onPhotoPrivacyToggled = viewModel::onPhotoPrivacyToggled,
            onMarkerStyleChange = viewModel::onMarkerStyleChange,
            onCollectionSelected = viewModel::onCollectionSelected,
            onCreateCollection = onCreateCollection,
            onMemberAdded = viewModel::onMemberAdded,
            onMemberRemoved = viewModel::onMemberRemoved,
            onSavePlace = viewModel::onSaveClicked,
            onErrorShown = viewModel::onErrorShown
        )
    )
}