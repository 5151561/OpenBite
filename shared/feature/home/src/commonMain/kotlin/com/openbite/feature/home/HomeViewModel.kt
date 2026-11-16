package com.openbite.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openbite.core.domain.ObserveMemoriesUseCase
import com.openbite.core.domain.ObserveMilestoneHighlightsUseCase
import com.openbite.core.domain.TodayProvider
import com.openbite.core.model.MemorySummary
import com.openbite.core.model.MilestoneHighlight
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate

data class HomeUiState(
    val isLoading: Boolean = true,
    val today: LocalDate? = null,
    /** 30 天内最近的一个纪念日，没有就不显示卡片。 */
    val nextMilestone: MilestoneHighlight? = null,
    val recent: List<MemorySummary> = emptyList(),
)

class HomeViewModel(
    observeMemories: ObserveMemoriesUseCase,
    observeHighlights: ObserveMilestoneHighlightsUseCase,
    todayProvider: TodayProvider,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> =
        combine(observeMemories(), observeHighlights(daysAhead = NEXT_MILESTONE_DAYS)) { memories, highlights ->
            HomeUiState(
                isLoading = false,
                today = todayProvider.today(),
                nextMilestone = highlights.firstOrNull(),
                recent = memories.take(RECENT_COUNT),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
        )

    private companion object {
        const val NEXT_MILESTONE_DAYS = 30
        const val RECENT_COUNT = 3
    }
}
