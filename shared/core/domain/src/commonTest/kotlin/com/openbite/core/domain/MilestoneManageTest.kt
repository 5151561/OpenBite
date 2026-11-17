package com.openbite.core.domain

import com.openbite.core.testing.FakePreferences
import com.openbite.core.testing.FakeMilestones
import com.openbite.core.data.MilestoneRepository
import com.openbite.core.model.MilestoneWithPeople
import com.openbite.core.model.Milestone
import com.openbite.core.model.MilestoneType
import com.openbite.core.model.Recurrence
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MilestoneManageTest {
    private val repo = FakeMilestones()
    private val prefs = FakePreferences()
    private val save = SaveMilestoneUseCase(repo, prefs, { "new-id" })
    private val love = MilestoneDraft(title = " 恋爱纪念日 ", date = LocalDate(2024, 11, 27), personIds = setOf("wife"))

    @Test
    fun validation() {
        assertEquals(MilestoneDraftError.TITLE_REQUIRED, validateMilestone(love.copy(title = "  ")))
        assertEquals(MilestoneDraftError.TITLE_TOO_LONG, validateMilestone(love.copy(title = "x".repeat(MAX_MILESTONE_TITLE + 1))))
        assertEquals(MilestoneDraftError.DATE_REQUIRED, validateMilestone(love.copy(date = null)))
        assertEquals(MilestoneDraftError.BAD_REMIND_DAYS, validateMilestone(love.copy(remindDaysBefore = 5)))
        assertNull(validateMilestone(love))
    }

    @Test
    fun createTrimsTitleAndStoresPeople() = runTest {
        val result = assertIs<SaveMilestoneResult.Saved>(save(love))
        assertEquals("new-id", result.id)
        val stored = repo.milestone("new-id")!!
        assertEquals("恋爱纪念日", stored.milestone.title)
        assertEquals(listOf("wife"), stored.personIds)
        assertIs<SaveMilestoneResult.Invalid>(save(love.copy(title = "")))
    }

    @Test
    fun editingDateResetsSentReminderButRenamingDoesNot() = runTest {
        save(love)
        prefs.markMilestoneRemindersSent(setOf("new-id:2026", "other:2026"))
        val draft = MilestoneDraft.from(repo.milestone("new-id")!!)
        save(draft.copy(title = "恋爱纪念"))
        assertEquals(setOf("new-id:2026", "other:2026"), prefs.sentMilestoneReminders())
        save(draft.copy(remindDaysBefore = 14))
        assertEquals(setOf("other:2026"), prefs.sentMilestoneReminders())
    }

    @Test
    fun deleteRemovesAndForgetsReminders() = runTest {
        save(love)
        repo.tagged["new-id"] = 2
        assertEquals(2, TaggedMealCountUseCase(repo)("new-id"))
        prefs.markMilestoneRemindersSent(setOf("new-id:2026"))
        DeleteMilestoneUseCase(repo, prefs)("new-id")
        assertNull(repo.milestone("new-id"))
        assertTrue(prefs.sentMilestoneReminders().isEmpty())
    }

    @Test
    fun reminderRelevantChanges() {
        val m = Milestone("m", "a", MilestoneType.OTHER, LocalDate(2024, 1, 1), Recurrence.YEARLY, 7)
        assertTrue(affectsReminders(m, m.copy(date = LocalDate(2024, 1, 2))))
        assertTrue(affectsReminders(m, m.copy(recurrence = Recurrence.NONE)))
        assertEquals(false, affectsReminders(m, m.copy(title = "b", type = MilestoneType.BIRTHDAY)))
    }
}
