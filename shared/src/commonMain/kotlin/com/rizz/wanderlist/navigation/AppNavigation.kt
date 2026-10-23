
package com.rizz.wanderlist.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.rizz.wanderlist.views.collection.CollectionDetailRoute
import com.rizz.wanderlist.views.collection.CollectionsRoute
import com.rizz.wanderlist.views.collection.CreateCollectionRoute
import com.rizz.wanderlist.views.home.HomeRoute
import com.rizz.wanderlist.views.place.AddPlaceRoute
import com.rizz.wanderlist.views.place.AddPlaceViewModel
import com.rizz.wanderlist.views.place.MapPickerRoute
import com.rizz.wanderlist.views.place.PlaceDetailRoute
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.koin.compose.viewmodel.koinViewModel

private val navConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Route.Home::class, Route.Home.serializer())
            subclass(Route.Collections::class, Route.Collections.serializer())
            subclass(
                Route.CreateCollection::class,
                Route.CreateCollection.serializer()
            )
            subclass(Route.Profile::class, Route.Profile.serializer())
            subclass(
                Route.MapPicker::class,
                Route.MapPicker.serializer()
            )
            subclass(
                Route.AddPlace::class,
                Route.AddPlace.serializer()
            )

            subclass(Route.CollectionDetail::class,
                Route.CollectionDetail.serializer()
            )
            subclass(Route.PlaceDetail::class, Route.PlaceDetail.serializer())
            subclass(Route.EditPlace::class, Route.EditPlace.serializer())

        }
    }
}

@Composable
fun AppNavigation(onSignOut: () -> Unit) {
    val backStack = rememberNavBackStack(navConfig, Route.Home)
    val addPlaceViewModel: AddPlaceViewModel = koinViewModel()
    val currentRoute = backStack.lastOrNull()

    val showBottomBar = currentRoute is Route.Home ||
            currentRoute is Route.Collections ||
            currentRoute is Route.Profile

    val goBack: () -> Unit = {
        if (backStack.size > 1) {
            val leaving = backStack.lastOrNull()
            if (leaving is Route.AddPlace || leaving is Route.EditPlace) addPlaceViewModel.clearDraft()
            backStack.removeLastOrNull()
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {

                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    val itemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(
                            alpha = 0.12f
                        ),
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    NavigationBarItem(
                        selected = currentRoute is Route.Collections,
                        onClick = {
                            navigateToTab(backStack, Route.Collections)
                        },
                        icon = {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null
                            )
                        },
                        label = { Text("Collections") },
                        colors = itemColors
                    )

                    NavigationBarItem(
                        selected = currentRoute is Route.Home,
                        onClick = {
                            navigateToTab(backStack, Route.Home)
                        },
                        icon = {
                            Icon(Icons.Default.Map, contentDescription = null)
                        },
                        label = { Text("Map") },
                        colors = itemColors
                    )

                    NavigationBarItem(
                        selected = currentRoute is Route.Profile,
                        onClick = {
                            navigateToTab(backStack, Route.Profile)
                        },
                        icon = {
                            Icon(Icons.Default.Person, contentDescription = null)
                        },
                        label = { Text("Profile") },
                        colors = itemColors
                    )
                }
            }
        }
    ) { innerPadding ->
        NavDisplay(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            backStack = backStack,
            onBack = goBack,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator()
            ),
            entryProvider = entryProvider {
                entry<Route.Home> {
                    HomeRoute(
                        onCollectionsClick = {
                            if (backStack.lastOrNull() !is Route.Collections) {
                                backStack.add(Route.Collections)
                            }
                        },
                        onProfileClick = {
                            if (backStack.lastOrNull() !is Route.Profile) {
                                backStack.add(Route.Profile)
                            }
                        },
                        onAddClick = { backStack.add(Route.AddPlace) },
                        onFilterClick = {
                            // Collection filtering will be implemented next.
                        }
                    )
                }

                entry<Route.Collections> {
                    CollectionsRoute(
                        onAddClick = { backStack.add(Route.CreateCollection) },
                        onCollectionClick = { id -> backStack.add(Route.CollectionDetail(id)) },
                        onSharedPlaceClick = { id -> backStack.add(Route.PlaceDetail(id)) },
                        onSignOutClick = {
                            addPlaceViewModel.clearDraft()
                            onSignOut()
                        }
                    )
                }

                entry<Route.CreateCollection> {
                    CreateCollectionRoute(
                        onBack = {
                            backStack.removeLastOrNull()
                        },
                        onSaved = {
                            backStack.removeLastOrNull()
                        }
                    )
                }
                entry<Route.Profile> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Profile — coming next")
                    }
                }

                entry<Route.AddPlace> {
                    AddPlaceRoute(
                        viewModel = addPlaceViewModel,
                        onBack = goBack,
                        onOpenMapPicker = { backStack.add(Route.MapPicker) },
                        onCreateCollection = { backStack.add(Route.CreateCollection) },
                        onSaved = { collectionId ->
                            backStack.removeLastOrNull() // leave Add Place
                            // If we came from this collection's own page, just return to it.
                            if (backStack.lastOrNull() != Route.CollectionDetail(collectionId)) {
                                backStack.add(Route.CollectionDetail(collectionId))
                            }
                            addPlaceViewModel.clearDraft()
                        }
                    )
                }

                entry<Route.MapPicker> {
                    MapPickerRoute(
                        viewModel = addPlaceViewModel,
                        onBack = { backStack.removeLastOrNull() }
                    )
                }

                entry<Route.CollectionDetail> { key ->
                    CollectionDetailRoute(
                        collectionId = key.collectionId,
                        onBack = { backStack.removeLastOrNull() },
                        onPlaceClick = { id -> backStack.add(Route.PlaceDetail(id)) },
                        onAddPlace = {
                            addPlaceViewModel.onCollectionSelected(key.collectionId)
                            backStack.add(Route.AddPlace)
                        },
                        onDeleted = { backStack.removeLastOrNull() }
                    )
                }

                entry<Route.PlaceDetail> { key ->
                    PlaceDetailRoute(
                        placeId = key.placeId,
                        onBack = { backStack.removeLastOrNull() },
                        onEdit = { id ->
                            // Start loading before the screen appears, so it never flashes the empty form.
                            addPlaceViewModel.startEditing(id)
                            backStack.add(Route.EditPlace(id))
                        },
                        onPlaceRemoved = { backStack.removeLastOrNull() }
                    )
                }

                entry<Route.EditPlace> { key ->
                    // Normally started by onEdit already; this covers the screen being recreated.
                    LaunchedEffect(key.placeId) { addPlaceViewModel.startEditing(key.placeId) }
                    AddPlaceRoute(
                        viewModel = addPlaceViewModel,
                        onBack = goBack,
                        onOpenMapPicker = { backStack.add(Route.MapPicker) },
                        onCreateCollection = { backStack.add(Route.CreateCollection) },
                        onSaved = {
                            backStack.removeLastOrNull() // back to the place's detail page
                            addPlaceViewModel.clearDraft()
                        }
                    )
                }
            }
        )
    }
}


private fun navigateToTab(
    backStack: androidx.navigation3.runtime.NavBackStack<NavKey>,
    destination: Route
) {
    while (backStack.size > 1 && backStack.lastOrNull() !is Route.Home) {
        backStack.removeLastOrNull()
    }

    if (backStack.lastOrNull() != destination) {
        backStack.add(destination)
    }
}
