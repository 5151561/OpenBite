package com.openbite.core.domain

import com.openbite.core.model.OrderLine
import com.openbite.core.model.OrderMenu
import com.openbite.core.model.OrderDish
import com.openbite.core.model.MenuStatus
import com.openbite.core.data.DraftRepository
import com.openbite.core.data.NewDraftItem
import com.openbite.core.data.NewDraftLog
import com.openbite.core.data.platform.PreferencesStore
import com.openbite.core.data.platform.ReminderScheduler
import com.openbite.core.model.Money
import com.openbite.core.model.Place
import com.openbite.core.model.PlaceMenu
import com.openbite.core.model.ScanError
import com.openbite.core.model.TasteProfile
import com.openbite.core.model.ScannedDish
import com.openbite.core.data.InMemoryOrderSessionRepository
import com.openbite.core.data.OrderSessionData
import com.openbite.core.data.OrderSessionRepository
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * 一次点单的句柄：识别中的菜单（或店铺里已存的菜单）+ 每道菜点了几份 + 这次临时改的口味。
 * 状态存在 data 层的 [OrderSessionRepository]（内存）；识别页、点单页、小票页拿同一个 id 共用。
 */
class OrderSession internal constructor(
    val id: String,
    private val state: MutableStateFlow<OrderSessionData>,
    private val repository: OrderSessionRepository,
) {
    val menu: StateFlow<OrderMenu> = state.mapState { it.menu }
    val quantities: StateFlow<Map<String, Int>> = state.mapState { it.quantities }

    /** 这次点单临时改的口味，不改档案；null 表示用档案。 */
    val tasteOverride: StateFlow<TasteProfile?> = state.mapState { it.tasteOverride }

    /** 识别页订阅：收到就重新识别并保存。 */
    val retryRequests: Flow<Unit> get() = repository.retryRequests(id)

    fun updateMenu(transform: (OrderMenu) -> OrderMenu) = state.update { it.copy(menu = transform(it.menu)) }

    fun setQuantity(key: String, qty: Int) = state.update { current ->
        val value = qty.coerceIn(0, MAX_QTY)
        current.copy(quantities = if (value == 0) current.quantities - key else current.quantities + (key to value))
    }

    fun setTasteOverride(profile: TasteProfile?) = state.update { it.copy(tasteOverride = profile) }

    /** 重新拍：换了照片，菜和份数都清掉。 */
    fun resetForNewPhoto() = state.update {
        it.copy(
            menu = it.menu.copy(dishes = emptyList(), status = MenuStatus.SCANNING, error = null, estimatedDishCount = null),
            quantities = emptyMap(),
        )
    }

    /**
     * 让识别页重新识别（发命令，不直接调识别页）。没有识别页在听时返回 false。
     * 发出前先把菜单标成识别中，等菜单的人不会读到旧的“失败”。
     */
    fun retry(): Boolean {
        val before = state.value.menu
        updateMenu { it.copy(status = MenuStatus.SCANNING, error = null) }
        if (repository.requestRetry(id)) return true
        updateMenu { before }
        return false
    }

    /** 点了的菜，按菜单顺序。 */
    fun lines(): List<OrderLine> = orderLines(state.value.menu, state.value.quantities)

    companion object {
        const val MAX_QTY = 99

        /** 测试用：不经过会话表的独立会话。 */
        fun standalone(id: String, initial: OrderMenu): OrderSession {
            val repository = InMemoryOrderSessionRepository()
            return OrderSession(id, repository.create(id, initial), repository)
        }
    }
}

/** 从一个 StateFlow 派生只读视图，不需要协程作用域。 */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private fun <T, R> StateFlow<T>.mapState(transform: (T) -> R): StateFlow<R> = object : StateFlow<R> {
    override val value: R get() = transform(this@mapState.value)
    override val replayCache: List<R> get() = listOf(value)
    override suspend fun collect(collector: FlowCollector<R>): Nothing {
        this@mapState.map(transform).distinctUntilChanged().collect(collector)
        error("StateFlow never completes")
    }
}

fun orderLines(menu: OrderMenu, quantities: Map<String, Int>): List<OrderLine> =
    menu.dishes.mapNotNull { dish -> quantities[dish.key]?.takeIf { it > 0 }?.let { OrderLine(dish, it) } }

/** 合计；有菜没价格或币种不一致时返回 null。 */
fun orderTotal(lines: List<OrderLine>): Money? {
    if (lines.isEmpty()) return null
    val currency = lines.first().dish.price?.currency ?: return null
    if (lines.any { it.dish.price?.currency != currency }) return null
    return Money(lines.sumOf { it.dish.price!!.amountMinor * it.qty }, currency)
}

/** 识别进度写进点单会话。识别重试时旧的菜保留（份数按原名对应），新结果覆盖。 */
fun OrderSession.apply(update: ScanUpdate) = updateMenu { menu ->
    when (update) {
        is ScanUpdate.Started -> menu.copy(
            status = MenuStatus.SCANNING,
            error = null,
            placeName = update.placeName ?: menu.placeName,
        )
        is ScanUpdate.PhotoReady, is ScanUpdate.DishStarted -> menu
        is ScanUpdate.Prepared -> menu.copy(location = update.location ?: menu.location)
        is ScanUpdate.Header -> menu.copy(
            // 已有店铺不跟着菜单抬头改名。
            placeName = if (menu.placeId == null) update.placeName ?: menu.placeName else menu.placeName,
            estimatedDishCount = update.estimatedDishCount,
            currency = update.currency ?: menu.currency,
            sourceLang = update.sourceLang ?: menu.sourceLang,
        )
        is ScanUpdate.DishFound -> menu.copy(dishes = menu.dishes.upsert(update.dish, menu.currency))
        ScanUpdate.Saving -> menu.copy(status = MenuStatus.SAVING)
        is ScanUpdate.Saved -> menu.copy(
            status = MenuStatus.READY,
            placeId = update.result.placeId,
            placeName = update.result.placeName,
            dishes = menu.dishes.map { it.copy(dishId = update.result.dishIds[it.key] ?: it.dishId) },
        )
    }
}

fun OrderSession.fail(error: ScanError) = updateMenu { it.copy(status = MenuStatus.FAILED, error = error) }

private fun List<OrderDish>.upsert(scanned: ScannedDish, defaultCurrency: String?): List<OrderDish> {
    val key = normalizeDishName(scanned.nameOriginal)
    if (key.isEmpty()) return this
    val currency = scanned.currency?.uppercase() ?: defaultCurrency
    val dish = OrderDish(
        key = key,
        nameOriginal = key,
        nameTranslated = scanned.nameTranslated.trim(),
        category = scanned.category.trim(),
        price = scanned.priceMinor?.takeIf { it >= 0 }?.let { amount -> currency?.let { Money(amount, it) } },
        allergens = scanned.allergens,
        dishId = null,
    )
    val index = indexOfFirst { it.key == key }
    return if (index < 0) this + dish else toMutableList().also { it[index] = dish.copy(dishId = this[index].dishId) }
}

/** 从店铺里已存的菜单开始点单（电子菜单页的“用这份菜单点单”）。 */
fun orderMenuFrom(menu: PlaceMenu): OrderMenu = OrderMenu(
    placeId = menu.place.id,
    placeName = menu.place.name,
    location = menu.place.lat?.let { lat -> menu.place.lon?.let { lat to it } },
    sourceLang = menu.photos.maxByOrNull { it.scan.takenAt }?.scan?.sourceLang,
    currency = menu.entries.firstNotNullOfOrNull { it.dish.price?.currency },
    dishes = menu.entries.map {
        OrderDish(
            key = normalizeDishName(it.dish.nameOriginal),
            nameOriginal = it.dish.nameOriginal,
            nameTranslated = it.dish.nameTranslated,
            category = it.dish.category,
            price = it.dish.price,
            allergens = it.dish.allergens,
            dishId = it.dish.id,
        )
    },
    status = MenuStatus.READY,
)

/** 点单会话表（薄用例）：识别页或电子菜单页创建，自己被销毁时移除。状态在 data 层的内存 repository 里。 */
class OrderSessions(private val ids: IdGenerator, private val repository: OrderSessionRepository) {
    fun create(initial: OrderMenu): OrderSession {
        val id = ids.newId()
        return OrderSession(id, repository.create(id, initial), repository)
    }

    /** 进程被杀后返回栈里的 id 会找不到，返回 null。 */
    operator fun get(id: String): OrderSession? = repository[id]?.let { OrderSession(id, it, repository) }

    fun remove(id: String) = repository.remove(id)
}

/** 手机当前时区。测试里换成固定时区。 */
fun interface TimeZoneProvider {
    fun current(): TimeZone
}

class SystemTimeZoneProvider : TimeZoneProvider {
    override fun current(): TimeZone = TimeZone.currentSystemDefault()
}

/**
 * “已下单”：生成一条草稿记录（status=draft、createdBy=user）。
 * - 店铺还没存（新店且识别失败）时一起建店；
 * - [linkDishes] 为 false 或菜还没有 dishId 时只存名字和价格快照；
 * - 名字快照存菜单原文，译名另存一份；
 * - 下单后排一个 90 分钟后的饭后提醒。
 * 返回新记录 id。
 */
/** 下单后多久提醒补记忆。 */
const val MEAL_FOLLOW_UP_MINUTES = 90

class PlaceOrderUseCase(
    private val drafts: DraftRepository,
    private val ids: IdGenerator,
    private val now: NowProvider,
    private val zones: TimeZoneProvider,
    private val reminders: ReminderScheduler,
) {
    @OptIn(ExperimentalTime::class)
    suspend operator fun invoke(menu: OrderMenu, lines: List<OrderLine>, linkDishes: Boolean): String {
        require(lines.isNotEmpty()) { "empty order" }
        val newPlace = if (menu.placeId == null) {
            Place(ids.newId(), menu.placeName, menu.location?.first, menu.location?.second, address = null)
        } else {
            null
        }
        val placeId = menu.placeId ?: newPlace!!.id
        val at = now.nowMillis()
        val zone = zones.current()
        val logId = ids.newId()
        drafts.createDraft(
            newPlace = newPlace,
            log = NewDraftLog(
                id = logId,
                placeId = placeId,
                visitedAt = at,
                timezone = zone.id,
                localDate = Instant.fromEpochMilliseconds(at).toLocalDateTime(zone).date.toString(),
                items = lines.map { line ->
                    NewDraftItem(
                        id = ids.newId(),
                        dishId = if (linkDishes) line.dish.dishId else null,
                        name = line.dish.nameOriginal,
                        nameTranslated = line.dish.nameTranslated.takeIf { it.isNotBlank() },
                        price = line.dish.price,
                        qty = line.qty,
                    )
                },
                createdAt = at,
            ),
        )
        reminders.scheduleMealFollowUp(logId, MEAL_FOLLOW_UP_MINUTES)
        return logId
    }
}

class ObserveTasteProfileUseCase(private val preferences: PreferencesStore) {
    operator fun invoke(): Flow<TasteProfile> = preferences.observeTasteProfile()
}

class SaveTasteProfileUseCase(private val preferences: PreferencesStore) {
    suspend operator fun invoke(profile: TasteProfile) = preferences.setTasteProfile(profile)
}
