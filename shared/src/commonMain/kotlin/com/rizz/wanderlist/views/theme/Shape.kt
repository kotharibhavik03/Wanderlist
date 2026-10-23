package com.rizz.wanderlist.views.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * One corner scale for the whole app. Material components pick from it automatically
 * (cards use medium, sheets extraLarge); custom surfaces use MaterialTheme.shapes too.
 */
internal val WanderlistShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),   // photo tiles, thumbnails
    medium = RoundedCornerShape(16.dp),  // cards, search field, small panels
    large = RoundedCornerShape(20.dp),   // map area, large panels
    extraLarge = RoundedCornerShape(28.dp)
)
