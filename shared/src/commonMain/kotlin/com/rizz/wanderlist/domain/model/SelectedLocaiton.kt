
package com.rizz.wanderlist.domain.model

/**
 * A location selected during the add-place flow.
 * This is temporary until the user saves the place.
 */
data class SelectedLocation(
    val latitude: Double,
    val longitude: Double,
    val actualName: String,
    val address: String? = null,
    val placeId: String? = null
)
