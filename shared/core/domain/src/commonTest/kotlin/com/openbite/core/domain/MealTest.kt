package com.openbite.core.domain

import com.openbite.core.model.DiningLog
import com.openbite.core.model.DiningLogItem
import com.openbite.core.model.DiningStatus
import com.openbite.core.model.Glyph
import com.openbite.core.model.GlyphTone
import com.openbite.core.model.LogSource
import com.openbite.core.model.Milestone
import com.openbite.core.model.MilestoneHighlight
import com.openbite.core.model.MilestoneType
import com.openbite.core.model.Money
import com.openbite.core.model.Place
import com.openbite.core.model.Recurrence
import com.openbite.core.model.UpcomingMilestone
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MealTest {
    private val place = Place("p", "割烹 みなと", null, null, null)

    private fun log(rating: Int?, itemRatings: List<Int?>, paid: Money? = null) = DiningLog(
        id = "l", place = place, visitedAt = 0, timezone = "Asia/Tokyo", localDate = LocalDate(2026, 10, 3),
        status = DiningStatus.DONE, milestoneId = null, note = null, source = LogSource.USER,
        items = itemRatings.mapIndexed { i, r -> DiningLogItem("i$i", null, "鰆の西京焼き", Money(1000, "JPY"), 1, r, null, "西京烧") },
        rating = rating, paidTotal = paid,
    )

    @Test
    fun mealRatingWinsOverDishAverage() {
        assertEquals(5.0, mealRating(log(5, listOf(4, 5, 4))))
        assertEquals(4.5, mealRating(log(null, listOf(4, 5))))
        assertNull(mealRating(log(null, listOf(null))))
    }

    @Test
    fun summaryUsesTranslatedNamesAndPaidTotal() {
        val summary = summarize(listOf(log(null, listOf(4), paid = Money(4246, "JPY"))), LocalDate(2026, 11, 17)).single()
        assertEquals(listOf("西京烧"), summary.dishNames)
        assertEquals(Money(4246, "JPY"), summary.total)
        val unpaid = summarize(listOf(log(null, listOf(4, 4))), LocalDate(2026, 11, 17)).single()
        assertEquals(Money(2000, "JPY"), unpaid.total)
    }

    @Test
    fun receiptExtraIsTaxOrNull() {
        assertEquals(Money(386, "JPY"), receiptExtra(Money(4246, "JPY"), Money(3860, "JPY")))
        assertNull(receiptExtra(Money(3860, "JPY"), Money(3860, "JPY")))
        assertNull(receiptExtra(Money(10, "USD"), Money(3860, "JPY")))
        assertNull(receiptExtra(null, Money(3860, "JPY")))
    }

    private fun highlight(id: String, days: Int, remind: Int = 7, year: Int = 2026) = MilestoneHighlight(
        UpcomingMilestone(
            Milestone(id, "恋爱纪念日", MilestoneType.ANNIVERSARY, LocalDate(2024, 11, 27), Recurrence.YEARLY, remind),
            nextDate = LocalDate(year, 11, 27),
            daysUntil = days,
            yearsSince = 2,
        ),
        Glyph("爱", GlyphTone.TERTIARY),
        previous = null,
    )

    @Test
    fun milestoneRemindersAreDueOncePerYearWithinWindow() {
        val all = listOf(highlight("love", 7), highlight("bd", 8), highlight("today", 0), highlight("off", 3, remind = 0))
        assertEquals(listOf("love", "today"), dueMilestoneReminders(all, emptySet()).map { it.upcoming.milestone.id })
        assertEquals(listOf("today"), dueMilestoneReminders(all, setOf("love:2026")).map { it.upcoming.milestone.id })
        // 明年的同一个纪念日是新的键。
        assertEquals("love:2027", milestoneReminderKey(highlight("love", 3, year = 2027)))
    }
}
