package com.openbite.feature.milestone

import com.openbite.core.domain.ObserveAllMilestonesUseCase
import com.openbite.core.domain.ObserveMemoriesUseCase
import com.openbite.core.domain.ObserveMilestoneHighlightsUseCase
import com.openbite.core.domain.ObserveReminderSettingsUseCase
import com.openbite.core.domain.ObserveUpcomingMilestonesUseCase
import com.openbite.core.domain.SetReminderSettingsUseCase
import com.openbite.core.model.Milestone
import com.openbite.core.model.MilestoneType
import com.openbite.core.model.MilestoneWithPeople
import com.openbite.core.model.Recurrence
import com.openbite.core.testing.FakeLogs
import com.openbite.core.testing.FakeMilestones
import com.openbite.core.testing.FakePreferences
import com.openbite.core.testing.FakeReminders
import com.openbite.core.testing.diningLog
import com.openbite.core.testing.keepCollecting
import com.openbite.core.testing.runViewModelTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MilestonesViewModelTest {
    private val today = LocalDate(2026, 11, 17)
    private val love = Milestone("ms-love", "恋爱纪念日", MilestoneType.ANNIVERSARY, LocalDate(2024, 11, 27), Recurrence.YEARLY, remindDaysBefore = 3)
    private val birthday = Milestone("ms-bd", "生日", MilestoneType.BIRTHDAY, LocalDate(1995, 3, 2), Recurrence.YEARLY)
    private val farewell = Milestone("ms-past", "送别", MilestoneType.OTHER, LocalDate(2025, 6, 1), Recurrence.NONE)
    private val prefs = FakePreferences()
    private val milestones = FakeMilestones(listOf(love, birthday, farewell).map { MilestoneWithPeople(it, emptyList()) })
    private val logs = FakeLogs(listOf(diningLog("log-2025", milestoneId = love.id, date = LocalDate(2025, 11, 27))))

    private fun viewModel() = MilestonesViewModel(
        ObserveMilestoneHighlightsUseCase(
            ObserveUpcomingMilestonesUseCase(milestones) { today },
            ObserveMemoriesUseCase(logs) { today },
        ),
        ObserveAllMilestonesUseCase(milestones),
        ObserveReminderSettingsUseCase(prefs),
        SetReminderSettingsUseCase(prefs, FakeReminders()),
    )

    @Test
    fun nextLaterAndPastInOneState() = runViewModelTest {
        val vm = viewModel()
        keepCollecting(vm.uiState)
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(love.id, state.next!!.upcoming.milestone.id)
        assertEquals(10, state.next!!.upcoming.daysUntil)
        assertEquals(3, state.remindDaysBefore)
        assertEquals(listOf(birthday.id), state.later.map { it.upcoming.milestone.id })
        assertEquals(listOf(farewell.id), state.past.map { it.id })
        assertEquals(true, state.reminders!!.milestones)
    }

    @Test
    fun turningOffMilestoneRemindersKeepsMealReminders() = runViewModelTest {
        val vm = viewModel()
        keepCollecting(vm.uiState)
        advanceUntilIdle()
        vm.setMilestoneReminders(false)
        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.reminders!!.milestones)
        assertTrue(prefs.reminderSettings.value.mealFollowUp)
    }
}
