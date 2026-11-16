package com.openbite.core.domain

import com.openbite.core.data.PlaceCounts
import com.openbite.core.model.DiningLog
import com.openbite.core.model.Dish
import com.openbite.core.model.DishStats
import com.openbite.core.model.Glyph
import com.openbite.core.model.GlyphTone
import com.openbite.core.model.MenuEntry
import com.openbite.core.model.MenuPhoto
import com.openbite.core.model.MenuScan
import com.openbite.core.model.Milestone
import com.openbite.core.model.Place
import com.openbite.core.model.PlaceBadge
import com.openbite.core.model.PlaceDetail
import com.openbite.core.model.PlaceMenu
import com.openbite.core.model.PlaceSummary
import com.openbite.core.model.PlaceVisit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** 店铺列表：最近去过的在前，没去过的按店名排在最后。 */
fun placeSummaries(
    places: List<Place>,
    logs: List<DiningLog>,
    counts: Map<String, PlaceCounts>,
    today: LocalDate,
): List<PlaceSummary> {
    val logsByPlace = logs.groupBy { it.place.id }
    return places.map { place ->
        val visits = logsByPlace[place.id].orEmpty()
        val lastVisit = visits.maxOfOrNull { it.localDate }
        PlaceSummary(
            place = place,
            glyph = placeGlyph(place.name),
            badge = placeBadge(place, visits, lastVisit, today),
            dishCount = counts[place.id]?.dishes ?: 0,
            photoCount = counts[place.id]?.photos ?: 0,
            visitCount = visits.size,
            lastVisit = lastVisit,
        )
    }.sortedWith(compareByDescending<PlaceSummary> { it.lastVisit }.thenBy { it.place.name })
}

/** 标签优先级：今天来过 > 过过纪念日 > 第一个用户标签。 */
private fun placeBadge(place: Place, visits: List<DiningLog>, lastVisit: LocalDate?, today: LocalDate): PlaceBadge? = when {
    lastVisit == today -> PlaceBadge.Tonight
    visits.any { it.milestoneId != null } -> PlaceBadge.Milestone
    else -> place.tags.firstOrNull()?.let { PlaceBadge.Tag(it) }
}

/** 地址只保留到区（没有区就到市）：“京都市中京区木屋町通…” -> “京都市中京区”。 */
fun shortArea(address: String?): String? {
    val text = address?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val ward = text.indexOf('区')
    if (ward >= 0) return text.substring(0, ward + 1)
    val city = text.indexOf('市')
    return if (city >= 0) text.substring(0, city + 1) else text
}

private val drinkKeywords = listOf("飲", "ドリンク", "酒", "ビール", "drink", "Drink")

fun isDrinkCategory(category: String): Boolean = drinkKeywords.any { it in category }

/**
 * 这家店每道菜被点过的情况。按 dishId 归并；没有 dishId 的按菜名归并。
 * 排序：点得多的在前，再按评分、最近点过。
 */
fun dishStats(placeLogs: List<DiningLog>, menu: List<Dish>): List<DishStats> {
    val dishesById = menu.associateBy { it.id }
    data class Order(val key: String, val dish: Dish?, val name: String, val translated: String?, val qty: Int, val rating: Int?, val date: LocalDate)
    val orders = placeLogs.flatMap { log ->
        log.items.map { item ->
            val dish = item.dishId?.let { dishesById[it] }
            Order(item.dishId ?: "name:${item.name}", dish, item.name, item.nameTranslated, item.qty, item.rating, log.localDate)
        }
    }
    return orders.groupBy { it.key }.map { (_, group) ->
        val dish = group.firstNotNullOfOrNull { it.dish }
        val ratings = group.mapNotNull { it.rating }
        DishStats(
            dishId = dish?.id,
            nameOriginal = dish?.nameOriginal ?: group.first().name,
            nameTranslated = dish?.nameTranslated ?: group.first().let { it.translated ?: it.name },
            timesOrdered = group.sumOf { it.qty },
            averageRating = if (ratings.isEmpty()) null else ratings.average(),
            isDrink = dish?.let { isDrinkCategory(it.category) } ?: false,
            lastOrdered = group.maxOf { it.date },
        )
    }.sortedWith(
        compareByDescending<DishStats> { it.timesOrdered }
            .thenByDescending { it.averageRating ?: -1.0 }
            .thenByDescending { it.lastOrdered },
    )
}

/** 店铺详情。[allLogs] 是全部记录，这里只挑这家店的。 */
fun placeDetail(
    place: Place,
    allLogs: List<DiningLog>,
    menu: List<Dish>,
    milestones: List<Milestone>,
    today: LocalDate,
): PlaceDetail {
    val placeLogs = allLogs.filter { it.place.id == place.id }
    val milestonesById = milestones.associateBy { it.id }
    val visits = summarize(placeLogs, today)
        .sortedByDescending { it.localDate }
        .map { memory ->
            val milestone = memory.milestoneId?.let { milestonesById[it] }
            PlaceVisit(
                memory = memory,
                milestone = milestone,
                milestoneYears = milestone?.let { memory.localDate.year - it.date.year } ?: 0,
            )
        }
    return PlaceDetail(
        place = place,
        glyph = placeGlyph(place.name),
        area = shortArea(place.address),
        visitCount = placeLogs.size,
        visits = visits,
        orderedDishes = dishStats(placeLogs, menu),
    )
}

private val dishTones = listOf(
    GlyphTone.TERTIARY,
    GlyphTone.PRIMARY_FIXED,
    GlyphTone.SECONDARY,
    GlyphTone.PRIMARY,
    GlyphTone.NEUTRAL,
)

private fun Char.isCjkIdeograph(): Boolean = this in '\u4E00'..'\u9FFF' || this in '\u3400'..'\u4DBF'

/** 菜品头像：原名里第一个汉字（“鯖の塩焼き” -> “鯖”），没有汉字就取第一个字。颜色按位置轮换。 */
fun dishGlyph(nameOriginal: String, index: Int): Glyph {
    val char = nameOriginal.firstOrNull { it.isCjkIdeograph() } ?: nameOriginal.trim().firstOrNull() ?: '?'
    return Glyph(char.toString(), dishTones[index.mod(dishTones.size)])
}

/** 照片时间按哪个时区显示：这家店最近一条记录的时区，没有记录就用手机当前时区。 */
fun placeTimeZone(placeLogs: List<DiningLog>): TimeZone =
    placeLogs.maxByOrNull { it.visitedAt }?.timezone?.let { runCatching { TimeZone.of(it) }.getOrNull() }
        ?: TimeZone.currentSystemDefault()

@OptIn(ExperimentalTime::class)
fun placeMenu(
    place: Place,
    allLogs: List<DiningLog>,
    menu: List<Dish>,
    scans: List<MenuScan>,
): PlaceMenu {
    val placeLogs = allLogs.filter { it.place.id == place.id }
    val stats = dishStats(placeLogs, menu).filter { it.dishId != null }.associateBy { it.dishId }
    val zone = placeTimeZone(placeLogs)
    return PlaceMenu(
        place = place,
        entries = menu.mapIndexed { index, dish -> MenuEntry(dish, dishGlyph(dish.nameOriginal, index), stats[dish.id]) },
        photos = scans.map { MenuPhoto(it, Instant.fromEpochMilliseconds(it.takenAt).toLocalDateTime(zone)) },
    )
}
