package com.rizz.wanderlist.navigation

sealed interface Screen {
    data object Collections : Screen
    data object CreateCollection : Screen
}