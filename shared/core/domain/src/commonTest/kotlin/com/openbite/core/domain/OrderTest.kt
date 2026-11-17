package com.openbite.core.domain

import com.openbite.core.testing.FakeReminders
import com.openbite.core.testing.FakeDrafts
import com.openbite.core.model.OrderMenu
import com.openbite.core.model.OrderDish
import com.openbite.core.model.MenuStatus
import com.openbite.core.data.DraftRepository
import com.openbite.core.data.NewDraftLog
import com.openbite.core.model.Money
import com.openbite.core.model.Place
import com.openbite.core.model.ScanError
import com.openbite.core.model.VisionModelChoice
import com.openbite.core.model.ScannedDish
import com.openbite.core.data.InMemoryOrderSessionRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class OrderTest {
    private fun newSession(placeId: String? = null) = OrderSession.standalone(
        "s",
        OrderMenu(placeId, "新店铺", null, null, null, emptyList(), MenuStatus.SCANNING),
    )

    private fun OrderSession.scan(vararg dishes: ScannedDish) {
        apply(ScanUpdate.Started(VisionModelChoice.SAMPLE))
        apply(ScanUpdate.Prepared(35.0 to 135.7))
        apply(ScanUpdate.Header("とり松", 3, "JPY", "ja"))
        dishes.forEach { apply(ScanUpdate.DishFound(it)) }
    }

    @Test
    fun streamFillsMenuWithHeaderCurrencyAndKeys() {
        val session = newSession()
        session.scan(ScannedDish("焼き鳥", "焼き鳥盛り合わせ\u3000", "烤鸡肉串拼盘", 980), ScannedDish(nameOriginal = "  "))
        val menu = session.menu.value
        assertEquals("とり松", menu.placeName)
        assertEquals("ja", menu.sourceLang)
        assertEquals(35.0 to 135.7, menu.location)
        val dish = menu.dishes.single()
        assertEquals("焼き鳥盛り合わせ", dish.key)
        assertEquals(Money(980, "JPY"), dish.price)
        assertNull(dish.dishId)
    }

    @Test
    fun existingPlaceKeepsItsName() {
        val session = newSession(placeId = "p")
        session.apply(ScanUpdate.Started(VisionModelChoice.SAMPLE, placeName = "割烹 みなと"))
        session.apply(ScanUpdate.Header("お品書き", null))
        assertEquals("割烹 みなと", session.menu.value.placeName)
    }

    @Test
    fun savedResolvesDishIdsByKey() {
        val session = newSession()
        session.scan(ScannedDish(nameOriginal = "冷奴", priceMinor = 380), ScannedDish(nameOriginal = "生ビール", priceMinor = 550))
        session.apply(ScanUpdate.Saving)
        assertEquals(MenuStatus.SAVING, session.menu.value.status)
        session.apply(
            ScanUpdate.Saved(ScanResult("p1", "とり松", true, "scan", 2, 2, 0, dishIds = mapOf("冷奴" to "d1", "生ビール" to "d2"))),
        )
        val menu = session.menu.value
        assertEquals(MenuStatus.READY, menu.status)
        assertEquals("p1", menu.placeId)
        assertEquals(listOf("d1", "d2"), menu.dishes.map { it.dishId })
    }

    @Test
    fun quantitiesSurviveRetryAndLinesFollowMenuOrder() {
        val session = newSession()
        session.scan(ScannedDish(nameOriginal = "冷奴", priceMinor = 380), ScannedDish(nameOriginal = "生ビール", priceMinor = 550))
        session.setQuantity("生ビール", 2)
        session.setQuantity("冷奴", 1)
        session.setQuantity("冷奴", 0)
        session.setQuantity("冷奴", 1)
        session.fail(ScanError.NETWORK)
        assertEquals(ScanError.NETWORK, session.menu.value.error)
        // 重试：同名的菜价格更新，份数还在。
        session.scan(ScannedDish(nameOriginal = "生ビール", priceMinor = 600))
        val lines = session.lines()
        assertEquals(listOf("冷奴" to 1, "生ビール" to 2), lines.map { it.dish.key to it.qty })
        assertEquals(Money(600, "JPY"), lines[1].dish.price)
        assertEquals(Money(1580, "JPY"), orderTotal(lines))
        assertNull(session.menu.value.error)
    }

    @Test
    fun quantityIsClampedAndTotalNeedsEveryPrice() {
        val session = newSession()
        session.scan(ScannedDish(nameOriginal = "時価の刺身"), ScannedDish(nameOriginal = "冷奴", priceMinor = 380))
        session.setQuantity("冷奴", 500)
        assertEquals(OrderSession.MAX_QTY, session.quantities.value["冷奴"])
        session.setQuantity("時価の刺身", 1)
        assertNull(orderTotal(session.lines()))
        assertNull(orderTotal(emptyList()))
    }

    @Test
    fun newPhotoClearsDishesAndQuantities() {
        val session = newSession()
        session.scan(ScannedDish(nameOriginal = "冷奴"))
        session.setQuantity("冷奴", 1)
        session.resetForNewPhoto()
        assertTrue(session.menu.value.dishes.isEmpty())
        assertTrue(session.lines().isEmpty())
    }

    private val reminders = FakeReminders()

    private fun placeOrder(drafts: FakeDrafts): PlaceOrderUseCase {
        var n = 0
        val tokyo = TimeZone.of("Asia/Tokyo")
        // 2026-11-17 23:30 东京 = 14:30 UTC：当地日期必须是东京的 17 号。
        val at = LocalDateTime.parse("2026-11-17T23:30").toInstant(tokyo).toEpochMilliseconds()
        return PlaceOrderUseCase(drafts, { "id-${++n}" }, { at }, { tokyo }, reminders)
    }

    private fun readyMenu(placeId: String?) = OrderMenu(
        placeId, "とり松", 35.0 to 135.7, "ja", "JPY",
        listOf(
            OrderDish("生ビール", "生ビール", "生啤", "飲み物", Money(550, "JPY"), listOf("wheat"), "d-beer"),
            OrderDish("冷奴", "冷奴", "", "一品", Money(380, "JPY"), emptyList(), null),
        ),
        MenuStatus.READY,
    )

    @Test
    fun placingOrderCreatesDraftWithSnapshots() = runTest {
        val drafts = FakeDrafts()
        val menu = readyMenu("p")
        val logId = placeOrder(drafts)(menu, orderLines(menu, mapOf("生ビール" to 2, "冷奴" to 1)), linkDishes = true)
        val log = drafts.log!!
        assertNull(drafts.place)
        assertEquals(logId, log.id)
        assertEquals("p", log.placeId)
        assertEquals("Asia/Tokyo", log.timezone)
        assertEquals("2026-11-17", log.localDate)
        assertEquals(listOf("生ビール", "冷奴"), log.items.map { it.name })
        assertEquals(listOf("生啤", null), log.items.map { it.nameTranslated })
        assertEquals(listOf(logId to MEAL_FOLLOW_UP_MINUTES), reminders.meals)
        assertEquals(listOf("d-beer", null), log.items.map { it.dishId })
        assertEquals(listOf(2, 1), log.items.map { it.qty })
        assertEquals(Money(550, "JPY"), log.items[0].price)
    }

    @Test
    fun withoutSavedPlaceCreatesPlaceAndDropsDishLinks() = runTest {
        val drafts = FakeDrafts()
        val menu = readyMenu(null).copy(status = MenuStatus.FAILED)
        placeOrder(drafts)(menu, orderLines(menu, mapOf("生ビール" to 1)), linkDishes = false)
        val place = drafts.place!!
        assertEquals("とり松", place.name)
        assertEquals(35.0, place.lat)
        assertEquals(place.id, drafts.log!!.placeId)
        assertNull(drafts.log!!.items.single().dishId)
    }

    @Test
    fun emptyOrderIsRejected() = runTest {
        assertFailsWith<IllegalArgumentException> { placeOrder(FakeDrafts())(readyMenu("p"), emptyList(), true) }
    }

    @Test
    fun retryIsACommandToTheScanPage() = runTest(UnconfinedTestDispatcher()) {
        val sessions = OrderSessions(IdGenerator { "s1" }, InMemoryOrderSessionRepository())
        val session = sessions.create(OrderMenu(null, "新店铺", null, null, null, emptyList(), MenuStatus.SCANNING))
        session.fail(ScanError.NETWORK)
        // 没有识别页在听：不能重试，状态不变。
        assertEquals(false, session.retry())
        assertEquals(MenuStatus.FAILED, session.menu.value.status)

        var received = 0
        val job = launch { sessions["s1"]!!.retryRequests.collect { received++ } }
        assertTrue(session.retry())
        assertEquals(1, received)
        assertEquals(MenuStatus.SCANNING, session.menu.value.status)
        job.cancel()

        sessions.remove("s1")
        assertNull(sessions["s1"], "进程重建后找不到的会话返回 null")
    }
}
