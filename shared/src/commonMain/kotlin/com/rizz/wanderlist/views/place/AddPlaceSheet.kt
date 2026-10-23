@file:OptIn(ExperimentalMaterial3Api::class)

package com.rizz.wanderlist.views.place

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rizz.wanderlist.domain.model.MemberRole
import com.rizz.wanderlist.domain.model.PlaceCollection
import com.rizz.wanderlist.util.isValidEmail

internal fun MemberRole.title(): String = when (this) {
    MemberRole.COLLABORATOR -> "Collaborator"
    MemberRole.VISITED_WITH -> "Visited with"
}

internal fun MemberRole.description(): String = when (this) {
    MemberRole.COLLABORATOR -> "Collaborator · can edit and add photos"
    MemberRole.VISITED_WITH -> "Visited with · can view and add their own photos"
}

@Composable
internal fun CollectionPickerSheet(
    collections: List<PlaceCollection>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Save to collection", style = MaterialTheme.typography.titleLarge)

            if (collections.isEmpty()) {
                Text(
                    "You have no collections yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(collections, key = { it.id }) { collection ->
                    ListItem(
                        headlineContent = { Text(collection.name) },
                        supportingContent = { Text("${collection.placeCount} places") },
                        leadingContent = {
                            MarkerBadge(
                                emoji = collection.icon,
                                brush = collectionBrush(collection.colors, MaterialTheme.colorScheme.primary)
                            )
                        },
                        trailingContent = {
                            if (collection.id == selectedId) {
                                Icon(Icons.Default.Check, contentDescription = "Selected")
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onSelect(collection.id) }
                    )
                }
            }

            TextButton(onClick = onCreate) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("New collection")
            }
        }
    }
}

@Composable
internal fun AddPersonDialog(
    onDismiss: () -> Unit,
    onAdd: (email: String, role: MemberRole) -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var role by remember { mutableStateOf(MemberRole.VISITED_WITH) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add person") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Google account email") },
                    isError = email.isNotBlank() && !email.isValidEmail(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                MemberRole.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = role == option,
                                onClick = { role = option },
                                role = Role.RadioButton
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = role == option, onClick = null)
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(option.title(), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (option == MemberRole.COLLABORATOR) "Can edit this place and add photos"
                                else "Can view this place and add their own photos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(email, role) }, enabled = email.isValidEmail()) {
                Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}