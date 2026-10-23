package com.rizz.wanderlist.domain.usecase

import com.rizz.wanderlist.domain.model.SelectedLocation
import com.rizz.wanderlist.domain.repository.PlaceSearch
import com.rizz.wanderlist.util.resultOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Results of searching everywhere for what the user is typing. */
data class LocationSearchState(
    val results: List<SelectedLocation> = emptyList(),
    val isLoading: Boolean = false,
    val failed: Boolean = false
)

/**
 * Search-as-you-type over [PlaceSearch]. Each search is a network request, so it only searches
 * once the query is [MIN_QUERY_LENGTH] characters long and typing pauses for [TYPING_PAUSE_MS];
 * a newer query cancels the search in progress.
 */
class SearchLocationsUseCase(private val placeSearch: PlaceSearch) {

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(queries: Flow<String>): Flow<LocationSearchState> = queries
        .map { it.trim() }
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.length < MIN_QUERY_LENGTH) {
                flowOf(LocationSearchState())
            } else {
                flow {
                    emit(LocationSearchState(isLoading = true))
                    delay(TYPING_PAUSE_MS)
                    emit(
                        resultOf { placeSearch.search(query) }.fold(
                            onSuccess = { LocationSearchState(results = it) },
                            onFailure = { LocationSearchState(failed = true) }
                        )
                    )
                }
            }
        }

    companion object {
        const val MIN_QUERY_LENGTH = 3
        private const val TYPING_PAUSE_MS = 400L
    }
}
