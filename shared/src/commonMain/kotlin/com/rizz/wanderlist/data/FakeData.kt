package com.rizz.wanderlist.data

import com.rizz.wanderlist.domain.model.PlaceCollection

object FakeData {
    val collections = listOf(
        PlaceCollection("1", "Goa trip", "🏖️", listOf(0xFF00ACC1, 0xFFFFB300), 4),
        PlaceCollection("2", "Wanted to visit", "⭐", listOf(0xFF7E57C2, 0xFFEC407A), 12),
        PlaceCollection("3", "Visited", "✅", listOf(0xFF43A047), 7),
        PlaceCollection("4", "Cafes", "☕", listOf(0xFF6D4C41, 0xFFFF7043, 0xFFFFCA28), 9)
    )
}