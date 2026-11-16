package com.openbite.feature.milestone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openbite.core.domain.ObserveMilestoneHighlightsUseCase
import com.openbite.core.model.MilestoneHighlight
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class MilestonesUiState(
    val isLoading: Boolean = true,
    /** 最近的一个，显示成大卡片。 */
    val next: MilestoneHighlight? = null,
    /** 之后的，按日期排。 */
    val later: List<MilestoneHighlight> = emptyList(),
) {
    /** 提醒提前几天：取最近一个纪念日的设置。 */
    val remindDaysBefore: Int get() = next?.upcoming?.milestone?.remindDaysBefore ?: DEFAULT_REMIND_DAYS

    companion object {
        const val DEFAULT_REMIND_DAYS = 7
    }
}

class MilestonesViewModel(
    observeHighlights: ObserveMilestoneHighlightsUseCase,
) : ViewModel() {

    val uiState: StateFlow<MilestonesUiState> =
        observeHighlights(daysAhead = DAYS_AHEAD).map { all ->
            MilestonesUiState(isLoading = false, next = all.firstOrNull(), later = all.drop(1))
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MilestonesUiState(),
        )

    private companion object {
        /** 每年重复的纪念日一年内必定出现；多留一些给明年的一次性纪念日。 */
        const val DAYS_AHEAD = 400
    }
}
