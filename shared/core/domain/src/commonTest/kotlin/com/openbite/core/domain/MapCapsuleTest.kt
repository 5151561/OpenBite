package com.openbite.core.domain

import com.openbite.core.model.DiningLog
import com.openbite.core.model.DiningLogItem
import com.openbite.core.model.DiningStatus
import com.openbite.core.model.LogSource
import com.openbite.core.model.Milestone
import com.openbite.core.model.MilestoneType
import com.openbite.core.model.Money
import com.openbite.core.model.Place
import com.openbite.core.model.Recurrence
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class MapCapsuleTest {
    private val today = LocalDate(2026, 11, 17)
    private val minato = Place("minato", "割烹 みなと", 35.0, 135.77, null, listOf("适合约会", "纪念日"))
    private val tori = Place("tori", "炭火焼鳥 とり松", 35.01, 135.77, null, listOf("深夜食堂"))
    private val hinata = Place("hinata", "麺屋 ひなた", 34.98, 135.76, null, listOf("深夜食堂"))
    private val noLocation = Place("new", "おばんざい 小春", null, null, null)
    private val love = Milestone("love", "恋爱纪念日", MilestoneType.ANNIVERSARY, LocalDate(2024, 11, 27), Recurrence.YEARLY)

    private fun log(id: String, place: Place, at: String, milestone: String? = null, status: DiningStatus = DiningStatus.DONE) = DiningLog(
        id = id, place = place,
        visitedAt = LocalDateTime.parse(at).toInstant(TimeZone.of("Asia/Tokyo")).toEpochMilliseconds(),
        timezone = "Asia/Tokyo", localDate = LocalDateTime.parse(at).date, status = status, milestoneId = milestone,
        note = null, source = LogSource.USER,
        items = listOf(DiningLogItem("$id-i", null, "菜", Money(1000, "JPY"), 1, 5, null)),
    )

    private val logs = listOf(
        log("tori-draft", tori, "2026-11-17T19:42", status = DiningStatus.DRAFT),
        log("hinata-1", hinata, "2026-11-14T22:40"),
        log("minato-2", minato, "2026-10-03T18:45"),
        log("minato-1", minato, "2025-11-27T19:30", milestone = "love"),
        log("new-1", noLocation, "2026-11-16T12:00"),
    )
    private val pins = mapPins(listOf(minato, tori, hinata, noLocation), logs)
    private val memories = summarize(logs, today)
    private val highlights = highlight(upcomingMilestones(listOf(love), today, 400), memories)

    @Test
    fun onlyPlacesWithCoordinatesBecomePins() {
        assertEquals(listOf("minato", "tori", "hinata"), pins.map { it.place.id })
        assertEquals(listOf(true, false, false), pins.map { it.hasMilestoneMeal })
        assertEquals(2, pins.first().visitCount)
    }

    @Test
    fun filtersAndCounts() {
        assertEquals(
            mapOf(MapFilter.ALL to 3, MapFilter.MILESTONE to 1, MapFilter.DATE to 1, MapFilter.LATE_NIGHT to 2),
            filterCounts(pins),
        )
        assertEquals(listOf("tori", "hinata"), filterPins(pins, MapFilter.LATE_NIGHT, "").map { it.place.id })
        assertEquals(listOf("hinata"), filterPins(pins, MapFilter.LATE_NIGHT, " ひなた ").map { it.place.id })
        assertEquals(listOf("minato"), filterPins(pins, MapFilter.ALL, "约会").map { it.place.id })
    }

    @Test
    fun selectionFollowsFilter() {
        val lateNight = filterPins(pins, MapFilter.LATE_NIGHT, "")
        val milestoneOnly = filterPins(pins, MapFilter.MILESTONE, "")
        // 选中的店还看得见：不变
        assertEquals("tori", visibleSelection("tori", lateNight, highlights))
        // 选中的店被筛掉，默认卡片那家可见：换成它
        assertEquals("minato", visibleSelection("tori", milestoneOnly, highlights))
        // 选中的店被筛掉，默认那家也不可见：换成第一家可见的
        assertEquals("tori", visibleSelection("minato", lateNight, highlights))
        // 一家都不剩
        assertNull(visibleSelection("minato", emptyList(), highlights))
        // 卡片跟着换：「深夜食堂」下选中 minato，显示 tori 的卡片
        val shown = visibleSelection("minato", lateNight, highlights)!!
        assertEquals("tori", capsuleFor(shown, lateNight, memories, listOf(love), highlights)!!.placeId)
    }

    @Test
    fun defaultCapsuleIsLastYearsAnniversaryMeal() {
        val capsule = capsuleFor(null, pins, memories, listOf(love), highlights)!!
        assertEquals(CapsuleKind.UPCOMING_MILESTONE, capsule.kind)
        assertEquals("minato-1", capsule.memory.logId)
        assertEquals(1, capsule.milestoneYears)
        assertEquals(1, capsule.yearsAgo)
        assertEquals(2, capsule.visitCount)
        // 选中的就是默认那家：还是默认卡片。
        assertEquals(CapsuleKind.UPCOMING_MILESTONE, capsuleFor("minato", pins, memories, listOf(love), highlights)!!.kind)
    }

    @Test
    fun tappingAnotherPinShowsItsMostRecentMeal() {
        val capsule = capsuleFor("tori", pins, memories, listOf(love), highlights)!!
        assertEquals(CapsuleKind.PLACE_RECENT, capsule.kind)
        assertEquals("tori-draft", capsule.memory.logId)
        assertNull(capsule.milestone)
    }

    @Test
    fun placeCapsulePrefersMilestoneMeal() {
        val pin = pins.first { it.place.id == "minato" }
        val capsule = placeCapsule(pin, memories, listOf(love))!!
        assertEquals(CapsuleKind.PLACE_MILESTONE, capsule.kind)
        assertEquals("minato-1", capsule.memory.logId)
    }

    @Test
    fun noDefaultWhenMilestoneShopIsNotOnMap() {
        val withoutMinato = pins.filterNot { it.place.id == "minato" }
        assertNull(capsuleFor(null, withoutMinato, memories, listOf(love), highlights))
        assertNull(placeCapsule(MapPin(noLocation, 0.0, 0.0, placeGlyph(noLocation.name), false, 0), emptyList(), emptyList()))
    }

    @Test
    fun tagsAreNormalizedAndSuggested() {
        assertEquals("深夜 食堂", normalizeTag("  深夜   食堂 "))
        assertNull(normalizeTag("   "))
        assertNull(normalizeTag("x".repeat(MAX_TAG_LENGTH + 1)))
        assertEquals(listOf("纪念日", "深夜食堂"), tagSuggestions(listOf("纪念日", "适合约会"), listOf("适合约会")))
    }
}
