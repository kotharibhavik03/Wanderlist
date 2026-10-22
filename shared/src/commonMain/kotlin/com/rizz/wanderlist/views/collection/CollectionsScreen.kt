package com.rizz.wanderlist.views.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rizz.wanderlist.domain.model.PlaceCollection
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CollectionsRoute(
    onAddClick: () -> Unit,
    onSignOutClick: () -> Unit,
    viewModel: CollectionsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CollectionsScreen(uiState = uiState, onAddClick = onAddClick, onSignOutClick = onSignOutClick)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    uiState: CollectionsUiState,
    onAddClick: () -> Unit,
    onSignOutClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wanderlist") },
                actions = { TextButton(onClick = onSignOutClick) { Text("Sign out") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) { Text("+", fontSize = 24.sp) }
        }
    ) { padding ->
        when (uiState) {
            CollectionsUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            is CollectionsUiState.Content ->
                if (uiState.collections.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center
                    ) { Text("No collections yet. Tap + to create one.") }
                } else {
                    LazyColumn(
                        modifier = Modifier.padding(padding),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.collections, key = { it.id }) { collection ->
                            CollectionCard(collection)
                        }
                    }
                }
        }
    }
}

@Composable
fun CollectionCard(collection: PlaceCollection) {
    val colors = collection.colors.map { Color(it) }
    val gradient = Brush.linearGradient(if (colors.size == 1) listOf(colors[0], colors[0]) else colors)

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(gradient),
                contentAlignment = Alignment.Center
            ) {
                Text(collection.icon, fontSize = 28.sp)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(collection.name, style = MaterialTheme.typography.titleMedium)
                Text("${collection.placeCount} places", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}