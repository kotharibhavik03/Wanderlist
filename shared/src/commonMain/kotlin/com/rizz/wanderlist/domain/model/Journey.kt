package com.rizz.wanderlist.domain.model

import com.rizz.wanderlist.util.distanceMeters

/**
 * A collection's visited places in the order they were visited: the route a journey replay
 * follows. Places without a visit date are placed by when they were saved.
 */
data class Journey(val stops: List<JourneyStop>) {

    /** The whole route, stop to stop in a straight line (as the crow flies), in kilometres. */
    val distanceKm: Double get() = stops.sumOf { it.kmFromPrevious ?: 0.0 }

    /** How many stops are placed by when they were saved, for lack of a visit date. */
    val stopsWithoutVisitDate: Int get() = stops.count { !it.hasVisitDate }

    /**
     * Calendar days from the first visit to the last, both included; null unless every stop has
     * a visit date (when they were saved says nothing about how long the trip took).
     */
    val days: Int? get() = if (stops.isEmpty() || stopsWithoutVisitDate > 0) null else dayOf(stops.last())

    /**
     * Which day of the journey [stop] was, 1 for the first visit's day; null unless every stop
     * has a visit date.
     */
    fun dayOf(stop: JourneyStop): Int? {
        if (stopsWithoutVisitDate > 0) return null
        return ((stop.date - stops.first().date) / DAY_MILLIS).toInt() + 1
    }

    /** A journey needs somewhere to go: at least two stops. */
    val canReplay: Boolean get() = stops.size >= MIN_STOPS

    companion object {
        const val MIN_STOPS = 2
    }
}

data class JourneyStop(
    val place: Place,
    /**
     * What puts the stop in its place: the visit date (UTC midnight, see
     * [Place.visitedOnEpochMillis]) when [hasVisitDate], else the moment the place was saved.
     */
    val date: Long,
    val hasVisitDate: Boolean,
    /** Straight-line distance from the stop before, in kilometres; null for the first stop. */
    val kmFromPrevious: Double?
)

/**
 * The journey through [places]: the visited ones, by visit date, or by when they were saved for
 * those without one. Places on the same day keep the order they were saved in. Wishlist places
 * aren't part of it.
 */
fun journeyOf(places: List<Place>): Journey {
    val ordered = places
        .filter { it.status == PlaceStatus.VISITED }
        .sortedWith(compareBy({ it.visitedOnEpochMillis ?: it.createdAtEpochMillis }, { it.createdAtEpochMillis }))
    val stops = ordered.mapIndexed { index, place ->
        val previous = ordered.getOrNull(index - 1)
        JourneyStop(
            place = place,
            date = place.visitedOnEpochMillis ?: place.createdAtEpochMillis,
            hasVisitDate = place.visitedOnEpochMillis != null,
            kmFromPrevious = previous?.let {
                distanceMeters(it.latitude, it.longitude, place.latitude, place.longitude) / 1000.0
            }
        )
    }
    return Journey(stops)
}

private const val DAY_MILLIS = 86_400_000L
