package com.openbite.core.domain

import com.openbite.core.data.DiningLogRepository
import com.openbite.core.data.MilestoneRepository
import com.openbite.core.model.DiningLog
import com.openbite.core.model.UpcomingMilestone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** 当前的当地日期。测试里换成固定日期。 */
fun interface TodayProvider {
    fun today(): LocalDate
}

@OptIn(ExperimentalTime::class)
class SystemTodayProvider : TodayProvider {
    override fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
}

class ObserveDiningLogsUseCase(private val repository: DiningLogRepository) {
    operator fun invoke(): Flow<List<DiningLog>> = repository.observeLogs()
}

class ObserveUpcomingMilestonesUseCase(
    private val repository: MilestoneRepository,
    private val todayProvider: TodayProvider,
) {
    operator fun invoke(daysAhead: Int = 30): Flow<List<UpcomingMilestone>> =
        repository.observeMilestones().map { upcomingMilestones(it, todayProvider.today(), daysAhead) }
}
