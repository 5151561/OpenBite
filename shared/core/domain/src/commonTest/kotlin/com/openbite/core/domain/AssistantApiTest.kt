package com.openbite.core.domain

import com.openbite.core.testing.FakeMeals
import com.openbite.core.testing.FakeDrafts
import com.openbite.core.testing.FakeMilestones
import com.openbite.core.testing.FakePlaces
import com.openbite.core.testing.FakeLogs
import com.openbite.core.data.DiningLogRepository
import com.openbite.core.data.DraftRepository
import com.openbite.core.model.MealEdit
import com.openbite.core.data.MealRepository
import com.openbite.core.data.MilestoneRepository
import com.openbite.core.model.MilestoneWithPeople
import com.openbite.core.data.NewDraftLog
import com.openbite.core.model.NewPhoto
import com.openbite.core.data.PlaceCounts
import com.openbite.core.data.PlaceRepository
import com.openbite.core.model.DiningLog
import com.openbite.core.model.DiningLogItem
import com.openbite.core.model.DiningStatus
import com.openbite.core.model.Dish
import com.openbite.core.model.LogSource
import com.openbite.core.model.MenuScan
import com.openbite.core.model.Milestone
import com.openbite.core.model.MilestoneType
import com.openbite.core.model.Money
import com.openbite.core.model.Person
import com.openbite.core.model.Photo
import com.openbite.core.model.PhotoKind
import com.openbite.core.model.Place
import com.openbite.core.model.Recurrence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssistantApiTest {
    private val minato = Place("place-minato", "みなと", 35.0050, 135.7680, "京都府京都市中京区木屋町通 1", listOf("适合约会", "纪念日"))
    private val tori = Place("place-tori", "鳥松", 35.0100, 135.7700, "京都府京都市下京区 2", listOf("深夜食堂"))
    private val far = Place("place-far", "遠い店", 34.6937, 135.5023, "大阪府大阪市北区 3", listOf("深夜食堂"))
    private val noLocation = Place("place-none", "位置不明", null, null, null)

    private val menuMinato = listOf(
        Dish("minato-uni", "place-minato", "刺身", "うに", "海胆", Money(2800, "JPY"), listOf("shellfish")),
        Dish("minato-saikyo", "place-minato", "焼物", "西京焼", "西京烧", Money(1600, "JPY")),
    )

    private val love = Milestone("ms-love", "恋爱纪念日", MilestoneType.ANNIVERSARY, LocalDate(2024, 11, 27), Recurrence.YEARLY)
    private val birthday = Milestone("ms-bday", "生日", MilestoneType.BIRTHDAY, LocalDate(1995, 3, 1), Recurrence.YEARLY)
    private val wife = Person("person-wife", "老婆")
    private val roommates = Person("person-room", "大学室友")

    private fun log(
        id: String,
        place: Place,
        date: LocalDate,
        visitedAt: Long,
        milestoneId: String? = null,
        companions: List<Person> = emptyList(),
        source: LogSource = LogSource.USER,
    ) = DiningLog(
        id = id,
        place = place,
        visitedAt = visitedAt,
        timezone = "Asia/Tokyo",
        localDate = date,
        status = DiningStatus.DONE,
        milestoneId = milestoneId,
        note = "好吃",
        source = source,
        items = listOf(DiningLogItem("$id-1", "minato-uni", "うに", Money(2800, "JPY"), 2, 5, "甜")),
        companions = companions,
        rating = 5,
        paidTotal = Money(9999, "JPY"),
        photos = listOf(
            Photo("$id-p", "photos/secret-$id.jpg", PhotoKind.MEAL, null),
            Photo("$id-r", "photos/receipt-$id.jpg", PhotoKind.RECEIPT, null),
        ),
    )

    private val logs = MutableStateFlow(
        listOf(
            log("log-2025", minato, LocalDate(2025, 11, 27), 1_764_200_000_000, "ms-love", listOf(wife)),
            log("log-2024", minato, LocalDate(2024, 11, 27), 1_732_700_000_000, "ms-love", listOf(wife)),
            log("log-tori", tori, LocalDate(2026, 10, 3), 1_791_000_000_000, null, listOf(roommates)),
        ),
    )

    private val drafts = FakeDrafts()
    private var nextId = 0
    private val api = AssistantApi(
        logs = FakeLogs(logs),
        places = FakePlaces(
            listOf(minato, tori, far, noLocation),
            menus = mapOf(minato.id to menuMinato),
            scans = mapOf(minato.id to listOf(MenuScan("scan-1", minato.id, "photos/menu-secret.jpg", 1L, "ja"))),
            readOnly = true,
        ),
        milestones = FakeMilestones(
            listOf(MilestoneWithPeople(love, listOf(wife.id)), MilestoneWithPeople(birthday, emptyList())),
            readOnly = true,
        ),
        drafts = drafts,
        persons = FakeMeals(persons = listOf(wife, roommates), readOnly = true),
        today = { LocalDate(2026, 11, 17) },
        ids = { "id-${nextId++}" },
        // 2026-11-17 20:00 东京
        now = { 1_794_913_200_000 },
        zones = { TimeZone.of("Asia/Tokyo") },
    )

    private suspend fun expectError(kind: AssistantErrorKind, block: suspend () -> Unit) {
        val e = assertFailsWith<AssistantException> { block() }
        assertEquals(kind, e.kind)
    }

    @Test
    fun upcomingMilestonesCarryPeopleAndPreviousMeals() = runTest {
        val result = api.upcomingMilestones(30)
        assertEquals(listOf("ms-love"), result.map { it.milestoneId })
        val m = result.single()
        assertEquals(LocalDate(2026, 11, 27), m.nextDate)
        assertEquals(10, m.daysUntil)
        assertEquals(2, m.yearsSince)
        assertTrue(m.recurring)
        assertEquals(listOf("老婆"), m.people)
        assertEquals(listOf("log-2025", "log-2024"), m.previousMeals.map { it.logId })
    }

    @Test
    fun upcomingMilestonesDefaultsAndLimits() = runTest {
        assertEquals(api.upcomingMilestones(30), api.upcomingMilestones(0))
        assertEquals(setOf("ms-love", "ms-bday"), api.upcomingMilestones(366).map { it.milestoneId }.toSet())
        expectError(AssistantErrorKind.INVALID_ARGUMENT) { api.upcomingMilestones(367) }
    }

    @Test
    fun queryDiningLogsFilters() = runTest {
        assertEquals(listOf("log-tori", "log-2025", "log-2024"), api.queryDiningLogs(null, null, null, null).map { it.logId })
        assertEquals(listOf("log-2025", "log-2024"), api.queryDiningLogs("ms-love", null, null, null).map { it.logId })
        assertEquals(listOf("log-tori"), api.queryDiningLogs(null, " 大学室友 ", null, null).map { it.logId })
        assertEquals(
            listOf("log-2025"),
            api.queryDiningLogs(null, "老婆", LocalDate(2025, 1, 1), LocalDate(2025, 12, 31)).map { it.logId },
        )
        val meal = api.queryDiningLogs("ms-love", null, null, null).first()
        assertEquals("恋爱纪念日", meal.milestoneTitle)
        assertEquals(Money(5600, "JPY"), meal.menuTotal)
        assertEquals(5.0, meal.rating)
    }

    @Test
    fun queryDiningLogsValidates() = runTest {
        expectError(AssistantErrorKind.INVALID_ARGUMENT) { api.queryDiningLogs(null, null, LocalDate(2026, 2, 1), LocalDate(2026, 1, 1)) }
        expectError(AssistantErrorKind.NOT_FOUND) { api.queryDiningLogs("ms-nope", null, null, null) }
        expectError(AssistantErrorKind.INVALID_ARGUMENT) { api.queryDiningLogs(null, "x".repeat(41), null, null) }
    }

    @Test
    fun queryDiningLogsCapsAt50() = runTest {
        logs.value = (1..80).map { log("bulk-$it", tori, LocalDate(2026, 1, 1), it.toLong()) }
        val result = api.queryDiningLogs(null, null, null, null)
        assertEquals(ASSISTANT_MAX_LOGS, result.size)
        assertEquals("bulk-80", result.first().logId)
    }

    @Test
    fun resultsNeverContainPhotosReceiptsOrPaidTotal() = runTest {
        val text = buildString {
            append(api.queryDiningLogs(null, null, null, null))
            append(api.upcomingMilestones(366))
            append(api.getPlaceMenu("place-minato"))
            append(api.searchPlaces(null, null, null, emptyList()))
        }
        assertFalse("photos/" in text)
        assertFalse("receipt" in text)
        assertFalse("9999" in text)
    }

    @Test
    fun searchPlacesByDistanceAndTags() = runTest {
        val near = api.searchPlaces(35.0050, 135.7680, null, emptyList())
        assertEquals(listOf("place-minato", "place-tori"), near.map { it.placeId })
        assertEquals(0.0, near.first().distanceKm)
        assertEquals(listOf("place-tori", "place-far"), api.searchPlaces(35.0050, 135.7680, 50.0, listOf("深夜食堂")).map { it.placeId })
        assertEquals(listOf("place-minato"), api.searchPlaces(null, null, null, listOf("适合约会", " 纪念日 ")).map { it.placeId })
        val recent = api.searchPlaces(null, null, null, emptyList())
        assertEquals("place-tori", recent.first().placeId)
        assertEquals(4, recent.size)
        val m = recent.first { it.placeId == "place-minato" }
        assertEquals(2, m.visitCount)
        assertEquals("京都府京都市中京区", m.area)
        assertNull(m.distanceKm)
    }

    @Test
    fun searchPlacesValidates() = runTest {
        expectError(AssistantErrorKind.INVALID_ARGUMENT) { api.searchPlaces(35.0, null, null, emptyList()) }
        expectError(AssistantErrorKind.INVALID_ARGUMENT) { api.searchPlaces(91.0, 0.0, null, emptyList()) }
        expectError(AssistantErrorKind.INVALID_ARGUMENT) { api.searchPlaces(0.0, 181.0, null, emptyList()) }
        expectError(AssistantErrorKind.INVALID_ARGUMENT) { api.searchPlaces(Double.NaN, 0.0, null, emptyList()) }
        expectError(AssistantErrorKind.INVALID_ARGUMENT) { api.searchPlaces(0.0, 0.0, 51.0, emptyList()) }
        expectError(AssistantErrorKind.INVALID_ARGUMENT) { api.searchPlaces(null, null, null, List(11) { "t$it" }) }
    }

    @Test
    fun getPlaceMenuWithStats() = runTest {
        val menu = api.getPlaceMenu(" place-minato ")
        assertEquals("place-minato", menu.place.placeId)
        assertEquals("ja", menu.menuLanguage)
        assertEquals(listOf("minato-uni", "minato-saikyo"), menu.dishes.map { it.dishId })
        val uni = menu.dishes.first()
        assertEquals(4, uni.timesOrdered)
        assertEquals(5.0, uni.averageRating)
        assertEquals(listOf("shellfish"), uni.allergens)
        assertEquals(0, menu.dishes[1].timesOrdered)
        expectError(AssistantErrorKind.INVALID_ARGUMENT) { api.getPlaceMenu("  ") }
        expectError(AssistantErrorKind.NOT_FOUND) { api.getPlaceMenu("place-nope") }
    }

    @Test
    fun createDiningLogAppendsAssistantDraft() = runTest {
        val before = logs.value
        val id = api.createDiningLog(
            "place-minato",
            listOf(AssistantNewItem("minato-uni", null, 2), AssistantNewItem(null, " 热茶 ", 1)),
            "ms-love",
            " 助手记的 ",
        )
        val (newPlace, draft) = drafts.created.single()
        assertNull(newPlace)
        assertEquals(id, draft.id)
        assertTrue(draft.byAssistant)
        assertEquals("place-minato", draft.placeId)
        assertEquals("ms-love", draft.milestoneId)
        assertEquals("助手记的", draft.note)
        assertEquals("Asia/Tokyo", draft.timezone)
        assertEquals("2026-11-17", draft.localDate)
        assertEquals(1_794_913_200_000, draft.visitedAt)
        val (uni, tea) = draft.items
        assertEquals("minato-uni", uni.dishId)
        assertEquals("うに", uni.name)
        assertEquals("海胆", uni.nameTranslated)
        assertEquals(Money(2800, "JPY"), uni.price)
        assertEquals(2, uni.qty)
        assertNull(tea.dishId)
        assertEquals("热茶", tea.name)
        assertNull(tea.price)
        // 已有记录不动
        assertEquals(before, logs.value)
    }

    @Test
    fun createDiningLogValidates() = runTest {
        val one = listOf(AssistantNewItem("minato-uni", null, 1))
        expectError(AssistantErrorKind.INVALID_ARGUMENT) { api.createDiningLog("", one, null, null) }
        expectError(AssistantErrorKind.NOT_FOUND) { api.createDiningLog("place-nope", one, null, null) }
        expectError(AssistantErrorKind.INVALID_ARGUMENT) { api.createDiningLog("place-minato", emptyList(), null, null) }
        expectError(AssistantErrorKind.INVALID_ARGUMENT) { api.createDiningLog("place-minato", List(31) { one[0] }, null, null) }
        expectError(AssistantErrorKind.INVALID_ARGUMENT) { api.createDiningLog("place-minato", listOf(AssistantNewItem("minato-uni", null, 0)), null, null) }
        expectError(AssistantErrorKind.INVALID_ARGUMENT) { api.createDiningLog("place-minato", listOf(AssistantNewItem("minato-uni", null, 21)), null, null) }
        expectError(AssistantErrorKind.NOT_FOUND) { api.createDiningLog("place-minato", listOf(AssistantNewItem("tori-beer", null, 1)), null, null) }
        expectError(AssistantErrorKind.INVALID_ARGUMENT) { api.createDiningLog("place-minato", listOf(AssistantNewItem(null, " ", 1)), null, null) }
        expectError(AssistantErrorKind.INVALID_ARGUMENT) { api.createDiningLog("place-minato", listOf(AssistantNewItem(null, "a\u0000b", 1)), null, null) }
        expectError(AssistantErrorKind.NOT_FOUND) { api.createDiningLog("place-minato", one, "ms-nope", null) }
        expectError(AssistantErrorKind.INVALID_ARGUMENT) { api.createDiningLog("place-minato", one, null, "x".repeat(501)) }
        expectError(AssistantErrorKind.INVALID_ARGUMENT) { api.createDiningLog("place-minato", one, null, "a\u0007b") }
        assertTrue(drafts.created.isEmpty())
    }

    @Test
    fun distanceKmIsRoughlyRight() {
        // 京都站 → 大阪站约 39 公里
        val d = distanceKm(34.9858, 135.7588, 34.7025, 135.4959)
        assertTrue(d in 38.0..41.0, "was $d")
    }
}
