package com.openbite.feature.scan

import com.openbite.core.data.InMemoryOrderSessionRepository
import com.openbite.core.domain.AppDispatchers
import com.openbite.core.domain.DiscardCaptureUseCase
import com.openbite.core.domain.OrderSessions
import com.openbite.core.domain.RenamePlaceUseCase
import com.openbite.core.domain.ScanMenuUseCase
import com.openbite.core.data.platform.LocationProvider
import com.openbite.core.model.MenuStatus
import com.openbite.core.model.ScanError
import com.openbite.core.network.AiProviderClient
import com.openbite.core.domain.ActiveVisionModel
import com.openbite.core.model.AiSettings
import com.openbite.core.testing.FakeImageProcessor
import com.openbite.core.testing.FakeMenuScans
import com.openbite.core.testing.FakePhotoStore
import com.openbite.core.testing.FakePlaces
import com.openbite.core.testing.FakePreferences
import com.openbite.core.testing.FakeSecrets
import com.openbite.core.testing.FakeTempFiles
import com.openbite.core.testing.appScope
import com.openbite.core.testing.clearForTest
import com.openbite.core.testing.noNetworkClient
import com.openbite.core.testing.runViewModelTest
import com.openbite.core.testing.sequentialIds
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScanViewModelTest {
    // 照片带 GPS，不会去要定位权限。
    private val images = FakeImageProcessor(gps = 35.0 to 135.7)
    private val scans = FakeMenuScans()
    private val temp = FakeTempFiles()
    private val prefs = FakePreferences(AiSettings(demoMode = true))
    private val sessions = OrderSessions(sequentialIds("session-"), InMemoryOrderSessionRepository())

    private fun TestScope.viewModel() = ScanViewModel(
        placeId = null,
        scanMenu = ScanMenuUseCase(
            imageProcessor = images,
            locationProvider = LocationProvider { 35.0 to 135.7 },
            photoStore = FakePhotoStore(),
            scans = scans,
            preferences = prefs,
            activeModel = ActiveVisionModel(prefs, FakeSecrets(), AiProviderClient(noNetworkClient())),
            ids = sequentialIds(),
            now = { 1_000L },
            tempFiles = temp,
            dispatchers = AppDispatchers(StandardTestDispatcher(testScheduler)),
        ),
        renamePlace = RenamePlaceUseCase(FakePlaces()),
        sessions = sessions,
        discardCapture = DiscardCaptureUseCase(temp, appScope),
    )

    @Test
    fun sampleScanFinishesAndFillsOrderSession() = runViewModelTest {
        val vm = viewModel()
        vm.onPhoto("content://camera/1", "新店铺")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(ScanPhase.DONE, state.phase)
        assertEquals(7, state.dishes.size)
        assertNotNull(scans.saved)
        val menu = sessions[vm.sessionId]!!.menu.value
        assertEquals(MenuStatus.READY, menu.status)
        assertEquals(7, menu.dishes.size)
        assertTrue(menu.dishes.all { it.dishId != null })
        // 识别成功后相机原图删掉。
        assertEquals(listOf("content://camera/1"), temp.discarded)
    }

    @Test
    fun photoWithoutGpsAsksForLocationPermission() = runViewModelTest {
        images.gps = null
        val vm = viewModel()
        vm.onPhoto("content://camera/1", "新店铺")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.askingLocationPermission)
        vm.onLocationPermissionResult(false)
        advanceUntilIdle()
        assertEquals(ScanPhase.DONE, vm.uiState.value.phase)
        assertNull(scans.saved!!.first!!.lat)
    }

    @Test
    fun secondPhotoIsIgnoredWhileWorking() = runViewModelTest {
        val vm = viewModel()
        vm.onPhoto("content://camera/1", "新店铺")
        vm.onPhoto("content://camera/2", "新店铺")
        advanceUntilIdle()
        assertEquals(listOf("content://camera/1"), images.processed)
    }

    @Test
    fun failureThenRetryCommandFromOrderPage() = runViewModelTest {
        val vm = viewModel()
        images.fail = true
        vm.onPhoto("content://camera/1", "新店铺")
        advanceUntilIdle()
        assertEquals(ScanPhase.FAILED, vm.uiState.value.phase)
        assertEquals(ScanError.IMAGE_UNREADABLE, vm.uiState.value.error)
        val session = sessions[vm.sessionId]!!
        assertEquals(MenuStatus.FAILED, session.menu.value.status)

        // 小票页点“重试”：发命令给识别页，识别页重新识别。
        images.fail = false
        assertTrue(session.retry())
        advanceUntilIdle()
        assertEquals(ScanPhase.DONE, vm.uiState.value.phase)
        assertEquals(MenuStatus.READY, session.menu.value.status)
    }

    @Test
    fun retryUsesLatestSettings() = runViewModelTest {
        prefs.ai.value = AiSettings()
        val vm = viewModel()
        vm.onPhoto("content://camera/1", "新店铺")
        advanceUntilIdle()
        assertEquals(ScanError.NO_MODEL, vm.uiState.value.error)
        // 去设置里开了演示模式，回来点重试。
        prefs.ai.value = AiSettings(demoMode = true)
        assertTrue(vm.retry())
        advanceUntilIdle()
        assertEquals(ScanPhase.DONE, vm.uiState.value.phase)
    }

    @Test
    fun resetAndLeavingDiscardCaptureAndSession() = runViewModelTest {
        val vm = viewModel()
        images.fail = true
        vm.onPhoto("content://camera/1", "新店铺")
        advanceUntilIdle()
        // 失败时原图留着给重试。
        assertTrue(temp.discarded.isEmpty())

        vm.reset()
        advanceUntilIdle()
        assertEquals(ScanPhase.WAITING_PHOTO, vm.uiState.value.phase)
        assertEquals(listOf("content://camera/1"), temp.discarded)

        vm.onPhoto("content://camera/2", "新店铺")
        advanceUntilIdle()
        val id = vm.sessionId
        vm.clearForTest()
        advanceUntilIdle()
        assertEquals(listOf("content://camera/1", "content://camera/2"), temp.discarded)
        assertNull(sessions[id])
    }
}
