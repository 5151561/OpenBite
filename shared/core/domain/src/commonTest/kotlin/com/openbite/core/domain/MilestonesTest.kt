package com.openbite.core.domain

import com.openbite.core.model.Milestone
import com.openbite.core.model.MilestoneType
import com.openbite.core.model.Recurrence
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MilestonesTest {
    private fun milestone(date: String, recurrence: Recurrence = Recurrence.YEARLY) = Milestone(
        id = "m",
        title = "恋爱纪念日",
        type = MilestoneType.ANNIVERSARY,
        date = LocalDate.parse(date),
        recurrence = recurrence,
    )

    @Test
    fun yearlyLaterThisYear() {
        val next = nextOccurrence(milestone("2024-11-27"), LocalDate.parse("2026-11-17"))
        assertEquals(LocalDate.parse("2026-11-27"), next)
    }

    @Test
    fun yearlyAlreadyPassedMovesToNextYear() {
        val next = nextOccurrence(milestone("2024-05-20"), LocalDate.parse("2026-11-17"))
        assertEquals(LocalDate.parse("2027-05-20"), next)
    }

    @Test
    fun todayCounts() {
        val next = nextOccurrence(milestone("2024-11-17"), LocalDate.parse("2026-11-17"))
        assertEquals(LocalDate.parse("2026-11-17"), next)
    }

    @Test
    fun leapDayFallsBackToFeb28() {
        val next = nextOccurrence(milestone("2024-02-29"), LocalDate.parse("2026-01-10"))
        assertEquals(LocalDate.parse("2026-02-28"), next)
    }

    @Test
    fun oneOffInThePastIsGone() {
        assertNull(nextOccurrence(milestone("2025-01-01", Recurrence.NONE), LocalDate.parse("2026-11-17")))
    }

    @Test
    fun upcomingFiltersAndCountsYears() {
        val result = upcomingMilestones(
            milestones = listOf(milestone("2024-11-27"), milestone("2024-05-20")),
            today = LocalDate.parse("2026-11-17"),
            daysAhead = 30,
        )
        assertEquals(1, result.size)
        assertEquals(10, result.single().daysUntil)
        assertEquals(2, result.single().yearsSince)
    }

    @Test
    fun oneOffInTheFutureIsItsOwnDate() {
        assertEquals(LocalDate.parse("2027-01-01"), nextOccurrence(milestone("2027-01-01", Recurrence.NONE), LocalDate.parse("2026-11-17")))
    }

    @Test
    fun leapDayInLeapYearStaysOnFeb29() {
        assertEquals(LocalDate.parse("2028-02-29"), nextOccurrence(milestone("2024-02-29"), LocalDate.parse("2027-03-01")))
        assertEquals(LocalDate.parse("2028-02-29"), nextOccurrence(milestone("2024-02-29"), LocalDate.parse("2028-01-10")))
    }

    @Test
    fun yearlyStartingInTheFutureHasZeroYears() {
        val result = upcomingMilestones(listOf(milestone("2026-11-20")), LocalDate.parse("2026-11-17"), daysAhead = 30).single()
        assertEquals(LocalDate.parse("2026-11-20"), result.nextDate)
        assertEquals(0, result.yearsSince)
    }

    @Test
    fun daysAheadBoundaryIsInclusive() {
        val today = LocalDate.parse("2026-11-17")
        val m = listOf(milestone("2024-11-27"))
        assertEquals(1, upcomingMilestones(m, today, daysAhead = 10).size)
        assertEquals(0, upcomingMilestones(m, today, daysAhead = 9).size)
    }
}
