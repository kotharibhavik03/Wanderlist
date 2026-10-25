
package com.rizz.wanderlist.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey {

    @Serializable
    data object Home : Route

    @Serializable
    data object Collections : Route

    @Serializable
    data object CreateCollection : Route

    @Serializable
    data class EditCollection(val collectionId: String) : Route

    @Serializable
    data object Profile : Route

    @Serializable
    data object AddPlace : Route

    @Serializable
    data object MapPicker : Route

    @Serializable
    data class CollectionDetail(val collectionId: String) : Route

    @Serializable
    data class PlaceDetail(val placeId: String) : Route

    @Serializable
    data class EditPlace(val placeId: String) : Route

    @Serializable
    data class JourneyReplay(val collectionId: String) : Route
}
