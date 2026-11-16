package com.openbite.feature.memories

import com.openbite.core.model.MemorySummary

/** 记忆列表顶部的筛选：全部 / 纪念日 / 草稿。 */
enum class MemoryFilter { ALL, MILESTONE, DRAFT }

/** 同一个月的记忆。界面按 [year] 是否是今年决定写“11月”还是“2025年11月”。 */
data class MonthGroup(
    val year: Int,
    val month: Int,
    val items: List<MemorySummary>,
)

fun filterMemories(memories: List<MemorySummary>, filter: MemoryFilter): List<MemorySummary> = when (filter) {
    MemoryFilter.ALL -> memories
    MemoryFilter.MILESTONE -> memories.filter { it.milestoneId != null }
    MemoryFilter.DRAFT -> memories.filter { it.isDraft }
}

/** 按当地日期的年月分组，新的月份在前；组内保持原来的顺序（新的在前）。 */
fun groupByMonth(memories: List<MemorySummary>): List<MonthGroup> = memories
    .sortedByDescending { it.localDate }
    .groupBy { it.localDate.year to it.localDate.month.ordinal + 1 }
    .map { (key, items) -> MonthGroup(year = key.first, month = key.second, items = items) }
