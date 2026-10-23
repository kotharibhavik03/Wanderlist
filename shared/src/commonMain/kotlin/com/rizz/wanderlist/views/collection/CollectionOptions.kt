package com.rizz.wanderlist.views.collection

/** One choice in the Create Collection pickers: the stored [value] and a [label] for screen readers. */
data class CollectionOption<T>(val value: T, val label: String)

object CollectionOptions {
    val icons = listOf(
        CollectionOption("🏖️", "Beach"),
        CollectionOption("⭐", "Star"),
        CollectionOption("✅", "Check mark"),
        CollectionOption("☕", "Coffee"),
        CollectionOption("🏔️", "Mountain"),
        CollectionOption("🍜", "Food"),
        CollectionOption("🏛️", "Museum"),
        CollectionOption("🛕", "Temple"),
        CollectionOption("🌆", "City"),
        CollectionOption("🌲", "Forest"),
        CollectionOption("🎡", "Fun fair"),
        CollectionOption("🚗", "Road trip"),
        CollectionOption("✈️", "Flight"),
        CollectionOption("🏕️", "Camping"),
        CollectionOption("🎭", "Theatre"),
        CollectionOption("🛍️", "Shopping")
    )

    val colors = listOf<CollectionOption<Long>>(
        CollectionOption(0xFF00ACC1, "Cyan"),
        CollectionOption(0xFFFFB300, "Amber"),
        CollectionOption(0xFF7E57C2, "Purple"),
        CollectionOption(0xFFEC407A, "Pink"),
        CollectionOption(0xFF43A047, "Green"),
        CollectionOption(0xFF6D4C41, "Brown"),
        CollectionOption(0xFFFF7043, "Orange"),
        CollectionOption(0xFF1E88E5, "Blue"),
        CollectionOption(0xFFE53935, "Red"),
        CollectionOption(0xFF546E7A, "Blue grey")
    )
}
