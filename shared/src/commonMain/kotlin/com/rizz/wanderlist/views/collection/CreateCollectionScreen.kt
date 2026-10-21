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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rizz.wanderlist.domain.model.PlaceCollection

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateCollectionScreen(
    onBack: () -> Unit,
    onSave: (name: String, icon: String, colors: List<Long>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf(CollectionOptions.icons.first()) }
    var selectedColors by remember { mutableStateOf(listOf(CollectionOptions.colors.first())) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New collection") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("←", fontSize = 24.sp) }
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
            // Live preview, reusing the same card from the list screen
            CollectionCard(
                PlaceCollection(
                    id = "preview",
                    name = name.ifBlank { "Collection name" },
                    icon = selectedIcon,
                    colors = selectedColors,
                    placeCount = 0
                )
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Collection name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Icon", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CollectionOptions.icons.forEach { icon ->
                    val isSelected = icon == selectedIcon
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { selectedIcon = icon },
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
                    val isSelected = colorValue in selectedColors
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
                            .clickable {
                                selectedColors = when {
                                    !isSelected -> selectedColors + colorValue
                                    selectedColors.size > 1 -> selectedColors - colorValue
                                    else -> selectedColors // keep at least one color
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Button(
                onClick = { onSave(name.trim(), selectedIcon, selectedColors) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save collection")
            }
        }
    }
}