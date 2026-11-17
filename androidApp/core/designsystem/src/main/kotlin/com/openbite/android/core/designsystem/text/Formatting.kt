package com.openbite.android.core.designsystem.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalResources
import com.openbite.android.core.designsystem.R
import com.openbite.core.model.MemorySummary
import com.openbite.core.model.Milestone
import com.openbite.core.model.MilestoneHighlight
import com.openbite.core.model.MilestoneType
import com.openbite.core.model.Recurrence
import com.openbite.core.model.AiProvider
import com.openbite.core.model.ModelUse
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** 月份名：中文“11月”，英文“Nov”。[month] 从 1 开始。 */
@Composable
@ReadOnlyComposable
fun monthName(month: Int): String = stringArrayResource(R.array.ds_month_names)[month - 1]

/** “11月27日”（英文“Nov 27”）；不是今年时写“2025年11月27日”（“Nov 27, 2025”）。 */
@Composable
@ReadOnlyComposable
fun monthDay(date: LocalDate, currentYear: Int? = null): String {
    val month = monthName(date.month.ordinal + 1)
    return if (currentYear != null && date.year != currentYear) {
        stringResource(R.string.ds_year_month_day, date.year, month, date.day)
    } else {
        stringResource(R.string.ds_month_day, month, date.day)
    }
}

/** 总是带年份：“2026年10月3日”。 */
@Composable
@ReadOnlyComposable
fun fullDate(date: LocalDate): String =
    stringResource(R.string.ds_year_month_day, date.year, monthName(date.month.ordinal + 1), date.day)

/** “11月27日 周五”。 */
@Composable
@ReadOnlyComposable
fun monthDayWeekday(date: LocalDate): String =
    stringResource(R.string.ds_date_weekday, monthDay(date), stringArrayResource(R.array.ds_weekdays)[date.dayOfWeek.ordinal])

/** 记忆列表的月份标题：“11月”，不是今年时写“2025年11月”。 */
@Composable
@ReadOnlyComposable
fun monthHeader(year: Int, month: Int, currentYear: Int): String =
    if (year == currentYear) stringResource(R.string.ds_month, monthName(month)) else stringResource(R.string.ds_year_month, year, monthName(month))

fun LocalTime.hhmm(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

/** “N 周年”的 N：中文界面 2 -> 两、12 -> 十二；英文界面 2 -> 2nd。 */
@Composable
@ReadOnlyComposable
fun chineseCount(n: Int): String = PlainText.chineseCount(LocalResources.current, n)

/** “恋爱纪念日”第 2 年显示成“恋爱两周年”；其余纪念日原样显示标题。 */
@Composable
@ReadOnlyComposable
fun milestoneTitle(highlight: MilestoneHighlight): String =
    milestoneTitle(highlight.upcoming.milestone, highlight.upcoming.yearsSince)

/** 某一年的纪念日标题：“恋爱纪念日”第 1 年 -> “恋爱一周年”。 */
@Composable
@ReadOnlyComposable
fun milestoneTitle(milestone: Milestone, years: Int): String {
    val suffix = stringResource(R.string.ds_anniversary_suffix)
    return if (milestone.type == MilestoneType.ANNIVERSARY && years > 0 && milestone.title.endsWith(suffix)) {
        stringResource(R.string.ds_anniversary_title, milestone.title.removeSuffix(suffix), chineseCount(years))
    } else {
        milestone.title
    }
}

@Composable
@ReadOnlyComposable
fun recurrenceLabel(recurrence: Recurrence): String = stringResource(
    if (recurrence == Recurrence.YEARLY) R.string.ds_recurrence_yearly else R.string.ds_recurrence_none,
)

/** “去年在 割烹 みなと”；更早的写“2024年在 …”。没有上一次时返回 null。 */
@Composable
@ReadOnlyComposable
fun previousCelebrationLabel(highlight: MilestoneHighlight): String? {
    val previous = highlight.previous ?: return null
    return if (previous.localDate.year == highlight.upcoming.nextDate.year - 1) {
        stringResource(R.string.ds_previous_last_year, previous.placeName)
    } else {
        stringResource(R.string.ds_previous_in_year, previous.localDate.year, previous.placeName)
    }
}

@Composable
@ReadOnlyComposable
fun quoted(text: String): String = stringResource(R.string.ds_quote, text)

/**
 * 记忆行的副标题，按设计稿：
 * - 草稿：“今晚 19:42 · 5 道菜 · 待补记忆”
 * - 有同行人：“10月3日 · 第 2 次来 · 和老婆”（第 1 次来不写次数）
 * - 一个人：“11月15日 · 果冻苏打、布丁”
 */
@Composable
@ReadOnlyComposable
fun memorySupportingText(memory: MemorySummary, currentYear: Int): String {
    val separator = stringResource(R.string.ds_separator)
    val listSeparator = stringResource(R.string.ds_list_separator)
    val time = memory.localTime.hhmm()
    val date = when {
        memory.isToday && memory.localTime.hour >= 17 -> stringResource(R.string.ds_tonight_time, time)
        memory.isToday -> stringResource(R.string.ds_today_time, time)
        else -> monthDay(memory.localDate, currentYear)
    }
    val details = buildList {
        if (memory.isDraft) {
            add(pluralStringResource(R.plurals.ds_memory_dish_count, memory.dishCount, memory.dishCount))
            add(stringResource(R.string.ds_memory_needs_detail))
        } else {
            if (memory.companions.isNotEmpty()) {
                if (memory.visitNumber > 1) add(stringResource(R.string.ds_memory_visit_number, memory.visitNumber))
                add(stringResource(R.string.ds_memory_with, memory.companions.joinToString(listSeparator)))
            } else if (memory.dishNames.isNotEmpty()) {
                add(memory.dishNames.take(2).joinToString(listSeparator))
            }
        }
    }
    return (listOf(date) + details).joinToString(separator)
}

/** 这次识别用的模型在界面上的名字：演示模式，或服务商给的模型 id。 */
@Composable
@ReadOnlyComposable
fun modelUseLabel(use: ModelUse): String = when (use) {
    ModelUse.Demo -> stringResource(R.string.ds_model_demo)
    is ModelUse.Real -> use.ref.modelId
}

/** 服务商的名字。 */
@Composable
@ReadOnlyComposable
fun providerLabel(provider: AiProvider): String = stringResource(
    when (provider) {
        AiProvider.ANTHROPIC -> R.string.ds_provider_anthropic
        AiProvider.OPENAI -> R.string.ds_provider_openai
        AiProvider.GEMINI -> R.string.ds_provider_gemini
        AiProvider.DEEPSEEK -> R.string.ds_provider_deepseek
        AiProvider.CUSTOM -> R.string.ds_provider_custom
    },
)
