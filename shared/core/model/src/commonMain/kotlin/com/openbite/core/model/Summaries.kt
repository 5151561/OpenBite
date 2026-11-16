package com.openbite.core.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.math.roundToInt

/** 头像色调，对应设计稿里五种容器色。具体颜色由各平台的主题决定。 */
enum class GlyphTone { PRIMARY, SECONDARY, TERTIARY, PRIMARY_FIXED, NEUTRAL }

/** 店铺或纪念日的单字头像，如“割”“鳥”“生”。 */
data class Glyph(val char: String, val tone: GlyphTone)

/** 列表里的一条用餐记忆，已经算好界面要显示的数据。文案由各平台拼。 */
data class MemorySummary(
    val logId: String,
    val placeId: String,
    val placeName: String,
    val glyph: Glyph,
    val localDate: LocalDate,
    val localTime: LocalTime,
    val isToday: Boolean,
    val isDraft: Boolean,
    /** 一共几份（按份数累加，两杯啤酒算 2）。 */
    val dishCount: Int,
    val dishNames: List<String>,
    /** 第几次来这家店（按时间顺序，从 1 开始）。 */
    val visitNumber: Int,
    val companions: List<String>,
    /** 菜品评分的平均值，没有评分时为 null。 */
    val rating: Double?,
    val total: Money?,
    val milestoneId: String?,
    val note: String?,
    /** 手机助手（AppFunctions）记的，列表里标“助手记录”。 */
    val byAssistant: Boolean = false,
)

/** 一个即将到来的纪念日，以及上一次为它吃的那顿饭。 */
data class MilestoneHighlight(
    val upcoming: UpcomingMilestone,
    val glyph: Glyph,
    val previous: MemorySummary?,
)

/** 评分保留一位小数：4.75 -> "4.8"，5 -> "5.0"。两端界面共用，保证显示一致。 */
fun formatRating(rating: Double): String {
    val tenths = (rating * 10).roundToInt()
    return "${tenths / 10}.${tenths % 10}"
}
