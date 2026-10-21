package com.rizz.wanderlist.views.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rizz.wanderlist.model.PlaceCollection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun CollectionsScreen(collections: List<PlaceCollection>) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Wanderlist") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* later */ }) { Text("+", fontSize = 24.sp) }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(collections, key = { it.id }) { collection ->
                CollectionCard(collection)
            }
        }
    }
}

@Composable
private fun CollectionCard(collection: PlaceCollection) {
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