package com.openbite.android.core.designsystem.text

import android.content.res.Resources
import com.openbite.android.core.designsystem.R
import com.openbite.core.model.Milestone
import com.openbite.core.model.MilestoneType
import kotlinx.datetime.LocalDate

/**
 * 和 Formatting.kt 里的 Composable 一样的文案，但只要 [Resources]。
 * 给通知（WorkManager 里没有 Composition）用。
 */
object PlainText {
    /** “N 周年”的 N：按 ds_count_style，中文数字（2 -> 两，12 -> 十二，21 -> 二十一）或英文序数（2nd、11th、23rd）。 */
    fun chineseCount(res: Resources, n: Int): String {
        if (res.getString(R.string.ds_count_style) == "ordinal") return ordinal(n)
        if (n == 2) return res.getString(R.string.ds_two_standalone)
        if (n !in 1..99) return n.toString()
        val digits = res.getStringArray(R.array.ds_chinese_digits)
        val ten = res.getString(R.string.ds_ten)
        return when {
            n < 10 -> digits[n]
            else -> (if (n >= 20) digits[n / 10] else "") + ten + (if (n % 10 != 0) digits[n % 10] else "")
        }
    }

    /** 英文序数：1st、2nd、3rd、4th、11th、12th、13th、21st。 */
    fun ordinal(n: Int): String {
        val suffix = when {
            n % 100 in 11..13 -> "th"
            n % 10 == 1 -> "st"
            n % 10 == 2 -> "nd"
            n % 10 == 3 -> "rd"
            else -> "th"
        }
        return "$n$suffix"
    }

    /** 月份名：中文“11月”，英文“Nov”。[month] 从 1 开始。 */
    fun monthName(res: Resources, month: Int): String = res.getStringArray(R.array.ds_month_names)[month - 1]

    /** 总是带年份：“2026年10月3日”（英文“Oct 3, 2026”）。 */
    fun fullDate(res: Resources, date: LocalDate): String =
        res.getString(R.string.ds_year_month_day, date.year, monthName(res, date.month.ordinal + 1), date.day)

    fun milestoneTitle(res: Resources, milestone: Milestone, years: Int): String {
        val suffix = res.getString(R.string.ds_anniversary_suffix)
        return if (milestone.type == MilestoneType.ANNIVERSARY && years > 0 && milestone.title.endsWith(suffix)) {
            res.getString(R.string.ds_anniversary_title, milestone.title.removeSuffix(suffix), chineseCount(res, years))
        } else {
            milestone.title
        }
    }

    fun monthDayWeekday(res: Resources, date: LocalDate): String = res.getString(
        R.string.ds_date_weekday,
        res.getString(R.string.ds_month_day, monthName(res, date.month.ordinal + 1), date.day),
        res.getStringArray(R.array.ds_weekdays)[date.dayOfWeek.ordinal],
    )
}
