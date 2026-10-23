
package com.rizz.wanderlist.views.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeRoute(
    onCollectionsClick: () -> Unit,
    onProfileClick: () -> Unit,
    onAddClick: () -> Unit,
    onFilterClick: () -> Unit
) {
    var searchText by rememberSaveable { mutableStateOf("") }

    val colors = MaterialTheme.colorScheme

    Scaffold(
        containerColor = colors.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add a place or collection"
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 8.dp)
                .background(colors.background)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(onClick = onFilterClick) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Filter by collection",
                        tint = colors.onBackground
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Wanderlist",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.onBackground
                    )

                    Text(
                        text = "Your world, your memories",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }

                Surface(
                    color = colors.surfaceVariant,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = 14.dp,
                            vertical = 8.dp
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "0",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary
                        )

                        Text(
                            text = "Places",
                            fontSize = 11.sp,
                            color = colors.onSurface
                        )
                    }
                }
            }

            // Search
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(
                        color = colors.surfaceVariant,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = colors.onSurfaceVariant
                )

                Spacer(Modifier.size(10.dp))

                Box(modifier = Modifier.weight(1f)) {
                    if (searchText.isEmpty()) {
                        Text(
                            text = "Search places, cities, collections...",
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    BasicTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = colors.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                IconButton(onClick = onFilterClick) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Collection filters",
                        tint = colors.onSurface
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Map placeholder
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp)
                    .background(
                        color = colors.surfaceVariant,
                        shape = RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = colors.surface
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier
                                .padding(14.dp)
                                .size(34.dp)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "Your map starts here",
                        color = colors.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = "Add places to see your memories on the map",
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
