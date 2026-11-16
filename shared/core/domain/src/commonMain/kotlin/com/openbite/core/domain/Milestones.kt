package com.openbite.core.domain

import com.openbite.core.model.Milestone
import com.openbite.core.model.Recurrence
import com.openbite.core.model.UpcomingMilestone
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.daysUntil

/**
 * 算出纪念日下一次发生的日期（含今天）。
 * 2 月 29 日的纪念日，在平年按 2 月 28 日算。一次性的纪念日过了就返回 null。
 */
fun nextOccurrence(milestone: Milestone, today: LocalDate): LocalDate? {
    if (milestone.recurrence == Recurrence.NONE) {
        return milestone.date.takeIf { it >= today }
    }
    if (milestone.date >= today) return milestone.date
    val thisYear = milestone.date.inYear(today.year)
    return if (thisYear >= today) thisYear else milestone.date.inYear(today.year + 1)
}

/** 未来 [daysAhead] 天内（含今天）的纪念日，按日期排序。 */
fun upcomingMilestones(
    milestones: List<Milestone>,
    today: LocalDate,
    daysAhead: Int,
): List<UpcomingMilestone> = milestones
    .mapNotNull { m ->
        val next = nextOccurrence(m, today) ?: return@mapNotNull null
        val days = today.daysUntil(next)
        if (days > daysAhead) return@mapNotNull null
        UpcomingMilestone(
            milestone = m,
            nextDate = next,
            daysUntil = days,
            yearsSince = next.year - m.date.year,
        )
    }
    .sortedBy { it.daysUntil }

private fun LocalDate.inYear(year: Int): LocalDate {
    val day = if (month == Month.FEBRUARY && day == 29 && !isLeapYear(year)) 28 else day
    return LocalDate(year, month, day)
}

private fun isLeapYear(year: Int) = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
