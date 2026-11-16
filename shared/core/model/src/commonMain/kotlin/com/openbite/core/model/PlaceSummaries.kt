package com.openbite.core.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/** 店铺列表上店名旁边的小标签。 */
sealed interface PlaceBadge {
    /** 今天来过。 */
    data object Tonight : PlaceBadge

    /** 在这里过过纪念日。 */
    data object Milestone : PlaceBadge

    /** 用户打的标签，如“深夜食堂”。 */
    data class Tag(val tag: String) : PlaceBadge
}

/** 店铺列表的一行。 */
data class PlaceSummary(
    val place: Place,
    val glyph: Glyph,
    val badge: PlaceBadge?,
    val dishCount: Int,
    val photoCount: Int,
    val visitCount: Int,
    val lastVisit: LocalDate?,
)

/** 一道菜在这家店被点过的情况。 */
data class DishStats(
    val dishId: String?,
    val nameOriginal: String,
    val nameTranslated: String,
    /** 一共点过几份（按份数累加，草稿也算）。 */
    val timesOrdered: Int,
    /** 打过分的平均值，没打过分为 null。 */
    val averageRating: Double?,
    /** 饮料按“杯”计数。 */
    val isDrink: Boolean,
    val lastOrdered: LocalDate,
)

/** 店铺详情里的一次到店，和它对应的纪念日（如“恋爱一周年”）。 */
data class PlaceVisit(
    val memory: MemorySummary,
    val milestone: Milestone?,
    /** 那天是第几周年（首次那年为 0）。 */
    val milestoneYears: Int,
)

data class PlaceDetail(
    val place: Place,
    val glyph: Glyph,
    /** 只到区一级：“京都市中京区”。 */
    val area: String?,
    val visitCount: Int,
    val visits: List<PlaceVisit>,
    val orderedDishes: List<DishStats>,
)

/** 电子菜单上的一道菜。[stats] 为 null 表示还没点过。 */
data class MenuEntry(
    val dish: Dish,
    val glyph: Glyph,
    val stats: DishStats?,
)

/** 一张菜单原照片，拍摄时间已换成店铺当地时间。 */
data class MenuPhoto(
    val scan: MenuScan,
    val takenAtLocal: LocalDateTime,
)

data class PlaceMenu(
    val place: Place,
    val entries: List<MenuEntry>,
    val photos: List<MenuPhoto>,
) {
    /** 最近一次拍菜单的时间，没有照片时为 null。 */
    val lastScannedAt: LocalDateTime? get() = photos.maxOfOrNull { it.takenAtLocal }
}
