package com.openbite.feature.meal

import com.openbite.core.domain.AddMealPhotoUseCase
import com.openbite.core.domain.AddPersonUseCase
import com.openbite.core.domain.DiscardPhotosUseCase
import com.openbite.core.domain.ObserveMealUseCase
import com.openbite.core.domain.SaveMealUseCase
import com.openbite.core.model.ScanError
import com.openbite.core.testing.FakeImageProcessor
import com.openbite.core.testing.FakeMeals
import com.openbite.core.testing.FakeMilestones
import com.openbite.core.testing.FakePhotoStore
import com.openbite.core.testing.FakeTempFiles
import com.openbite.core.testing.appScope
import com.openbite.core.testing.clearForTest
import com.openbite.core.testing.diningLog
import com.openbite.core.testing.runViewModelTest
import com.openbite.core.testing.sequentialIds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemoryEditViewModelTest {
    private val meals = FakeMeals(logs = listOf(diningLog("log-1")))
    private val photos = FakePhotoStore()
    private val images = FakeImageProcessor()
    private val temp = FakeTempFiles()

    private fun TestScope.viewModel() = MemoryEditViewModel(
        logId = "log-1",
        observeMeal = ObserveMealUseCase(meals, FakeMilestones()),
        saveMeal = SaveMealUseCase(meals, photos) { 9_000L },
        addPerson = AddPersonUseCase(meals, sequentialIds("person-")),
        addPhoto = AddMealPhotoUseCase(images, photos, sequentialIds("photo-"), { 1_000L }, temp),
        discardPhotos = DiscardPhotosUseCase(photos, appScope),
        now = { 9_000L },
        today = { LocalDate(2026, 11, 17) },
    ).also { advanceUntilIdle() }

    @Test
    fun loadsEditorFromLog() = runViewModelTest {
        val vm = viewModel()
        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.editor!!.items.size)
        assertFalse(state.editor!!.dirty)
    }

    @Test
    fun photoAddedWhileSavingStaysPendingAndDirty() = runViewModelTest {
        val vm = viewModel()
        vm.addPhoto("content://camera/a")
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        meals.saveGate = gate
        vm.save(done = true)
        runCurrent()
        assertTrue(vm.uiState.value.saving)

        // 保存还没完成时又加了一张照片。
        vm.addPhoto("content://camera/b")
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        val saved = meals.saved.single()
        assertEquals(listOf("photos/photo-1.jpg"), saved.addedPhotos.map { it.path })
        val editor = vm.uiState.value.editor!!
        // 第二张没存进去：还算待清理（离开页面会被删），页面也还有没存的改动。
        assertEquals(listOf("photos/photo-2.jpg"), editor.addedPhotos.map { it.path })
        assertTrue(editor.dirty)
    }

    @Test
    fun saveFailureShowsErrorAndKeepsEdits() = runViewModelTest {
        val vm = viewModel()
        vm.edit { it.setNote("好吃") }
        meals.failSave = true
        vm.save(done = true)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.saving)
        assertNull(state.saved)
        assertTrue(state.saveFailed)
        assertTrue(state.editor!!.dirty)
        vm.saveErrorShown()
        assertFalse(vm.uiState.value.saveFailed)

        // 再存一次成功。
        meals.failSave = false
        vm.save(done = false)
        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.saved)
        assertEquals("好吃", meals.saved.single().note)
    }

    @Test
    fun photoErrorIsReportedAndConsumed() = runViewModelTest {
        val vm = viewModel()
        images.fail = true
        vm.addPhoto("content://camera/x")
        advanceUntilIdle()
        assertEquals(ScanError.IMAGE_UNREADABLE, vm.uiState.value.photoError)
        vm.photoErrorShown()
        assertNull(vm.uiState.value.photoError)
        // 相机原图读完就删，失败也一样。
        assertEquals(listOf("content://camera/x"), temp.discarded)
    }

    @Test
    fun discardAndLeavingDeleteUnsavedPhotos() = runViewModelTest {
        val vm = viewModel()
        vm.addPhoto("content://camera/a")
        advanceUntilIdle()
        vm.discard()
        advanceUntilIdle()
        assertEquals(listOf("photos/photo-1.jpg"), photos.deleted)
        assertFalse(vm.uiState.value.editor!!.dirty)

        vm.addPhoto("content://camera/b")
        advanceUntilIdle()
        vm.clearForTest()
        advanceUntilIdle()
        assertEquals(listOf("photos/photo-1.jpg", "photos/photo-2.jpg"), photos.deleted)
    }

    @Test
    fun savedPhotosAreNotDeletedOnLeave() = runViewModelTest {
        val vm = viewModel()
        vm.addPhoto("content://camera/a")
        advanceUntilIdle()
        vm.save(done = true)
        advanceUntilIdle()
        assertEquals(true, vm.uiState.value.saved)
        vm.clearForTest()
        advanceUntilIdle()
        assertTrue(photos.deleted.isEmpty())
    }

    @Test
    fun addCompanionSelectsNewPerson() = runViewModelTest {
        val vm = viewModel()
        vm.addCompanion("  小林 ")
        advanceUntilIdle()
        assertEquals("小林", meals.persons.value.single().displayName)
        assertEquals(setOf("person-1"), vm.uiState.value.editor!!.personIds)
    }
}
