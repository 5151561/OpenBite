package com.openbite.feature.meal

import com.openbite.core.data.NewPhoto
import com.openbite.core.model.DiningLog
import com.openbite.core.model.DiningLogItem
import com.openbite.core.model.DiningStatus
import com.openbite.core.model.LogSource
import com.openbite.core.model.Money
import com.openbite.core.model.Person
import com.openbite.core.model.Photo
import com.openbite.core.model.PhotoKind
import com.openbite.core.model.Place
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MealEditorTest {
    private val log = DiningLog(
        id = "l", place = Place("p", "とり松", null, null, null), visitedAt = 0, timezone = "Asia/Tokyo",
        localDate = LocalDate(2026, 11, 17), status = DiningStatus.DRAFT, milestoneId = null, note = null, source = LogSource.USER,
        items = listOf(
            DiningLogItem("a", "d1", "焼き鳥盛り合わせ", Money(980, "JPY"), 1, null, null, "烤鸡肉串拼盘"),
            DiningLogItem("b", "d2", "生ビール", Money(550, "JPY"), 2, null, null, "生啤"),
        ),
        companions = listOf(Person("wife", "老婆")),
        photos = listOf(Photo("ph1", "photos/ph1.jpg", PhotoKind.MEAL, 1), Photo("r1", "photos/r1.jpg", PhotoKind.RECEIPT, 2)),
    )

    @Test
    fun loadsFromLogWithoutReceiptPhotos() {
        val editor = MealEditor.from(log)
        assertEquals(setOf("wife"), editor.personIds)
        assertEquals(listOf("ph1"), editor.photos.map { it.id })
        assertEquals(Money(2080, "JPY"), editor.orderedTotal)
        assertFalse(editor.dirty)
    }

    @Test
    fun ratingsToggleOffWhenTappedAgain() {
        val editor = MealEditor.from(log).setRating(5).setItemRating("a", 4)
        assertEquals(5, editor.rating)
        assertNull(editor.setRating(5).rating)
        assertEquals(4, editor.items.first().rating)
        assertNull(editor.setItemRating("a", 4).items.first().rating)
        assertTrue(editor.dirty)
    }

    @Test
    fun qtyZeroRemovesItemAndTotalFollows() {
        val editor = MealEditor.from(log).setQty("b", 1).setQty("a", 0)
        assertEquals(listOf("b"), editor.items.map { it.id })
        assertEquals(listOf("a"), editor.removedItemIds)
        assertEquals(Money(550, "JPY"), editor.orderedTotal)
        assertEquals(MealEditor.MAX_QTY, editor.setQty("b", 500).items.single().qty)
    }

    @Test
    fun photosAddedAndRemoved() {
        val added = NewPhoto("ph2", "photos/ph2.jpg", PhotoKind.MEAL, 3)
        val editor = MealEditor.from(log).addPhoto(added).removePhoto("ph1")
        assertEquals(listOf("ph1"), editor.removedPhotos.map { it.id })
        assertEquals("photos/ph2.jpg", editor.addedPhotoPath("ph2"))
        assertTrue(editor.removePhoto("ph2").addedPhotos.isEmpty())
    }

    @Test
    fun toEditTrimsTextAndCarriesEverything() {
        val edit = MealEditor.from(log)
            .setNote("  炭火味很足  ")
            .setItemComment("a", " ")
            .toggleMilestone("love")
            .togglePerson("wife")
            .togglePerson("roommates")
            .toEdit(done = true, updatedAt = 9)
        assertEquals("炭火味很足", edit.note)
        assertNull(edit.items.first().comment)
        assertEquals("love", edit.milestoneId)
        assertEquals(listOf("roommates"), edit.personIds)
        assertTrue(edit.done)
        assertNull(MealEditor.from(log).toggleMilestone("love").toggleMilestone("love").milestoneId)
    }
}
