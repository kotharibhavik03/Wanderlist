package com.rizz.wanderlist.views.journey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rizz.wanderlist.domain.model.Journey
import com.rizz.wanderlist.domain.model.JourneyStop
import com.rizz.wanderlist.domain.model.PlaceCollection
import com.rizz.wanderlist.domain.model.journeyOf
import com.rizz.wanderlist.domain.repository.CollectionRepository
import com.rizz.wanderlist.domain.repository.PlaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Where the replay is: the whole route before it starts, one stop, or the end. */
sealed interface JourneyPosition {
    data object Overview : JourneyPosition
    data class AtStop(val index: Int) : JourneyPosition
    data object Finished : JourneyPosition
}

data class JourneyReplayUiState(
    val collection: PlaceCollection? = null,
    val journey: Journey = Journey(stops = emptyList()),
    val position: JourneyPosition = JourneyPosition.Overview,
    val isLoading: Boolean = true
) {
    /** The stop being shown, or null on the overview and at the end. */
    val currentStop: JourneyStop?
        get() = (position as? JourneyPosition.AtStop)?.let { journey.stops.getOrNull(it.index) }

    /** How many stops have been reached: everything up to and including the current one. */
    val reachedStops: Int
        get() = when (position) {
            JourneyPosition.Overview -> 0
            is JourneyPosition.AtStop -> position.index + 1
            JourneyPosition.Finished -> journey.stops.size
        }
}

/** A collection's visited places, replayed stop by stop in the order they were visited. */
class JourneyReplayViewModel(
    collectionId: String,
    collectionRepository: CollectionRepository,
    placeRepository: PlaceRepository
) : ViewModel() {

    private val position = MutableStateFlow<JourneyPosition>(JourneyPosition.Overview)

    val uiState: StateFlow<JourneyReplayUiState> = combine(
        collectionRepository.observeCollections().map { list -> list.firstOrNull { it.id == collectionId } },
        placeRepository.observePlaces(collectionId).map(::journeyOf),
        position
    ) { collection, journey, position ->
        JourneyReplayUiState(
            collection = collection,
            journey = journey,
            position = position.within(journey),
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JourneyReplayUiState())

    /** From the overview or the end, back to the first stop. */
    fun onStart() {
        position.value = JourneyPosition.AtStop(0)
    }

    /** A tapped marker: go straight to that stop. */
    fun onStopSelected(index: Int) {
        if (index in uiState.value.journey.stops.indices) position.value = JourneyPosition.AtStop(index)
    }

    fun onNext() {
        val stops = uiState.value.journey.stops.size
        position.update { current ->
            when (current) {
                JourneyPosition.Overview -> JourneyPosition.AtStop(0)
                is JourneyPosition.AtStop ->
                    if (current.index + 1 < stops) JourneyPosition.AtStop(current.index + 1) else JourneyPosition.Finished
                JourneyPosition.Finished -> current
            }
        }
    }

    fun onPrevious() {
        val stops = uiState.value.journey.stops.size
        position.update { current ->
            when (current) {
                JourneyPosition.Overview -> current
                is JourneyPosition.AtStop ->
                    if (current.index > 0) JourneyPosition.AtStop(current.index - 1) else JourneyPosition.Overview
                JourneyPosition.Finished -> JourneyPosition.AtStop(stops - 1)
            }
        }
    }
}

/** A stop that no longer exists (a place was deleted or moved meanwhile) falls back to a valid one. */
private fun JourneyPosition.within(journey: Journey): JourneyPosition = when {
    this !is JourneyPosition.AtStop -> this
    journey.stops.isEmpty() -> JourneyPosition.Overview
    index >= journey.stops.size -> JourneyPosition.AtStop(journey.stops.lastIndex)
    else -> this
}
