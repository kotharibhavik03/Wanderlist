package com.rizz.wanderlist.model

data class PlaceCollection(
    val id: String,
    val name: String,
    val icon: String,        // an emoji for now; we'll add an icon picker later
    val colors: List<Long>,  // several colors, stored as ARGB numbers
    val placeCount: Int
)