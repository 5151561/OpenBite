package com.openbite.feature.milestone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openbite.core.model.ReminderSettings
import com.openbite.core.domain.ObserveMilestoneHighlightsUseCase
import com.openbite.core.domain.ObserveReminderSettingsUseCase
import com.openbite.core.domain.SetReminderSettingsUseCase
import kotlinx.coroutines.launch
import com.openbite.core.model.MilestoneHighlight
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import com.openbite.core.domain.ObserveAllMilestonesUseCase
import com.openbite.core.model.Milestone
import kotlinx.coroutines.flow.stateIn

data class MilestonesUiState(
    val isLoading: Boolean = true,
    /** 最近的一个，显示成大卡片。 */
    val next: MilestoneHighlight? = null,
    /** 之后的，按日期排。 */
    val later: List<MilestoneHighlight> = emptyList(),
    /** 已经过去的一次性纪念日（不再出现在“之后”里，但还能点开改或删）。 */
    val past: List<Milestone> = emptyList(),
) {
    /** 提醒提前几天：取最近一个纪念日的设置。 */
    val remindDaysBefore: Int get() = next?.upcoming?.milestone?.remindDaysBefore ?: DEFAULT_REMIND_DAYS

    companion object {
        const val DEFAULT_REMIND_DAYS = 7
    }
}

class MilestonesViewModel(
    observeHighlights: ObserveMilestoneHighlightsUseCase,
    observeAllMilestones: ObserveAllMilestonesUseCase,
    observeReminders: ObserveReminderSettingsUseCase,
    private val setReminders: SetReminderSettingsUseCase,
) : ViewModel() {

    /** “系统通知”开关：纪念日提醒。加载完之前为 null。 */
    val reminders: StateFlow<ReminderSettings?> = observeReminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setMilestoneReminders(enabled: Boolean) {
        val current = reminders.value ?: return
        viewModelScope.launch { setReminders(current.copy(milestones = enabled)) }
    }

    val uiState: StateFlow<MilestonesUiState> =
        combine(observeHighlights(daysAhead = DAYS_AHEAD), observeAllMilestones()) { all, every ->
            val upcomingIds = all.map { it.upcoming.milestone.id }.toSet()
            MilestonesUiState(
                isLoading = false,
                next = all.firstOrNull(),
                later = all.drop(1),
                past = every.filterNot { it.id in upcomingIds }.sortedByDescending { it.date },
            )
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
