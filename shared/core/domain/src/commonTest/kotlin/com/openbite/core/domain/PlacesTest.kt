package com.openbite.core.domain

import com.openbite.core.data.PlaceCounts
import com.openbite.core.model.DiningLog
import com.openbite.core.model.DiningLogItem
import com.openbite.core.model.DiningStatus
import com.openbite.core.model.Dish
import com.openbite.core.model.LogSource
import com.openbite.core.model.MenuScan
import com.openbite.core.model.Milestone
import com.openbite.core.model.MilestoneType
import com.openbite.core.model.Money
import com.openbite.core.model.Place
import com.openbite.core.model.PlaceBadge
import com.openbite.core.model.Recurrence
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class PlacesTest {
    private val today = LocalDate.parse("2026-11-17")
    private val minato = Place("minato", "割烹 みなと", null, null, "京都市中京区木屋町通三条下ル", listOf("适合约会", "纪念日"))
    private val tori = Place("tori", "炭火焼鳥 とり松", null, null, "京都市中京区先斗町通", listOf("深夜食堂"))
    private val hinata = Place("hinata", "麺屋 ひなた", null, null, null, listOf("深夜食堂"))
    private val cafe = Place("cafe", "喫茶 こもれび", null, null, null)
    private val newPlace = Place("new", "蕎麦 いろは", null, null, null)

    private fun dish(id: String, place: Place, original: String, translated: String, category: String = "料理") =
        Dish(id, place.id, category, original, translated, Money(1000, "JPY"))

    private val uni = dish("uni", minato, "うに茶碗蒸し", "海胆茶碗蒸")
    private val saikyo = dish("saikyo", minato, "鰆の西京焼き", "西京烧鲅鱼")
    private val tempura = dish("tempura", minato, "季節の天ぷら", "时令天妇罗")
    private val beer = dish("beer", tori, "生ビール", "生啤", category = "飲み物")
    private val yakitori = dish("yakitori", tori, "焼き鳥盛り合わせ", "烤鸡肉串拼盘")

    private fun log(
        id: String,
        place: Place,
        at: String,
        items: List<Pair<Dish, Int?>>,
        qty: Int = 1,
        status: DiningStatus = DiningStatus.DONE,
        milestoneId: String? = null,
    ): DiningLog {
        val local = LocalDateTime.parse(at)
        return DiningLog(
            id = id,
            place = place,
            visitedAt = local.toInstant(TimeZone.of("Asia/Tokyo")).toEpochMilliseconds(),
            timezone = "Asia/Tokyo",
            localDate = local.date,
            status = status,
            milestoneId = milestoneId,
            note = null,
            source = LogSource.USER,
            items = items.mapIndexed { i, (d, r) ->
                DiningLogItem("$id-$i", d.id, d.nameTranslated, d.price, if (d == beer) 2 else qty, r, null)
            },
        )
    }

    private val logs = listOf(
        log("tori1", tori, "2026-11-17T19:42", listOf(yakitori to null, beer to null), status = DiningStatus.DRAFT),
        log("cafe1", cafe, "2026-11-15T15:00", emptyList()),
        log("hinata1", hinata, "2026-11-14T22:40", emptyList()),
        log("minato2", minato, "2026-10-03T18:45", listOf(uni to 5, saikyo to 5, tempura to 4)),
        log("minato1", minato, "2025-11-27T19:30", listOf(uni to 5, saikyo to 4), milestoneId = "love"),
    )

    @Test
    fun listSortsByLastVisitAndPicksBadge() {
        val counts = mapOf("minato" to PlaceCounts(12, 2), "tori" to PlaceCounts(6, 1))
        val list = placeSummaries(listOf(newPlace, minato, cafe, hinata, tori), logs, counts, today)
        assertEquals(listOf("tori", "cafe", "hinata", "minato", "new"), list.map { it.place.id })
        assertEquals(PlaceBadge.Tonight, list[0].badge)
        assertNull(list[1].badge)
        assertEquals(PlaceBadge.Tag("深夜食堂"), list[2].badge)
        assertEquals(PlaceBadge.Milestone, list[3].badge)
        assertEquals(2, list[3].visitCount)
        assertEquals(12, list[3].dishCount)
        assertEquals(2, list[3].photoCount)
        assertEquals(0, list[4].visitCount)
        assertEquals(0, list[4].dishCount)
    }

    @Test
    fun shortAreaStopsAtWardOrCity() {
        assertEquals("京都市中京区", shortArea("京都市中京区木屋町通三条下ル"))
        assertEquals("奈良市", shortArea("奈良市登大路町"))
        assertEquals("Shibuya", shortArea(" Shibuya "))
        assertNull(shortArea(""))
        assertNull(shortArea(null))
    }

    @Test
    fun dishStatsCountQuantitiesAndAverageRatings() {
        val stats = dishStats(logs.filter { it.place.id == "minato" }, listOf(uni, saikyo, tempura))
        assertEquals(listOf("uni", "saikyo", "tempura"), stats.map { it.dishId })
        assertEquals(listOf(2, 2, 1), stats.map { it.timesOrdered })
        assertEquals(listOf(5.0, 4.5, 4.0), stats.map { it.averageRating })
        assertEquals("うに茶碗蒸し", stats[0].nameOriginal)
        assertEquals(LocalDate.parse("2026-10-03"), stats[0].lastOrdered)
    }

    @Test
    fun draftOrdersCountButHaveNoRating() {
        val stats = dishStats(logs.filter { it.place.id == "tori" }, listOf(yakitori, beer))
        val beerStats = stats.first { it.dishId == "beer" }
        assertEquals(2, beerStats.timesOrdered)
        assertTrue(beerStats.isDrink)
        assertNull(beerStats.averageRating)
    }

    @Test
    fun detailListsVisitsNewestFirstWithAnniversaryYear() {
        val love = Milestone("love", "恋爱纪念日", MilestoneType.ANNIVERSARY, LocalDate.parse("2024-11-27"), Recurrence.YEARLY)
        val detail = placeDetail(minato, logs, listOf(uni, saikyo, tempura), listOf(love), today)
        assertEquals("京都市中京区", detail.area)
        assertEquals(2, detail.visitCount)
        assertEquals(listOf("minato2", "minato1"), detail.visits.map { it.memory.logId })
        assertNull(detail.visits[0].milestone)
        assertEquals("love", detail.visits[1].milestone?.id)
        assertEquals(1, detail.visits[1].milestoneYears)
        assertEquals(2, detail.visits[0].memory.visitNumber)
    }

    @Test
    fun dishGlyphTakesFirstKanji() {
        assertEquals("巻", dishGlyph("だし巻き玉子", 0).char)
        assertEquals("焼", dishGlyph("焼き鳥盛り合わせ", 0).char)
        assertEquals("海", dishGlyph("海老の天ぷら", 0).char)
        assertEquals("ナ", dishGlyph("ナポリタン", 0).char)
        assertEquals(dishGlyph("a", 0).tone, dishGlyph("b", 5).tone)
    }

    @Test
    fun menuMarksUnorderedDishesAndConvertsPhotoTime() {
        val takenAt = LocalDateTime.parse("2026-11-17T19:31").toInstant(TimeZone.of("Asia/Tokyo")).toEpochMilliseconds()
        val salt = dish("saba", tori, "鯖の塩焼き", "盐烤青花鱼")
        val menu = placeMenu(tori, logs, listOf(yakitori, salt, beer), listOf(MenuScan("s1", "tori", "seed/menus/tori-1.webp", takenAt)))
        assertEquals(listOf("yakitori", "saba", "beer"), menu.entries.map { it.dish.id })
        assertEquals(1, menu.entries[0].stats?.timesOrdered)
        assertNull(menu.entries[1].stats)
        assertEquals(LocalDateTime.parse("2026-11-17T19:31"), menu.lastScannedAt)
    }
}
