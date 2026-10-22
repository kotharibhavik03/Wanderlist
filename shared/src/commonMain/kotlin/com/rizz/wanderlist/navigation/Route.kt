package com.rizz.wanderlist.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey {

    @Serializable
    data object Collections : Route

    @Serializable
    data object CreateCollection : Route
}