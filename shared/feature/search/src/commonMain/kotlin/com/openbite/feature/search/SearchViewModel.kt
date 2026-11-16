package com.openbite.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openbite.core.domain.SearchMemoriesUseCase
import com.openbite.core.domain.SearchPlacesUseCase
import com.openbite.core.domain.TodayProvider
import com.openbite.core.model.MemorySummary
import com.openbite.core.model.PlaceSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

enum class SearchKind { MEMORIES, PLACES }

sealed interface SearchResults {
    /** 还没输入。 */
    data object Idle : SearchResults
    data object Searching : SearchResults
    data class Memories(val items: List<MemorySummary>) : SearchResults
    data class Places(val items: List<PlaceSummary>) : SearchResults

    val isEmptyResult: Boolean get() = (this is Memories && items.isEmpty()) || (this is Places && items.isEmpty())
}

data class SearchUiState(
    val kind: SearchKind,
    val query: String = "",
    val results: SearchResults = SearchResults.Idle,
    val currentYear: Int = 0,
)

/** 输入停 [DEBOUNCE_MS] 毫秒再搜；新的输入会取消上一次搜索。 */
const val DEBOUNCE_MS = 250L

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
fun searchResults(
    queries: Flow<String>,
    search: suspend (String) -> SearchResults,
    debounceMillis: Long = DEBOUNCE_MS,
): Flow<SearchResults> = queries
    .map { it.trim() }
    .distinctUntilChanged()
    .debounce { if (it.isEmpty()) 0L else debounceMillis }
    .transformLatest { q ->
        if (q.isEmpty()) {
            emit(SearchResults.Idle)
        } else {
            emit(SearchResults.Searching)
            emit(search(q))
        }
    }

class SearchViewModel(
    kind: SearchKind,
    searchMemories: SearchMemoriesUseCase,
    searchPlaces: SearchPlacesUseCase,
    today: TodayProvider,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val year = today.today().year

    private val results = searchResults(query, { q ->
        when (kind) {
            SearchKind.MEMORIES -> SearchResults.Memories(searchMemories(q))
            SearchKind.PLACES -> SearchResults.Places(searchPlaces(q))
        }
    })

    val uiState: StateFlow<SearchUiState> = combine(query, results) { q, r -> SearchUiState(kind, q, r, year) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState(kind, currentYear = year))

    fun setQuery(value: String) {
        query.value = value
    }
}
