package com.openbite.android.core.designsystem.preview

import com.openbite.core.model.Glyph
import com.openbite.core.model.GlyphTone
import com.openbite.core.model.MemorySummary
import com.openbite.core.model.Milestone
import com.openbite.core.model.MilestoneHighlight
import com.openbite.core.model.MilestoneType
import com.openbite.core.model.Money
import com.openbite.core.model.Recurrence
import com.openbite.core.model.UpcomingMilestone
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** @Preview 用的假数据，内容和种子数据一致（京都，今天 2026-11-17）。 */
object PreviewData {
    val today: LocalDate = LocalDate(2026, 11, 17)

    private fun memory(
        id: String,
        name: String,
        glyph: Glyph,
        date: LocalDate,
        dishes: List<String>,
        rating: Double? = null,
        draft: Boolean = false,
        visit: Int = 1,
        companions: List<String> = emptyList(),
        milestoneId: String? = null,
        note: String? = null,
    ) = MemorySummary(
        logId = id,
        placeId = id,
        placeName = name,
        glyph = glyph,
        localDate = date,
        localTime = LocalTime(19, 42),
        isToday = date == today,
        isDraft = draft,
        dishCount = dishes.size,
        dishNames = dishes,
        visitNumber = visit,
        companions = companions,
        rating = rating,
        total = null,
        milestoneId = milestoneId,
        note = note,
    )

    val memories: List<MemorySummary> = listOf(
        memory("tori", "炭火焼鳥 とり松", Glyph("鳥", GlyphTone.TERTIARY), today, List(5) { "串" }, draft = true),
        memory("cafe", "喫茶 こもれび", Glyph("茶", GlyphTone.PRIMARY_FIXED), LocalDate(2026, 11, 15), listOf("果冻苏打", "布丁"), 4.5),
        memory("ramen", "麺屋 ひなた", Glyph("麺", GlyphTone.SECONDARY), LocalDate(2026, 11, 14), listOf("黑酱油拉面"), 5.0),
        memory("yoshoku", "洋食 つばめ", Glyph("洋", GlyphTone.NEUTRAL), LocalDate(2026, 10, 26), listOf("蛋包饭", "炸牡蛎"), 4.0),
        memory("minato2", "割烹 みなと", Glyph("割", GlyphTone.PRIMARY), LocalDate(2026, 10, 3), listOf("海胆茶碗蒸"), 5.0, visit = 2, companions = listOf("老婆")),
        memory(
            "minato1", "割烹 みなと", Glyph("割", GlyphTone.PRIMARY), LocalDate(2025, 11, 27), List(6) { "菜" }, 5.0,
            companions = listOf("老婆"), milestoneId = "ms-love", note = "海胆茶碗蒸，她说明年还要来。",
        ),
    )

    val highlights: List<MilestoneHighlight> = listOf(
        MilestoneHighlight(
            upcoming = UpcomingMilestone(
                Milestone("ms-love", "恋爱纪念日", MilestoneType.ANNIVERSARY, LocalDate(2024, 11, 27), Recurrence.YEARLY),
                nextDate = LocalDate(2026, 11, 27),
                daysUntil = 10,
                yearsSince = 2,
            ),
            glyph = Glyph("爱", GlyphTone.TERTIARY),
            previous = memories.last().copy(total = Money(18_400, "JPY")),
        ),
        MilestoneHighlight(
            upcoming = UpcomingMilestone(
                Milestone("ms-bd", "老婆生日", MilestoneType.BIRTHDAY, LocalDate(1996, 3, 8), Recurrence.YEARLY),
                nextDate = LocalDate(2027, 3, 8),
                daysUntil = 111,
                yearsSince = 31,
            ),
            glyph = Glyph("生", GlyphTone.SECONDARY),
            previous = null,
        ),
        MilestoneHighlight(
            upcoming = UpcomingMilestone(
                Milestone("ms-reunion", "大学室友聚会", MilestoneType.CELEBRATION, LocalDate(2027, 6, 20), Recurrence.NONE),
                nextDate = LocalDate(2027, 6, 20),
                daysUntil = 215,
                yearsSince = 0,
            ),
            glyph = Glyph("聚", GlyphTone.PRIMARY_FIXED),
            previous = null,
        ),
    )
}
