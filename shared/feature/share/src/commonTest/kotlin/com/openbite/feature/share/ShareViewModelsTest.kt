package com.openbite.feature.share

import com.openbite.core.data.platform.IncomingFile
import com.openbite.core.domain.AppDispatchers
import com.openbite.core.domain.ExportPlaceUseCase
import com.openbite.core.domain.ImportError
import com.openbite.core.domain.ImportPlaceUseCase
import com.openbite.core.domain.ObserveSharePlaceUseCase
import com.openbite.core.domain.OpenSharedPlaceUseCase
import com.openbite.core.domain.ShareOptions
import com.openbite.core.model.Dish
import com.openbite.core.model.Money
import com.openbite.core.testing.FakeImageProcessor
import com.openbite.core.testing.FakeImport
import com.openbite.core.testing.FakeIncomingFiles
import com.openbite.core.testing.FakePhotoStore
import com.openbite.core.testing.FakePlaces
import com.openbite.core.testing.keepCollecting
import com.openbite.core.testing.kyotoPlace
import com.openbite.core.testing.runViewModelTest
import com.openbite.core.testing.sequentialIds
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShareViewModelsTest {
    private val places = FakePlaces(
        listOf(kyotoPlace),
        menus = mapOf(kyotoPlace.id to listOf(Dish("dish-uni", kyotoPlace.id, "蒸物", "うに茶碗蒸し", "海胆茶碗蒸", Money(2800, "JPY")))),
    )
    private val photos = FakePhotoStore()
    private val files = FakeIncomingFiles()
    private val repo = FakeImport()

    private fun TestScope.dispatchers() = AppDispatchers(StandardTestDispatcher(testScheduler))

    private suspend fun TestScope.exportedBytes(): ByteArray =
        ExportPlaceUseCase(places, photos, dispatchers())(kyotoPlace.id, ShareOptions())!!.bytes

    private fun TestScope.importVm(uri: String) = ImportViewModel(
        uri,
        OpenSharedPlaceUseCase(files, dispatchers()),
        ImportPlaceUseCase(FakeImageProcessor(), photos, repo, sequentialIds("new-"), { 5_000L }),
    )

    @Test
    fun importReadsPreviewThenImportsOnce() = runViewModelTest {
        files.files["content://a"] = IncomingFile("みなと.openbite", null, exportedBytes(), false)
        val vm = importVm("content://a")
        assertEquals(ImportPhase.READING, vm.uiState.value.phase)
        advanceUntilIdle()
        assertEquals(ImportPhase.READY, vm.uiState.value.phase)
        assertEquals("割烹 みなと", vm.uiState.value.preview!!.name)
        assertEquals(1, vm.uiState.value.preview!!.dishCount)

        // 连点两次“导入”只导一次。
        vm.confirm()
        vm.confirm()
        advanceUntilIdle()
        assertEquals(1, repo.imports)
        assertEquals(ImportPhase.DONE, vm.uiState.value.phase)
        assertNotNull(vm.uiState.value.placeId)

        // 跳转过就消费掉，重组时不再跳。
        vm.importHandled()
        assertNull(vm.uiState.value.placeId)
        vm.confirm()
        advanceUntilIdle()
        assertEquals(1, repo.imports)
    }

    @Test
    fun importRejectsWrongFileAndUnreadableUri() = runViewModelTest {
        files.files["content://pdf"] = IncomingFile("menu.pdf", "application/pdf", byteArrayOf(1), false)
        val wrong = importVm("content://pdf")
        advanceUntilIdle()
        assertEquals(ImportPhase.FAILED, wrong.uiState.value.phase)
        assertEquals(ImportError.NOT_OPENBITE, wrong.uiState.value.error)

        val revoked = importVm("content://gone")
        advanceUntilIdle()
        assertEquals(ImportError.READ_FAILED, revoked.uiState.value.error)
    }

    @Test
    fun importSaveFailure() = runViewModelTest {
        files.files["content://a"] = IncomingFile("みなと.openbite", null, exportedBytes(), false)
        repo.fail = true
        val vm = importVm("content://a")
        advanceUntilIdle()
        vm.confirm()
        advanceUntilIdle()
        assertEquals(ImportPhase.FAILED, vm.uiState.value.phase)
        assertEquals(ImportError.SAVE_FAILED, vm.uiState.value.error)
    }

    @Test
    fun sharePlaceExportsAndConsumesEvents() = runViewModelTest {
        val vm = SharePlaceViewModel(kyotoPlace.id, ObserveSharePlaceUseCase(places), ExportPlaceUseCase(places, photos, dispatchers()))
        keepCollecting(vm.uiState)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.dishCount)
        assertTrue(vm.uiState.value.fileName.endsWith(".openbite"))

        vm.export()
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.exported)
        vm.exportHandled()
        advanceUntilIdle()
        assertNull(vm.uiState.value.exported)
    }

    @Test
    fun sharePlaceFailureIsShownOnce() = runViewModelTest {
        val vm = SharePlaceViewModel("deleted", ObserveSharePlaceUseCase(places), ExportPlaceUseCase(places, photos, dispatchers()))
        keepCollecting(vm.uiState)
        vm.export()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.failed)
        vm.failureShown()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.failed)
    }
}
