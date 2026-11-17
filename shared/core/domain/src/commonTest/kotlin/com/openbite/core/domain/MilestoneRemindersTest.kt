package com.openbite.core.domain

import com.openbite.core.model.Milestone
import com.openbite.core.model.MilestoneType
import com.openbite.core.model.MilestoneWithPeople
import com.openbite.core.model.Recurrence
import com.openbite.core.model.ReminderSettings
import com.openbite.core.testing.FakeLogs
import com.openbite.core.testing.FakeMilestones
import com.openbite.core.testing.FakePreferences
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MilestoneRemindersTest {
    private val today = LocalDate(2026, 11, 17)
    private val prefs = FakePreferences()
    private val love = Milestone("love", "恋爱纪念日", MilestoneType.ANNIVERSARY, LocalDate(2024, 11, 27), Recurrence.YEARLY, remindDaysBefore = 14)
    private val later = Milestone("later", "生日", MilestoneType.BIRTHDAY, LocalDate(1995, 12, 30), Recurrence.YEARLY, remindDaysBefore = 7)
    private val reminders = MilestoneRemindersUseCase(
        ObserveMilestoneHighlightsUseCase(
            ObserveUpcomingMilestonesUseCase(FakeMilestones(listOf(love, later).map { MilestoneWithPeople(it, emptyList()) })) { today },
            ObserveMemoriesUseCase(FakeLogs()) { today },
        ),
        prefs,
    )

    @Test
    fun dueOnlyInsideRemindWindowAndOncePerYear() = runTest {
        val due = reminders.due()
        assertEquals(listOf("love"), due.map { it.upcoming.milestone.id })
        reminders.markSent(due)
        assertEquals(setOf("love:2026"), prefs.sent)
        assertTrue(reminders.due().isEmpty())
    }

    @Test
    fun switchOffMeansNothingIsDue() = runTest {
        prefs.reminderSettings.value = ReminderSettings(mealFollowUp = true, milestones = false)
        assertTrue(reminders.due().isEmpty())
    }

    @Test
    fun markingNothingWritesNothing() = runTest {
        reminders.markSent(emptyList())
        assertTrue(prefs.sent.isEmpty())
    }
}
