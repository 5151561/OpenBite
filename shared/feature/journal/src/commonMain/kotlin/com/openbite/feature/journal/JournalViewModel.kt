package com.openbite.feature.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openbite.core.domain.ObserveDiningLogsUseCase
import com.openbite.core.domain.ObserveUpcomingMilestonesUseCase
import com.openbite.core.model.DiningLog
import com.openbite.core.model.UpcomingMilestone
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class JournalUiState(
    val isLoading: Boolean = true,
    val upcoming: List<UpcomingMilestone> = emptyList(),
    val logs: List<DiningLog> = emptyList(),
)

class JournalViewModel(
    observeLogs: ObserveDiningLogsUseCase,
    observeUpcoming: ObserveUpcomingMilestonesUseCase,
) : ViewModel() {

    val uiState: StateFlow<JournalUiState> =
        combine(observeLogs(), observeUpcoming(daysAhead = 30)) { logs, upcoming ->
            JournalUiState(isLoading = false, upcoming = upcoming, logs = logs)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = JournalUiState(),
        )
}
