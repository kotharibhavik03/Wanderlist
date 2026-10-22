package com.rizz.wanderlist.views.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
                    TextButton(onClick = onBack) { Text("←", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
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
                PlaceCollection(
                    id = "preview",
                    name = uiState.name.ifBlank { "Collection name" },
                    icon = uiState.icon,
                    colors = uiState.colors
                )
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

            Text("Icon", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CollectionOptions.icons.forEach { icon ->
                    val isSelected = icon == uiState.icon
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { onIconSelected(icon) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(icon, fontSize = 24.sp)
                    }
                }
            }

            Text("Colors (pick one or more)", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CollectionOptions.colors.forEach { colorValue ->
                    val isSelected = colorValue in uiState.colors
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(colorValue))
                            .then(
                                if (isSelected)
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                else Modifier
                            )
                            .clickable { onColorToggled(colorValue) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

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

private fun CollectionError.toMessage(): String = when (this) {
    CollectionError.NameBlank -> "Please enter a name"
    CollectionError.NameTooLong ->
        "Use ${CreateCollectionUseCase.MAX_NAME_LENGTH} characters or fewer"
    CollectionError.NoColor -> "Pick at least one color"
}