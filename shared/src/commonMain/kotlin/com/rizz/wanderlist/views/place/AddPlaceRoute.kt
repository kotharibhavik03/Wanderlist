package com.rizz.wanderlist.views.place

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rizz.wanderlist.views.photo.rememberPhotoPicker

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
    val myEmail by viewModel.myEmail.collectAsStateWithLifecycle()
    val currentOnSaved by rememberUpdatedState(onSaved)
    val photoPicker = rememberPhotoPicker(onPicked = viewModel::onPhotosPicked)

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
        myEmail = myEmail,
        actions = AddPlaceActions(
            onBack = onBack,
            onOpenMapPicker = onOpenMapPicker,
            onCustomNameChange = viewModel::onCustomNameChange,
            onNotesChange = viewModel::onNotesChange,
            onStatusChange = viewModel::onStatusChange,
            onVisitedOnChange = viewModel::onVisitedOnChange,
            onRatingChange = viewModel::onRatingChange,
            onAddPhotos = photoPicker::launch,
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