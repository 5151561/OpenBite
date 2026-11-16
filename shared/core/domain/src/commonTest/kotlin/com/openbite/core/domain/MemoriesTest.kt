package com.openbite.core.domain

import com.openbite.core.model.DiningLog
import com.openbite.core.model.DiningLogItem
import com.openbite.core.model.DiningStatus
import com.openbite.core.model.GlyphTone
import com.openbite.core.model.LogSource
import com.openbite.core.model.Milestone
import com.openbite.core.model.MilestoneType
import com.openbite.core.model.Money
import com.openbite.core.model.Person
import com.openbite.core.model.Place
import com.openbite.core.model.Recurrence
import com.openbite.core.model.UpcomingMilestone
import com.openbite.core.model.formatRating
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

class MemoriesTest {
    private val minato = Place("p-minato", "割烹 みなと", null, null, null)
    private val tori = Place("p-tori", "炭火焼鳥 とり松", null, null, null)
    private val today = LocalDate.parse("2026-11-17")

    @OptIn(ExperimentalTime::class)
    private fun log(
        id: String,
        place: Place,
        at: String,
        ratings: List<Int?> = listOf(5),
        status: DiningStatus = DiningStatus.DONE,
        milestoneId: String? = null,
        prices: List<Long?> = ratings.map { 1000L },
    ): DiningLog {
        val local = LocalDateTime.parse(at)
        return DiningLog(
            id = id,
            place = place,
            visitedAt = local.toInstant(TimeZone.of("Asia/Tokyo")).toEpochMilliseconds(),
            timezone = "Asia/Tokyo",
            localDate = local.date,
            status = status,
            milestoneId = milestoneId,
            note = null,
            source = LogSource.USER,
            items = ratings.mapIndexed { i, r ->
                DiningLogItem("$id-$i", null, "dish$i", prices[i]?.let { Money(it, "JPY") }, 1, r, null)
            },
            companions = listOf(Person("wife", "老婆")),
        )
    }

    @Test
    fun glyphUsesBusinessKeywordThenFirstChar() {
        assertEquals("割", placeGlyph("割烹 みなと").char)
        assertEquals("鳥", placeGlyph("炭火焼鳥 とり松").char)
        assertEquals("茶", placeGlyph("喫茶 こもれび").char)
        assertEquals("麺", placeGlyph("麺屋 ひなた").char)
        assertEquals(GlyphTone.NEUTRAL, placeGlyph("洋食 つばめ").tone)
        assertEquals("天", placeGlyph("天ぷら 八坂").char)
    }

    @Test
    fun ratingIsAverageWithOneDecimal() {
        val log = log("a", minato, "2026-10-03T19:00", ratings = listOf(4, 5, null))
        assertEquals("4.5", formatRating(averageRating(log)!!))
        assertEquals("5.0", formatRating(5.0))
        assertEquals("4.8", formatRating(4.75))
        assertNull(averageRating(log("b", minato, "2026-10-03T19:00", ratings = listOf(null))))
    }

    @Test
    fun totalSumsPricesAndNeedsEveryPrice() {
        assertEquals(Money(3000, "JPY"), totalOf(log("a", minato, "2026-10-03T19:00", ratings = listOf(5, 5, 5))))
        assertNull(totalOf(log("b", minato, "2026-10-03T19:00", ratings = listOf(5, 5), prices = listOf(1000, null))))
    }

    @Test
    fun summaryCountsVisitsPerPlaceInTimeOrder() {
        val logs = listOf(
            log("tori", tori, "2026-11-17T19:42", status = DiningStatus.DRAFT, ratings = listOf(null)),
            log("second", minato, "2026-10-03T19:00"),
            log("first", minato, "2025-11-27T19:30"),
        )
        val summaries = summarize(logs, today)
        assertEquals(listOf("tori", "second", "first"), summaries.map { it.logId })
        assertEquals(listOf(1, 2, 1), summaries.map { it.visitNumber })
        assertTrue(summaries[0].isToday && summaries[0].isDraft)
        assertEquals(LocalTime(19, 42), summaries[0].localTime)
        assertEquals(listOf("老婆"), summaries[1].companions)
    }

    @Test
    fun previousCelebrationIsLatestDoneLogBeforeNextDate() {
        val logs = summarize(
            listOf(
                log("draft", minato, "2026-11-27T19:00", status = DiningStatus.DRAFT, milestoneId = "love"),
                log("y2025", minato, "2025-11-27T19:30", milestoneId = "love"),
                log("y2024", tori, "2024-11-27T19:30", milestoneId = "love"),
                log("other", minato, "2026-10-03T19:00"),
            ),
            today,
        )
        val previous = previousCelebration("love", LocalDate.parse("2026-11-27"), logs)
        assertEquals("y2025", previous?.logId)
        assertNull(previousCelebration("birthday", LocalDate.parse("2027-03-08"), logs))
    }

    @Test
    fun highlightPairsMilestoneWithPreviousMeal() {
        val love = Milestone("love", "恋爱纪念日", MilestoneType.ANNIVERSARY, LocalDate.parse("2024-11-27"), Recurrence.YEARLY)
        val birthday = Milestone("bd", "老婆生日", MilestoneType.BIRTHDAY, LocalDate.parse("1996-03-08"), Recurrence.YEARLY)
        val upcoming = upcomingMilestones(listOf(birthday, love), today, daysAhead = 400)
        val result = highlight(upcoming, summarize(listOf(log("y2025", minato, "2025-11-27T19:30", milestoneId = "love")), today))
        assertEquals(listOf("love", "bd"), result.map { it.upcoming.milestone.id })
        assertEquals(10, result[0].upcoming.daysUntil)
        assertEquals(2, result[0].upcoming.yearsSince)
        assertEquals("y2025", result[0].previous?.logId)
        assertEquals("爱", result[0].glyph.char)
        assertEquals(111, result[1].upcoming.daysUntil)
        assertEquals("生", result[1].glyph.char)
        assertNull(result[1].previous)
    }

    @Test
    fun oneOffMilestoneNextYearIsCounted() {
        val reunion = Milestone("r", "大学室友聚会", MilestoneType.CELEBRATION, LocalDate.parse("2027-06-20"), Recurrence.NONE)
        val upcoming: List<UpcomingMilestone> = upcomingMilestones(listOf(reunion), today, daysAhead = 400)
        assertEquals(215, upcoming.single().daysUntil)
        assertEquals("聚", milestoneGlyph(reunion).char)
    }
}
