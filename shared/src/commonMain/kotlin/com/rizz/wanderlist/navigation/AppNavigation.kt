
package com.rizz.wanderlist.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.rizz.wanderlist.views.collection.CollectionDetailRoute
import com.rizz.wanderlist.views.collection.CollectionsRoute
import com.rizz.wanderlist.views.collection.CollectionFormRoute
import com.rizz.wanderlist.views.home.HomeRoute
import com.rizz.wanderlist.views.place.AddPlaceRoute
import com.rizz.wanderlist.views.place.AddPlaceViewModel
import com.rizz.wanderlist.views.mapPicker.MapPickerRoute
import com.rizz.wanderlist.views.place.PlaceDetailRoute
import com.rizz.wanderlist.views.profile.ProfileRoute
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.koin.compose.viewmodel.koinViewModel

/** The floating bottom bar's corner radius and its gaps to the screen edges. */
private val BottomBarCornerRadius = 24.dp
private val BottomBarHorizontalMargin = 12.dp
private val BottomBarVerticalMargin = 8.dp

private val navConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Route.Home::class, Route.Home.serializer())
            subclass(Route.Collections::class, Route.Collections.serializer())
            subclass(
                Route.CreateCollection::class,
                Route.CreateCollection.serializer()
            )
            subclass(
                Route.EditCollection::class,
                Route.EditCollection.serializer()
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
        // The frame reserves only the bottom bar. Each screen handles the status bar itself, so
        // Home's map can draw behind it; screens with a top app bar get the inset from that bar.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                // A floating bar: rounded, clear of the screen edges and of the system navigation area.
                Surface(
                    shape = RoundedCornerShape(BottomBarCornerRadius),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = BottomBarHorizontalMargin, vertical = BottomBarVerticalMargin)
                ) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        // The system navigation inset is already outside the bar, as margin.
                        windowInsets = WindowInsets(0, 0, 0, 0)
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
        }
    ) { innerPadding ->
        // Content draws behind the floating bottom bar. Each tab decides how to keep clear of it:
        // Home lets its map run underneath, the other tabs sit above it (AboveBottomBar).
        val bottomBarPadding = innerPadding.calculateBottomPadding()
        NavDisplay(
            modifier = Modifier.fillMaxSize(),
            backStack = backStack,
            onBack = goBack,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator()
            ),
            entryProvider = entryProvider {
                entry<Route.Home> {
                    HomeRoute(
                        bottomBarPadding = bottomBarPadding,
                        onAddClick = { backStack.add(Route.AddPlace) },
                        onPlaceClick = { id -> backStack.add(Route.PlaceDetail(id)) },
                        onSaveLocation = { location ->
                            // The draft is empty here (it's cleared whenever Add Place closes).
                            addPlaceViewModel.onLocationSelected(location)
                            backStack.add(Route.AddPlace)
                        }
                    )
                }

                entry<Route.Collections> {
                    AboveBottomBar(bottomBarPadding) {
                        CollectionsRoute(
                            onAddClick = { backStack.add(Route.CreateCollection) },
                            onCollectionClick = { id -> backStack.add(Route.CollectionDetail(id)) },
                            onSharedPlaceClick = { id -> backStack.add(Route.PlaceDetail(id)) }
                        )
                    }
                }

                entry<Route.CreateCollection> {
                    CollectionFormRoute(
                        collectionId = null,
                        onBack = { backStack.removeLastOrNull() },
                        onSaved = { backStack.removeLastOrNull() }
                    )
                }
                entry<Route.EditCollection> { key ->
                    CollectionFormRoute(
                        collectionId = key.collectionId,
                        onBack = { backStack.removeLastOrNull() },
                        // Back to the collection's page, which shows the change live.
                        onSaved = { backStack.removeLastOrNull() }
                    )
                }
                entry<Route.Profile> {
                    AboveBottomBar(bottomBarPadding) {
                        ProfileRoute(
                            onSignOut = {
                                addPlaceViewModel.clearDraft()
                                onSignOut()
                            }
                        )
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
                    // Starts from the draft's location: the place being edited, or one saved from search.
                    val initial = remember { addPlaceViewModel.uiState.value.selectedLocation }
                    MapPickerRoute(
                        initial = initial,
                        onConfirm = { location ->
                            addPlaceViewModel.onLocationSelected(location)
                            backStack.removeLastOrNull()
                        },
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
                        onEdit = { backStack.add(Route.EditCollection(key.collectionId)) },
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


/**
 * Keeps a tab's content above the floating bottom bar. The padding is also marked as consumed,
 * so the screen's own Scaffold doesn't add the system navigation inset a second time.
 */
@Composable
private fun AboveBottomBar(bottomBarPadding: Dp, content: @Composable () -> Unit) {
    val padding = PaddingValues(bottom = bottomBarPadding)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .consumeWindowInsets(padding)
    ) { content() }
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
