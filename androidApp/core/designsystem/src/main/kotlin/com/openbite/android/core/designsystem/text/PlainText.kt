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
    fun chineseCount(res: Resources, n: Int): String {
        if (n == 2) return res.getString(R.string.ds_two_standalone)
        if (n !in 1..99) return n.toString()
        val digits = res.getStringArray(R.array.ds_chinese_digits)
        val ten = res.getString(R.string.ds_ten)
        return when {
            n < 10 -> digits[n]
            else -> (if (n >= 20) digits[n / 10] else "") + ten + (if (n % 10 != 0) digits[n % 10] else "")
        }
    }

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
        res.getString(R.string.ds_month_day, date.month.ordinal + 1, date.day),
        res.getStringArray(R.array.ds_weekdays)[date.dayOfWeek.ordinal],
    )
}
