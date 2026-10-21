package com.rizz.wanderlist

import androidx.compose.runtime.*
import com.rizz.wanderlist.data.repository.InMemoryCollectionRepository
import com.rizz.wanderlist.navigation.Screen
import com.rizz.wanderlist.views.collection.CollectionsScreen
import com.rizz.wanderlist.views.collection.CreateCollectionScreen
import com.rizz.wanderlist.views.theme.WanderlistTheme
import kotlinx.coroutines.launch

@Composable
fun App() {
    WanderlistTheme {
        val repository = remember { InMemoryCollectionRepository() }
        val scope = rememberCoroutineScope()
        val collections by repository.observeCollections().collectAsState(initial = emptyList())
        var currentScreen by remember { mutableStateOf<Screen>(Screen.Collections) }

        when (currentScreen) {
            Screen.Collections -> CollectionsScreen(
                collections = collections,
                onAddClick = { currentScreen = Screen.CreateCollection }
            )

            Screen.CreateCollection -> CreateCollectionScreen(
                onBack = { currentScreen = Screen.Collections },
                onSave = { name, icon, colors ->
                    scope.launch {
                        repository.createCollection(name, icon, colors)
                        currentScreen = Screen.Collections
                    }
                }
            )
        }
    }
}