package com.openbite.feature.memories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openbite.core.domain.ObserveMemoriesUseCase
import com.openbite.core.domain.TodayProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class MemoriesUiState(
    val isLoading: Boolean = true,
    val filter: MemoryFilter = MemoryFilter.ALL,
    val currentYear: Int = 0,
    val groups: List<MonthGroup> = emptyList(),
    /** 筛选前一条记录都没有（区分“还没记录”和“筛选后为空”）。 */
    val isEmpty: Boolean = false,
)

class MemoriesViewModel(
    observeMemories: ObserveMemoriesUseCase,
    private val todayProvider: TodayProvider,
) : ViewModel() {

    private val filter = MutableStateFlow(MemoryFilter.ALL)

    val uiState: StateFlow<MemoriesUiState> =
        combine(observeMemories(), filter) { memories, selected ->
            MemoriesUiState(
                isLoading = false,
                filter = selected,
                currentYear = todayProvider.today().year,
                groups = groupByMonth(filterMemories(memories, selected)),
                isEmpty = memories.isEmpty(),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MemoriesUiState(),
        )

    fun selectFilter(selected: MemoryFilter) {
        filter.value = selected
    }
}
