package com.openbite.core.domain

import com.openbite.core.model.MenuScan
import com.openbite.core.model.Milestone
import com.openbite.core.model.MilestoneType
import com.openbite.core.model.MilestoneWithPeople
import com.openbite.core.model.Photo
import com.openbite.core.model.PhotoKind
import com.openbite.core.model.Place
import com.openbite.core.model.PlaceDeletionSummary
import com.openbite.core.model.Recurrence
import com.openbite.core.testing.FakeLogs
import com.openbite.core.testing.FakeMilestones
import com.openbite.core.testing.FakePhotoStore
import com.openbite.core.testing.FakePlaceRemoval
import com.openbite.core.testing.FakePlaces
import com.openbite.core.testing.FakeReminders
import com.openbite.core.testing.diningLog
import com.openbite.core.testing.testAppScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeletePlaceTest {
    private val koharu = Place("koharu", "おばんざい 小春", null, null, null, listOf("家常菜"))
    private val minato = Place("minato", "割烹 みなと", null, null, null)
    private val dish = com.openbite.core.model.Dish("d1", "koharu", "一品", "湯葉刺し", "生豆腐皮", null)
    private val places = FakePlaces(
        listOf(koharu, minato),
        menus = mapOf("koharu" to listOf(dish), "minato" to listOf(dish.copy(id = "d2", placeId = "minato"))),
        scans = mapOf(
            "koharu" to listOf(MenuScan("s1", "koharu", "photos/s1.jpg", 1)),
            "minato" to listOf(MenuScan("s2", "minato", "seed/menus/minato-1.jpg", 1)),
        ),
    )
    private val logs = FakeLogs(
        listOf(
            diningLog("draft", place = koharu).copy(
                photos = listOf(Photo("p1", "photos/p1.jpg", PhotoKind.MEAL, null), Photo("r1", "photos/r1.jpg", PhotoKind.RECEIPT, null)),
            ),
            diningLog("love-2025", place = minato, milestoneId = "love"),
        ),
    )
    private val milestones = FakeMilestones(
        listOf(MilestoneWithPeople(Milestone("love", "恋爱纪念日", MilestoneType.ANNIVERSARY, LocalDate(2024, 11, 27), Recurrence.YEARLY), listOf("wife"))),
    )
    private val photos = FakePhotoStore().apply {
        listOf("photos/s1.jpg", "photos/p1.jpg", "photos/r1.jpg", "photos/other.jpg").forEach { files[it] = byteArrayOf(1) }
    }
    private val reminders = FakeReminders()
    private val removal = FakePlaceRemoval(places, logs)

    private fun TestScope.delete() = DeletePlaceUseCase(removal, DiscardPhotosUseCase(photos, testAppScope(this)), reminders)

    @Test
    fun summaryCountsWhatGoesAway() = runTest {
        assertEquals(PlaceDeletionSummary("おばんざい 小春", 1, 1, 3), PlaceDeletionSummaryUseCase(removal)("koharu"))
        assertNull(PlaceDeletionSummaryUseCase(removal)("gone"))
    }

    @Test
    fun deletesPlaceLogsPhotosAndRemindersOnly() = runTest {
        assertTrue(delete()("koharu"))
        advanceUntilIdle()
        assertEquals(listOf("minato"), places.places.value.map { it.id })
        assertEquals(listOf("minato"), places.menus.value.keys.toList())
        assertEquals(listOf("love-2025"), logs.logs.value.map { it.id })
        // 菜单原照片、饭菜照片、小票照片的文件都删了；别的照片不动。
        assertEquals(setOf("photos/other.jpg"), photos.files.keys)
        assertEquals(listOf("draft"), reminders.cancelled)
        // 纪念日保留。
        assertEquals(listOf("love"), milestones.rows.value.keys.toList())
    }

    @Test
    fun unknownPlaceIsNoOp() = runTest {
        assertFalse(delete()("gone"))
        advanceUntilIdle()
        assertEquals(2, places.places.value.size)
        assertTrue(photos.deleted.isEmpty())
    }

    @Test
    fun failureDeletesNoFiles() = runTest {
        removal.fail = true
        kotlin.test.assertFailsWith<IllegalStateException> { delete()("koharu") }
        advanceUntilIdle()
        assertTrue(photos.deleted.isEmpty())
        assertEquals(2, places.places.value.size)
    }
}
