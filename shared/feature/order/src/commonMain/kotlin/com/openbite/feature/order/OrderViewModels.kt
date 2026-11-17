package com.openbite.feature.order

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openbite.core.domain.MenuStatus
import com.openbite.core.domain.ObservePlaceMenuUseCase
import com.openbite.core.domain.ObserveTasteProfileUseCase
import com.openbite.core.domain.OrderDish
import com.openbite.core.domain.OrderLine
import com.openbite.core.domain.OrderMenu
import com.openbite.core.domain.OrderSession
import com.openbite.core.domain.OrderSessions
import com.openbite.core.domain.PlaceOrderUseCase
import com.openbite.core.domain.Receipt
import com.openbite.core.domain.ReceiptLine
import com.openbite.core.domain.buildReceipt
import com.openbite.core.domain.effectiveTaste
import com.openbite.core.domain.matchedAllergens
import com.openbite.core.domain.orderLines
import com.openbite.core.domain.orderMenuFrom
import com.openbite.core.domain.orderTotal
import com.openbite.core.model.Allergen
import com.openbite.core.model.Money
import com.openbite.core.model.TasteProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 点单页上的一行：菜、份数、和我的过敏原对上的“可能含有”。 */
data class OrderItem(val dish: OrderDish, val qty: Int, val warnings: List<Allergen>)

data class OrderUiState(
    val isLoading: Boolean = true,
    /** 会话不见了（进程被杀过）或店铺没有菜单。 */
    val unavailable: Boolean = false,
    val placeName: String = "",
    val status: MenuStatus = MenuStatus.SCANNING,
    val estimatedDishCount: Int? = null,
    val categories: List<String> = emptyList(),
    /** null：全部。 */
    val category: String? = null,
    val items: List<OrderItem> = emptyList(),
    val profile: TasteProfile = TasteProfile.EMPTY,
    val override: TasteProfile? = null,
    val effectiveTaste: TasteProfile = TasteProfile.EMPTY,
    val total: Money? = null,
    val count: Int = 0,
    val dishCount: Int = 0,
) {
    val visibleItems: List<OrderItem> get() = if (category == null) items else items.filter { it.dish.category == category }
}

/**
 * 点单页（设计稿 03）。[sessionId] 不为 null 时接识别页的会话（边识别边点单）；
 * 否则用 [placeId] 店铺里已存的菜单新开一个会话，并在自己销毁时移除。
 */
@OptIn(ExperimentalCoroutinesApi::class)
/** 点单页的参数：[sessionId] 来自识别页（边识别边点单）；为 null 时用 [placeId] 店铺里存好的菜单。 */
data class OrderArgs(val sessionId: String?, val placeId: String?)

class OrderViewModel(
    sessionId: String?,
    placeId: String?,
    private val sessions: OrderSessions,
    observeMenu: ObservePlaceMenuUseCase,
    observeTaste: ObserveTasteProfileUseCase,
) : ViewModel() {
    private val ownedSessionId = MutableStateFlow<String?>(null)
    private val sessionFlow = MutableStateFlow(sessionId?.let { sessions[it] })
    private val category = MutableStateFlow<String?>(null)
    private val missing = MutableStateFlow(sessionId != null && sessionFlow.value == null)

    /** 小票页要用的会话 id；从店铺菜单开始时等菜单读出来才有。 */
    val sessionId: StateFlow<String?> = sessionFlow.map { it?.id }
        .stateIn(viewModelScope, SharingStarted.Eagerly, sessionFlow.value?.id)

    init {
        if (sessionId == null && placeId != null) {
            viewModelScope.launch {
                val menu = observeMenu(placeId).first()
                if (menu == null || menu.entries.isEmpty()) {
                    missing.value = true
                } else {
                    val created = sessions.create(orderMenuFrom(menu))
                    ownedSessionId.value = created.id
                    sessionFlow.value = created
                }
            }
        } else if (sessionId == null) {
            missing.value = true
        }
    }

    private val taste = observeTaste()

    val uiState: StateFlow<OrderUiState> = combine(sessionFlow, missing, taste, category) { session, gone, profile, selected ->
        Quad(session, gone, profile, selected)
    }.flatMapLatest { (session, gone, profile, selected) ->
        when {
            gone -> flowOf(OrderUiState(isLoading = false, unavailable = true))
            session == null -> flowOf(OrderUiState())
            else -> combine(session.menu, session.quantities, session.tasteOverride) { menu, quantities, override ->
                orderState(menu, quantities, profile, override, selected)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrderUiState())

    fun setQuantity(key: String, qty: Int) {
        sessionFlow.value?.setQuantity(key, qty)
    }

    fun selectCategory(selected: String?) {
        category.value = selected
    }

    /** 这次点单临时改口味；null 恢复成档案。 */
    fun setTasteOverride(profile: TasteProfile?) {
        sessionFlow.value?.setTasteOverride(profile)
    }

    override fun onCleared() {
        ownedSessionId.value?.let(sessions::remove)
    }

    private data class Quad(val session: OrderSession?, val gone: Boolean, val profile: TasteProfile, val category: String?)
}

internal fun orderState(
    menu: OrderMenu,
    quantities: Map<String, Int>,
    profile: TasteProfile,
    override: TasteProfile?,
    category: String?,
): OrderUiState {
    val taste = effectiveTaste(profile, override)
    val lines = orderLines(menu, quantities)
    val categories = menu.dishes.map { it.category }.filter { it.isNotBlank() }.distinct()
    return OrderUiState(
        isLoading = false,
        placeName = menu.placeName,
        status = menu.status,
        estimatedDishCount = menu.estimatedDishCount,
        categories = if (categories.size > 1) categories else emptyList(),
        category = category?.takeIf { it in categories },
        items = menu.dishes.map { OrderItem(it, quantities[it.key] ?: 0, matchedAllergens(it.allergens, taste)) },
        profile = profile,
        override = override,
        effectiveTaste = taste,
        total = orderTotal(lines),
        count = lines.sumOf { it.qty },
        dishCount = menu.dishes.size,
    )
}

enum class PlacePhase {
    IDLE,

    /** 菜单还在识别或保存，等它存好再下单。 */
    WAITING_FOR_MENU,

    /** 识别或保存失败：用户选重试，或者不关联菜单直接记下。 */
    NEEDS_DECISION,
    PLACING,
    PLACED,
    FAILED,
}

data class ReceiptUiState(
    val isLoading: Boolean = true,
    val unavailable: Boolean = false,
    val receipt: Receipt? = null,
    val menuStatus: MenuStatus = MenuStatus.READY,
    val phase: PlacePhase = PlacePhase.IDLE,
    /** 下单成功后的草稿记录 id。 */
    val placedLogId: String? = null,
)

/** “给店员看”（设计稿 04）。点“已下单”才生成草稿记录。 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReceiptViewModel(
    sessionId: String,
    sessions: OrderSessions,
    observeTaste: ObserveTasteProfileUseCase,
    private val placeOrder: PlaceOrderUseCase,
) : ViewModel() {
    private val session: OrderSession? = sessions[sessionId]
    private val phase = MutableStateFlow(PlacePhase.IDLE)
    private val placedLogId = MutableStateFlow<String?>(null)
    private var placing = false

    val uiState: StateFlow<ReceiptUiState> = if (session == null) {
        MutableStateFlow(ReceiptUiState(isLoading = false, unavailable = true))
    } else {
        combine(session.menu, session.quantities, session.tasteOverride, observeTaste(), phase) { menu, quantities, override, profile, p ->
            val lines = orderLines(menu, quantities).map {
                ReceiptLine(it.dish.nameOriginal, it.dish.nameTranslated.takeIf { name -> name.isNotBlank() && name != it.dish.nameOriginal }, it.qty)
            }
            ReceiptUiState(
                isLoading = false,
                receipt = buildReceipt(lines, effectiveTaste(profile, override), menu.sourceLang),
                menuStatus = menu.status,
                phase = p,
            )
        }.combine(placedLogId) { state, logId -> state.copy(placedLogId = logId) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReceiptUiState())
    }

    /** 已下单：菜单存好就直接生成草稿；还在识别就等；失败了让用户选。 */
    fun placeOrder() {
        val session = session ?: return
        if (placing) return
        placing = true
        viewModelScope.launch {
            try {
                phase.value = PlacePhase.WAITING_FOR_MENU
                val menu = session.menu.first { it.status == MenuStatus.READY || it.status == MenuStatus.FAILED }
                if (menu.status == MenuStatus.FAILED) {
                    phase.value = PlacePhase.NEEDS_DECISION
                } else {
                    create(session, linkDishes = true)
                }
            } finally {
                placing = false
            }
        }
    }

    /** 失败后重试识别和保存，存好后接着下单。 */
    fun retrySave() {
        val session = session ?: return
        if (session.retry()) placeOrder() else phase.value = PlacePhase.NEEDS_DECISION
    }

    /** 不关联菜单：只存名字和价格快照（新店会一起建店）。 */
    fun placeWithoutMenu() {
        val session = session ?: return
        if (placing) return
        placing = true
        viewModelScope.launch {
            try {
                create(session, linkDishes = false)
            } finally {
                placing = false
            }
        }
    }

    fun dismissDecision() {
        if (phase.value == PlacePhase.NEEDS_DECISION || phase.value == PlacePhase.FAILED) phase.value = PlacePhase.IDLE
    }

    private suspend fun create(session: OrderSession, linkDishes: Boolean) {
        val lines: List<OrderLine> = session.lines()
        if (lines.isEmpty()) {
            phase.value = PlacePhase.IDLE
            return
        }
        phase.value = PlacePhase.PLACING
        try {
            placedLogId.value = placeOrder(session.menu.value, lines, linkDishes)
            phase.value = PlacePhase.PLACED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            phase.value = PlacePhase.FAILED
        }
    }
}
