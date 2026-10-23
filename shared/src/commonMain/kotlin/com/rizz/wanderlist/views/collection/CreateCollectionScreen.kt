package com.rizz.wanderlist.views.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rizz.wanderlist.domain.model.PlaceCollection
import com.rizz.wanderlist.domain.usecase.CollectionError
import com.rizz.wanderlist.domain.usecase.CreateCollectionUseCase
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CreateCollectionRoute(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: CreateCollectionViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnSaved by rememberUpdatedState(onSaved)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                CreateCollectionEvent.Saved -> currentOnSaved()
            }
        }
    }

    CreateCollectionScreen(
        uiState = uiState,
        onBack = onBack,
        onNameChange = viewModel::onNameChange,
        onIconSelected = viewModel::onIconSelected,
        onColorToggled = viewModel::onColorToggled,
        onSaveClick = viewModel::onSaveClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCollectionScreen(
    uiState: CreateCollectionUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onIconSelected: (String) -> Unit,
    onColorToggled: (Long) -> Unit,
    onSaveClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New collection") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            CollectionCard(
                collection = PlaceCollection(
                    id = "preview",
                    name = uiState.name.ifBlank { "Collection name" },
                    icon = uiState.icon,
                    colors = uiState.colors
                ),
                placeCount = 0
            )

            OutlinedTextField(
                value = uiState.name,
                onValueChange = onNameChange,
                label = { Text("Collection name") },
                singleLine = true,
                isError = uiState.error != null,
                supportingText = { uiState.error?.let { Text(it.toMessage()) } },
                modifier = Modifier.fillMaxWidth()
            )

            SectionTitle("Icon")
            IconPicker(selected = uiState.icon, onSelect = onIconSelected)

            SectionTitle("Colours (pick one or more)")
            ColorPicker(selected = uiState.colors, onToggle = onColorToggled)

            Button(
                onClick = onSaveClick,
                enabled = uiState.canSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (uiState.isSaving) "Saving…" else "Save collection")
            }
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

/** Single choice, so each option is a radio button to screen readers. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconPicker(selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CollectionOptions.icons.forEach { option ->
            val isSelected = option.value == selected
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .then(
                        if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        else Modifier
                    )
                    .selectable(
                        selected = isSelected,
                        onClick = { onSelect(option.value) },
                        role = Role.RadioButton
                    )
                    .semantics { contentDescription = option.label },
                contentAlignment = Alignment.Center
            ) {
                // The label above is read instead of the raw emoji.
                Text(option.value, fontSize = 24.sp, modifier = Modifier.clearAndSetSemantics { })
            }
        }
    }
}

/** Multiple choice, so each swatch is a checkbox to screen readers. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPicker(selected: List<Long>, onToggle: (Long) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CollectionOptions.colors.forEach { option ->
            val swatch = Color(option.value)
            val isSelected = option.value in selected
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(swatch)
                    .then(
                        if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        else Modifier
                    )
                    .toggleable(
                        value = isSelected,
                        onValueChange = { onToggle(option.value) },
                        role = Role.Checkbox
                    )
                    .semantics { contentDescription = option.label },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null, // the checkbox state is already announced
                        tint = contrastingTint(swatch)
                    )
                }
            }
        }
    }
}

/** Black on light swatches (amber), white on dark ones (blue), so the tick is always visible. */
private fun contrastingTint(background: Color): Color =
    if (background.luminance() > 0.5f) Color.Black else Color.White

private fun CollectionError.toMessage(): String = when (this) {
    CollectionError.NameBlank -> "Please enter a name"
    CollectionError.NameTooLong ->
        "Use ${CreateCollectionUseCase.MAX_NAME_LENGTH} characters or fewer"
    CollectionError.NoColor -> "Pick at least one colour"
}
