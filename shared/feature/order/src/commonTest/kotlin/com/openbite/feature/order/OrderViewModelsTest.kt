package com.openbite.feature.order

import com.openbite.core.data.InMemoryOrderSessionRepository
import com.openbite.core.domain.ObservePlaceMenuUseCase
import com.openbite.core.domain.ObserveTasteProfileUseCase
import com.openbite.core.domain.OrderSessions
import com.openbite.core.domain.PlaceOrderUseCase
import com.openbite.core.domain.ResolveReceiptLanguageUseCase
import com.openbite.core.domain.ActiveVisionModel
import com.openbite.core.network.AiProviderClient
import com.openbite.core.testing.FakeSecrets
import com.openbite.core.testing.noNetworkClient
import com.openbite.core.domain.fail
import com.openbite.core.model.Dish
import com.openbite.core.model.MenuStatus
import com.openbite.core.model.Money
import com.openbite.core.model.OrderDish
import com.openbite.core.model.OrderMenu
import com.openbite.core.model.ScanError
import com.openbite.core.testing.FakeDrafts
import com.openbite.core.testing.FakeLogs
import com.openbite.core.testing.FakePlaces
import com.openbite.core.testing.FakePreferences
import com.openbite.core.testing.FakeReminders
import com.openbite.core.testing.clearForTest
import com.openbite.core.testing.inBackground
import com.openbite.core.testing.keepCollecting
import com.openbite.core.testing.kyotoPlace
import com.openbite.core.testing.runViewModelTest
import com.openbite.core.testing.sequentialIds
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OrderViewModelsTest {
    private val sessions = OrderSessions(sequentialIds("session-"), InMemoryOrderSessionRepository())
    private val prefs = FakePreferences()
    private val drafts = FakeDrafts()
    private val places = FakePlaces(
        listOf(kyotoPlace),
        menus = mapOf(
            kyotoPlace.id to listOf(
                Dish("dish-uni", kyotoPlace.id, "蒸物", "うに茶碗蒸し", "海胆茶碗蒸", Money(2800, "JPY")),
                Dish("dish-ale", kyotoPlace.id, "飲み物", "生ビール", "生啤", Money(550, "JPY")),
            ),
        ),
    )

    private fun scanningMenu(status: MenuStatus = MenuStatus.SCANNING) = OrderMenu(
        placeId = null,
        placeName = "新店铺",
        location = 35.0 to 135.7,
        sourceLang = "ja",
        currency = "JPY",
        dishes = listOf(OrderDish("冷奴", "冷奴", "凉拌豆腐", "一品", Money(380, "JPY"), emptyList(), dishId = null)),
        status = status,
    )

    private fun orderVm(sessionId: String? = null, placeId: String? = null) = OrderViewModel(
        sessionId,
        placeId,
        sessions,
        ObservePlaceMenuUseCase(places, FakeLogs()),
        ObserveTasteProfileUseCase(prefs),
    )

    private fun receiptVm(sessionId: String) = ReceiptViewModel(
        sessionId,
        sessions,
        ObserveTasteProfileUseCase(prefs),
        PlaceOrderUseCase(drafts, sequentialIds("log-"), { 1_794_913_200_000 }, { TimeZone.of("Asia/Tokyo") }, FakeReminders()),
        ResolveReceiptLanguageUseCase(prefs, ActiveVisionModel(prefs, FakeSecrets(), AiProviderClient(noNetworkClient()))),
    )

    /** 扮演识别页：收到重试命令就把菜单存好。 */
    private fun TestScope.scanPageSavesOnRetry(sessionId: String) {
        val session = sessions[sessionId]!!
        inBackground {
            session.retryRequests.collect {
                session.updateMenu { menu ->
                    menu.copy(status = MenuStatus.READY, placeId = "new-place", dishes = menu.dishes.map { it.copy(dishId = "dish-1") })
                }
            }
        }
    }

    @Test
    fun orderFromSavedMenuOwnsItsSession() = runViewModelTest {
        val vm = orderVm(placeId = kyotoPlace.id)
        keepCollecting(vm.uiState)
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(2, state.items.size)
        assertEquals(listOf("蒸物", "飲み物"), state.categories)

        vm.setQuantity("生ビール", 2)
        vm.setQuantity("うに茶碗蒸し", 1)
        advanceUntilIdle()
        assertEquals(Money(3900, "JPY"), vm.uiState.value.total)
        assertEquals(3, vm.uiState.value.count)

        val id = vm.sessionId.value!!
        vm.clearForTest()
        assertNull(sessions[id], "自己建的会话随页面一起移除")
    }

    @Test
    fun lostSessionAfterProcessDeathAsksToGoBack() = runViewModelTest {
        val vm = orderVm(sessionId = "gone")
        keepCollecting(vm.uiState)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.unavailable)
        assertTrue(vm.uiState.value.sessionLost)

        val receipt = receiptVm("gone")
        assertTrue(receipt.uiState.value.sessionLost)
    }

    @Test
    fun receiptFollowsMenuLanguageAndFallsBackToEnglishWhenItCannotTranslate() = runViewModelTest {
        val session = sessions.create(scanningMenu())
        session.setQuantity("冷奴", 1)
        val vm = receiptVm(session.id)
        keepCollecting(vm.uiState)
        advanceUntilIdle()
        assertEquals("ja", vm.uiState.value.receipt!!.language.tag)
        assertNull(vm.uiState.value.pendingLanguage)

        // 瑞典语不在内置表里；没选模型，翻不了：先用英文，记下想要的语言。
        session.updateMenu { it.copy(sourceLang = "sv-SE") }
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals("en", state.receipt!!.language.tag)
        assertEquals("sv", state.pendingLanguage)
        assertFalse(state.translating)

        vm.retryTranslation()
        advanceUntilIdle()
        assertEquals("sv", vm.uiState.value.pendingLanguage, "还是翻不了，横幅留着")
    }

    @Test
    fun placeWithoutMenuIsUnavailableButNotLost() = runViewModelTest {
        val vm = orderVm(placeId = "no-such-place")
        keepCollecting(vm.uiState)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.unavailable)
        assertFalse(vm.uiState.value.sessionLost)
    }

    @Test
    fun placingWaitsForMenuThenCreatesOneDraft() = runViewModelTest {
        val session = sessions.create(scanningMenu())
        session.setQuantity("冷奴", 2)
        val vm = receiptVm(session.id)
        keepCollecting(vm.uiState)
        vm.placeOrder()
        vm.placeOrder()
        advanceUntilIdle()
        assertEquals(PlacePhase.WAITING_FOR_MENU, vm.uiState.value.phase)

        session.updateMenu { it.copy(status = MenuStatus.READY, placeId = "p", dishes = it.dishes.map { d -> d.copy(dishId = "dish-1") }) }
        advanceUntilIdle()
        assertEquals(PlacePhase.PLACED, vm.uiState.value.phase)
        assertEquals("log-1", vm.uiState.value.placedLogId)
        assertEquals(1, drafts.created.size)
        assertEquals("dish-1", drafts.log!!.items.single().dishId)
    }

    @Test
    fun failedMenuRetryGoesThroughScanPage() = runViewModelTest {
        val session = sessions.create(scanningMenu())
        session.setQuantity("冷奴", 1)
        session.fail(ScanError.NETWORK)
        val vm = receiptVm(session.id)
        keepCollecting(vm.uiState)
        vm.placeOrder()
        advanceUntilIdle()
        assertEquals(PlacePhase.NEEDS_DECISION, vm.uiState.value.phase)

        // 识别页不在了：重试不了，还是让用户选。
        vm.retrySave()
        advanceUntilIdle()
        assertEquals(PlacePhase.NEEDS_DECISION, vm.uiState.value.phase)
        assertEquals(MenuStatus.FAILED, session.menu.value.status)

        scanPageSavesOnRetry(session.id)
        advanceUntilIdle()
        vm.retrySave()
        advanceUntilIdle()
        assertEquals(PlacePhase.PLACED, vm.uiState.value.phase)
        assertEquals("dish-1", drafts.log!!.items.single().dishId)
    }

    @Test
    fun placeWithoutMenuKeepsOnlySnapshots() = runViewModelTest {
        val session = sessions.create(scanningMenu(MenuStatus.FAILED))
        session.setQuantity("冷奴", 1)
        val vm = receiptVm(session.id)
        keepCollecting(vm.uiState)
        vm.placeWithoutMenu()
        advanceUntilIdle()
        assertEquals(PlacePhase.PLACED, vm.uiState.value.phase)
        assertNull(drafts.log!!.items.single().dishId)
        // 新店一起建出来。
        assertEquals("新店铺", drafts.place!!.name)
    }

    @Test
    fun draftFailureShowsFailedAndCanBeDismissed() = runViewModelTest {
        val session = sessions.create(scanningMenu(MenuStatus.READY))
        session.setQuantity("冷奴", 1)
        drafts.fail = true
        val vm = receiptVm(session.id)
        keepCollecting(vm.uiState)
        vm.placeOrder()
        advanceUntilIdle()
        assertEquals(PlacePhase.FAILED, vm.uiState.value.phase)
        vm.dismissDecision()
        advanceUntilIdle()
        assertEquals(PlacePhase.IDLE, vm.uiState.value.phase)
    }
}
