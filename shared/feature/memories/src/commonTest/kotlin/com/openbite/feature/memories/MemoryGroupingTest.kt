package com.openbite.feature.memories

import com.openbite.core.model.Glyph
import com.openbite.core.model.GlyphTone
import com.openbite.core.model.MemorySummary
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class MemoryGroupingTest {
    private fun memory(id: String, date: String, draft: Boolean = false, milestone: String? = null) = MemorySummary(
        logId = id,
        placeId = "p",
        placeName = "店",
        glyph = Glyph("店", GlyphTone.PRIMARY),
        localDate = LocalDate.parse(date),
        localTime = LocalTime(19, 0),
        isToday = false,
        isDraft = draft,
        dishCount = 1,
        dishNames = listOf("菜"),
        visitNumber = 1,
        companions = emptyList(),
        rating = null,
        total = null,
        milestoneId = milestone,
        note = null,
    )

    private val all = listOf(
        memory("tori", "2026-11-17", draft = true),
        memory("cafe", "2026-11-15"),
        memory("ramen", "2026-11-14"),
        memory("yoshoku", "2026-10-26"),
        memory("minato2", "2026-10-03"),
        memory("minato1", "2025-11-27", milestone = "love"),
    )

    @Test
    fun groupsByYearAndMonthNewestFirst() {
        val groups = groupByMonth(all)
        assertEquals(listOf(2026 to 11, 2026 to 10, 2025 to 11), groups.map { it.year to it.month })
        assertEquals(listOf("tori", "cafe", "ramen"), groups[0].items.map { it.logId })
        assertEquals(listOf("minato1"), groups[2].items.map { it.logId })
    }

    @Test
    fun sameMonthOfDifferentYearsStaysSeparate() {
        val groups = groupByMonth(listOf(memory("a", "2025-11-01"), memory("b", "2026-11-01")))
        assertEquals(listOf(2026, 2025), groups.map { it.year })
    }

    @Test
    fun groupingSortsUnorderedInput() {
        val groups = groupByMonth(all.reversed())
        assertEquals("tori", groups.first().items.first().logId)
    }

    @Test
    fun filters() {
        assertEquals(6, filterMemories(all, MemoryFilter.ALL).size)
        assertEquals(listOf("minato1"), filterMemories(all, MemoryFilter.MILESTONE).map { it.logId })
        assertEquals(listOf("tori"), filterMemories(all, MemoryFilter.DRAFT).map { it.logId })
    }

    @Test
    fun emptyInputGivesNoGroups() {
        assertEquals(emptyList(), groupByMonth(filterMemories(all, MemoryFilter.DRAFT).drop(1)))
    }
}
