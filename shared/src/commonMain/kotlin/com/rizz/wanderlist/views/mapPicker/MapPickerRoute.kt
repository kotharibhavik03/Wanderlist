package com.rizz.wanderlist.views.place

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rizz.wanderlist.domain.model.SelectedLocation

@Composable
fun MapPickerRoute(viewModel: AddPlaceViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MapPickerScreen(
        initial = uiState.selectedLocation,
        onBack = onBack,
        onConfirm = {
            viewModel.onLocationSelected(it)
            onBack()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPickerScreen(
    initial: SelectedLocation?,
    onBack: () -> Unit,
    onConfirm: (SelectedLocation) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initial?.actualName.orEmpty()) }
    var address by rememberSaveable { mutableStateOf(initial?.address.orEmpty()) }
    var latText by rememberSaveable { mutableStateOf(initial?.latitude?.toString().orEmpty()) }
    var lngText by rememberSaveable { mutableStateOf(initial?.longitude?.toString().orEmpty()) }

    val latitude = latText.trim().toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
    val longitude = lngText.trim().toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
    val canConfirm = name.isNotBlank() && latitude != null && longitude != null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Choose location") },
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
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "The interactive map arrives with the Google Maps step. For now enter the place " +
                        "manually. In Google Maps, long-press a spot and copy the coordinates shown.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Place name") }
            )
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Address (optional)") }
            )
            OutlinedTextField(
                value = latText,
                onValueChange = { latText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Latitude (-90 to 90)") },
                isError = latText.isNotBlank() && latitude == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
            )
            OutlinedTextField(
                value = lngText,
                onValueChange = { lngText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Longitude (-180 to 180)") },
                isError = lngText.isNotBlank() && longitude == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
            )
            Button(
                onClick = {
                    if (latitude != null && longitude != null) {
                        onConfirm(
                            SelectedLocation(
                                latitude = latitude,
                                longitude = longitude,
                                actualName = name.trim(),
                                address = address.trim().ifBlank { null }
                            )
                        )
                    }
                },
                enabled = canConfirm,
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) { Text("Use this location") }
        }
    }
}