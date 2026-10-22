package com.rizz.wanderlist.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.rizz.wanderlist.views.collection.CollectionsRoute
import com.rizz.wanderlist.views.collection.CreateCollectionRoute
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

// Every new Route MUST be registered here, or the app will crash on iOS.
private val navConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Route.Collections::class, Route.Collections.serializer())
            subclass(Route.CreateCollection::class, Route.CreateCollection.serializer())
        }
    }
}

@Composable
fun AppNavigation(onSignOut: () -> Unit) {
    val backStack = rememberNavBackStack(navConfig, Route.Collections)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<Route.Collections> {
                CollectionsRoute(
                    onAddClick = { backStack.add(Route.CreateCollection) },
                    onSignOutClick = onSignOut
                )
            }
            entry<Route.CreateCollection> {
                CreateCollectionRoute(
                    onBack = { backStack.removeLastOrNull() },
                    onSaved = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}