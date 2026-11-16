package com.openbite.core.domain

import com.openbite.core.model.DiningLog
import com.openbite.core.model.DiningStatus
import com.openbite.core.model.Glyph
import com.openbite.core.model.GlyphTone
import com.openbite.core.model.MemorySummary
import com.openbite.core.model.Milestone
import com.openbite.core.model.MilestoneHighlight
import com.openbite.core.model.MilestoneType
import com.openbite.core.model.Money
import com.openbite.core.model.UpcomingMilestone
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val placeGlyphRules: List<Pair<List<String>, Glyph>> = listOf(
    listOf("焼鳥", "やきとり") to Glyph("鳥", GlyphTone.TERTIARY),
    listOf("割烹", "懐石", "料亭") to Glyph("割", GlyphTone.PRIMARY),
    listOf("喫茶", "珈琲", "カフェ") to Glyph("茶", GlyphTone.PRIMARY_FIXED),
    listOf("麺", "ラーメン", "らーめん") to Glyph("麺", GlyphTone.SECONDARY),
    listOf("洋食") to Glyph("洋", GlyphTone.NEUTRAL),
    listOf("寿司", "鮨") to Glyph("鮨", GlyphTone.PRIMARY),
    listOf("居酒屋") to Glyph("酒", GlyphTone.TERTIARY),
    listOf("蕎麦", "そば") to Glyph("蕎", GlyphTone.SECONDARY),
)

/** 店铺头像：按店名里的业态关键词取一个字，认不出就取店名第一个字。 */
fun placeGlyph(name: String): Glyph {
    placeGlyphRules.firstOrNull { (keys, _) -> keys.any { it in name } }?.let { return it.second }
    val first = name.trim().firstOrNull()?.toString() ?: "?"
    val tones = GlyphTone.entries
    return Glyph(first, tones[name.hashCode().mod(tones.size)])
}

/** 纪念日头像：生日“生”、聚会“聚”、恋爱/结婚纪念“爱”，其余取标题第一个字。 */
fun milestoneGlyph(milestone: Milestone): Glyph = when {
    milestone.type == MilestoneType.BIRTHDAY || "生日" in milestone.title -> Glyph("生", GlyphTone.SECONDARY)
    "聚" in milestone.title -> Glyph("聚", GlyphTone.PRIMARY_FIXED)
    milestone.type == MilestoneType.ANNIVERSARY -> Glyph("爱", GlyphTone.TERTIARY)
    else -> Glyph(milestone.title.firstOrNull()?.toString() ?: "?", GlyphTone.NEUTRAL)
}

/** 一顿饭的评分：有总评分用总评分，否则用菜品评分的平均值。 */
fun mealRating(log: DiningLog): Double? = log.rating?.toDouble() ?: averageRating(log)

/** 菜品评分的平均值。 */
fun averageRating(log: DiningLog): Double? {
    val ratings = log.items.mapNotNull { it.rating }
    return if (ratings.isEmpty()) null else ratings.average()
}

/** 一顿饭的合计；币种不一致或缺价格时返回 null。 */
fun totalOf(log: DiningLog): Money? {
    val prices = log.items.map { item -> item.price?.let { it to item.qty } ?: return null }
    if (prices.isEmpty()) return null
    val currency = prices.first().first.currency
    if (prices.any { it.first.currency != currency }) return null
    return Money(prices.sumOf { (price, qty) -> price.amountMinor * qty }, currency)
}

/**
 * 把记录转成列表要显示的摘要，顺序与输入一致。
 * 第几次来按同一家店的到店时间算，草稿也算一次。
 */
@OptIn(ExperimentalTime::class)
fun summarize(logs: List<DiningLog>, today: LocalDate): List<MemorySummary> {
    val visitNumbers = logs.groupBy { it.place.id }
        .flatMap { (_, visits) -> visits.sortedBy { it.visitedAt }.mapIndexed { i, log -> log.id to i + 1 } }
        .toMap()
    return logs.map { log ->
        val local = Instant.fromEpochMilliseconds(log.visitedAt).toLocalDateTime(TimeZone.of(log.timezone))
        MemorySummary(
            logId = log.id,
            placeId = log.place.id,
            placeName = log.place.name,
            glyph = placeGlyph(log.place.name),
            localDate = log.localDate,
            localTime = local.time,
            isToday = log.localDate == today,
            isDraft = log.status == DiningStatus.DRAFT,
            dishCount = log.items.sumOf { it.qty },
            dishNames = log.items.map { it.displayName },
            visitNumber = visitNumbers.getValue(log.id),
            companions = log.companions.map { it.displayName },
            rating = mealRating(log),
            total = log.paidTotal ?: totalOf(log),
            milestoneId = log.milestoneId,
            note = log.note,
            byAssistant = log.source == com.openbite.core.model.LogSource.ASSISTANT,
        )
    }
}

/** 上一次为这个纪念日吃的那顿饭：标了这个纪念日、已完成、日期早于 [before] 的最近一条。 */
fun previousCelebration(
    milestoneId: String,
    before: LocalDate,
    memories: List<MemorySummary>,
): MemorySummary? = memories
    .filter { it.milestoneId == milestoneId && !it.isDraft && it.localDate < before }
    .maxByOrNull { it.localDate }

/** 给每个即将到来的纪念日配上上一次的记录。 */
fun highlight(upcoming: List<UpcomingMilestone>, memories: List<MemorySummary>): List<MilestoneHighlight> =
    upcoming.map {
        MilestoneHighlight(
            upcoming = it,
            glyph = milestoneGlyph(it.milestone),
            previous = previousCelebration(it.milestone.id, it.nextDate, memories),
        )
    }
