package com.openbite.feature.place

import com.openbite.core.domain.AddPlaceTagUseCase
import com.openbite.core.domain.DeletePlaceUseCase
import com.openbite.core.domain.DiscardPhotosUseCase
import com.openbite.core.domain.ObserveAllTagsUseCase
import com.openbite.core.domain.ObservePlaceDetailUseCase
import com.openbite.core.domain.PlaceDeletionSummaryUseCase
import com.openbite.core.domain.RemovePlaceTagUseCase
import com.openbite.core.model.MenuScan
import com.openbite.core.model.Place
import com.openbite.core.testing.FakeLogs
import com.openbite.core.testing.FakeMilestones
import com.openbite.core.testing.FakePhotoStore
import com.openbite.core.testing.FakePlaceRemoval
import com.openbite.core.testing.FakePlaces
import com.openbite.core.testing.FakeReminders
import com.openbite.core.testing.appScope
import com.openbite.core.testing.diningLog
import com.openbite.core.testing.keepCollecting
import com.openbite.core.testing.runViewModelTest
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaceDetailDeleteTest {
    private val koharu = Place("koharu", "おばんざい 小春", null, null, null)
    private val minato = Place("minato", "割烹 みなと", null, null, null)
    private val places = FakePlaces(listOf(koharu, minato), scans = mapOf("koharu" to listOf(MenuScan("s1", "koharu", "photos/s1.jpg", 1))))
    private val logs = FakeLogs(listOf(diningLog("draft", place = koharu), diningLog("old", place = minato)))
    private val photos = FakePhotoStore().apply { files["photos/s1.jpg"] = byteArrayOf(1) }
    private val removal = FakePlaceRemoval(places, logs)

    private fun TestScope.viewModel() = PlaceDetailViewModel(
        "koharu",
        ObservePlaceDetailUseCase(places, logs, FakeMilestones()) { LocalDate(2026, 11, 17) },
        { LocalDate(2026, 11, 17) },
        ObserveAllTagsUseCase(places),
        AddPlaceTagUseCase(places),
        RemovePlaceTagUseCase(places),
        PlaceDeletionSummaryUseCase(removal),
        DeletePlaceUseCase(removal, DiscardPhotosUseCase(photos, appScope), FakeReminders()),
    ).also { keepCollecting(it.uiState) }

    @Test
    fun confirmThenDeleteThenAskToGoBack() = runViewModelTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.requestDelete()
        advanceUntilIdle()
        val summary = vm.uiState.value.confirmingDelete!!
        assertEquals("おばんざい 小春", summary.placeName)
        assertEquals(1, summary.logCount)
        assertEquals(1, summary.photoCount)

        vm.confirmDelete()
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state.deleted)
        assertNull(state.confirmingDelete)
        // 店没了，详情是 null（页面显示“已经不在了”，不会崩）。
        assertNull(state.detail)
        assertEquals(listOf("minato"), places.places.value.map { it.id })
        assertEquals(listOf("old"), logs.logs.value.map { it.id })
        assertTrue(photos.files.isEmpty())
    }

    @Test
    fun cancelKeepsEverything() = runViewModelTest {
        val vm = viewModel()
        vm.requestDelete()
        advanceUntilIdle()
        vm.cancelDelete()
        vm.confirmDelete()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.deleted)
        assertEquals(2, places.places.value.size)
    }

    @Test
    fun failureIsShownOnceAndNothingIsDeleted() = runViewModelTest {
        val vm = viewModel()
        vm.requestDelete()
        advanceUntilIdle()
        removal.fail = true
        vm.confirmDelete()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.deleteFailed)
        assertFalse(vm.uiState.value.deleted)
        vm.deleteErrorShown()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.deleteFailed)
        assertEquals(2, places.places.value.size)
        assertEquals(1, photos.files.size)
    }
}
