package com.openbite.core.domain

import com.openbite.core.model.DiningLog
import com.openbite.core.model.Money
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

/** 分享卡片上的城市：“京都市中京区” -> “京都”，“東京都渋谷区” -> “東京”。认不出返回 null。 */
fun cityOf(address: String?): String? {
    val text = address?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    for (mark in listOf('市', '都', '府', '県')) {
        val i = text.indexOf(mark)
        if (i > 0) return text.substring(0, i)
    }
    return null
}

/** 分享卡片（设计稿 16）上的内容。[showPlace] / [showAmount] 是分享时的开关，不存。 */
data class MealCard(
    /** 不显示店名和城市时为 null。 */
    val placeName: String?,
    val city: String?,
    val date: LocalDate,
    /** 0–5 颗星；没打分为 null。 */
    val stars: Int?,
    val note: String?,
    /** 最多 [MAX_CARD_DISHES] 道（译名优先）。 */
    val dishNames: List<String>,
    /** 没列出来的还有几道。 */
    val moreDishes: Int,
    /** 不显示金额时为 null。 */
    val amount: Money?,
    /** 几个人（我 + 同行人）；不显示金额时为 null。 */
    val headcount: Int?,
)

const val MAX_CARD_DISHES = 4

fun mealCard(log: DiningLog, showPlace: Boolean, showAmount: Boolean): MealCard {
    val names = log.items.map { it.displayName }
    return MealCard(
        placeName = log.place.name.takeIf { showPlace },
        city = if (showPlace) cityOf(log.place.address) else null,
        date = log.localDate,
        stars = mealRating(log)?.roundToInt()?.coerceIn(0, 5),
        note = log.note,
        dishNames = names.take(MAX_CARD_DISHES),
        moreDishes = (names.size - MAX_CARD_DISHES).coerceAtLeast(0),
        amount = if (showAmount) log.paidTotal ?: totalOf(log) else null,
        headcount = if (showAmount) 1 + log.companions.size else null,
    )
}
