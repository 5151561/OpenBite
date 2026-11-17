package com.openbite.feature.meal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openbite.core.model.NewPhoto
import com.openbite.core.domain.AddMealPhotoUseCase
import com.openbite.core.domain.AddPersonUseCase
import com.openbite.core.domain.DiscardPhotosUseCase
import com.openbite.core.domain.MealDetail
import com.openbite.core.domain.NowProvider
import com.openbite.core.domain.ObserveMealUseCase
import com.openbite.core.domain.ReadReceiptUseCase
import com.openbite.core.domain.SaveMealUseCase
import com.openbite.core.domain.SaveReceiptUseCase
import com.openbite.core.domain.TodayProvider
import com.openbite.core.domain.receiptExtra
import com.openbite.core.model.Money
import com.openbite.core.model.PhotoKind
import com.openbite.core.model.ScanError
import com.openbite.core.model.ScanException
import com.openbite.core.model.ModelUse
import com.openbite.core.model.moneyInputText
import com.openbite.core.model.CurrencySource
import com.openbite.core.model.isKnownCurrency
import com.openbite.core.domain.CurrencyPreferencesUseCase
import com.openbite.core.domain.DefaultCurrencyUseCase
import com.openbite.core.domain.LAST_RESORT_CURRENCY
import com.openbite.core.model.parseMoneyInput
import com.openbite.core.model.ScannedReceipt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MealUiState(
    val isLoading: Boolean = true,
    /** 加载完仍为 null：记录已被删掉。 */
    val detail: MealDetail? = null,
    val currentYear: Int = 0,
)

/** 一餐的只读页面（已完成的记录）。 */
class MealViewModel(logId: String, observeMeal: ObserveMealUseCase, today: TodayProvider) : ViewModel() {
    val uiState: StateFlow<MealUiState> = observeMeal(logId)
        .map { MealUiState(false, it, today.today().year) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MealUiState())
}

data class MemoryEditUiState(
    val isLoading: Boolean = true,
    val missing: Boolean = false,
    /** 实时的记录（店名、时间、小票金额会变）。 */
    val detail: MealDetail? = null,
    val editor: MealEditor? = null,
    val addingPhoto: Boolean = false,
    val saving: Boolean = false,
    val photoError: ScanError? = null,
    /** 保存完成：true 是存入记忆，false 是稍后。 */
    val saved: Boolean? = null,
    /** 保存失败了，界面提示一次后调 [MemoryEditViewModel.saveErrorShown]。改动都还在，可以再存。 */
    val saveFailed: Boolean = false,
    val currentYear: Int = 0,
)

/** 补记忆（设计稿 05）。编辑都在内存里，“存入记忆”或“稍后”才写数据库。 */
class MemoryEditViewModel(
    private val logId: String,
    private val observeMeal: ObserveMealUseCase,
    private val saveMeal: SaveMealUseCase,
    private val addPerson: AddPersonUseCase,
    private val addPhoto: AddMealPhotoUseCase,
    private val discardPhotos: DiscardPhotosUseCase,
    private val now: NowProvider,
    today: TodayProvider,
) : ViewModel() {
    private val state = MutableStateFlow(MemoryEditUiState(currentYear = today.today().year))
    val uiState: StateFlow<MemoryEditUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            observeMeal(logId).collect { detail ->
                state.update {
                    when {
                        detail == null -> it.copy(isLoading = false, missing = it.editor == null, detail = null)
                        // 编辑器只在第一次加载时建；之后只刷新实时数据（小票金额、新加的同行人）。
                        it.editor == null -> it.copy(isLoading = false, detail = detail, editor = MealEditor.from(detail.log))
                        else -> it.copy(detail = detail)
                    }
                }
            }
        }
    }

    fun edit(transform: (MealEditor) -> MealEditor) = state.update { s -> s.editor?.let { s.copy(editor = transform(it)) } ?: s }

    fun removePhoto(id: String) {
        val path = state.value.editor?.addedPhotoPath(id)
        edit { it.removePhoto(id) }
        if (path != null) viewModelScope.launch { discardPhotos(listOf(path)) }
    }

    fun addPhoto(sourceUri: String) {
        state.update { it.copy(addingPhoto = true, photoError = null) }
        viewModelScope.launch {
            try {
                val stored = addPhoto(sourceUri, PhotoKind.MEAL)
                state.update { s -> s.copy(addingPhoto = false, editor = s.editor?.addPhoto(stored.photo)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ScanException) {
                state.update { it.copy(addingPhoto = false, photoError = e.error) }
            } catch (e: Exception) {
                state.update { it.copy(addingPhoto = false, photoError = ScanError.UNKNOWN) }
            }
        }
    }

    fun photoErrorShown() = state.update { it.copy(photoError = null) }

    fun saveErrorShown() = state.update { it.copy(saveFailed = false) }

    /** 在纪念日旁边直接加一个同行人，加完就选上。 */
    fun addCompanion(name: String) {
        viewModelScope.launch {
            val person = addPerson(name) ?: return@launch
            edit { it.togglePerson(person.id) }
        }
    }

    /** [done] 为 true：存入记忆；false：稍后（还是草稿，但改动都存下）。 */
    fun save(done: Boolean) {
        val editor = state.value.editor ?: return
        if (state.value.saving) return
        state.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            try {
                saveMeal(editor.toEdit(done, now.nowMillis()), editor.removedPhotos.map { it.path })
                state.update { s -> s.copy(saving = false, saved = done, editor = s.editor?.afterSaving(editor)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }

    /**
     * 保存的是 [saved] 这份快照。保存期间又改了（比如刚好加好一张照片）的部分还没存：
     * 只把快照里的新照片移出“待清理”，有新改动就还算 dirty。
     */
    private fun MealEditor.afterSaving(saved: MealEditor): MealEditor = copy(
        addedPhotos = addedPhotos - saved.addedPhotos.toSet(),
        removedPhotos = removedPhotos - saved.removedPhotos.toSet(),
        dirty = this != saved,
    )

    /** 放弃改动：删掉这次新拍、但没存进去的照片文件。 */
    fun discard() {
        val added = state.value.editor?.addedPhotos.orEmpty().map { it.path }
        edit { it.copy(addedPhotos = emptyList(), dirty = false) }
        discardPhotos.inBackground(added)
    }

    /**
     * 页面不管怎么离开（返回、通知深链清栈）都会走到这里：新拍但没存的照片在应用级作用域里删掉。
     * 正在保存时不删，照片可能已经写进数据库。
     */
    override fun onCleared() {
        val s = state.value
        if (!s.saving) discardPhotos.inBackground(s.editor?.addedPhotos.orEmpty().map { it.path })
    }
}

enum class ReceiptPhase { IDLE, PROCESSING, READING, READ, FAILED, SAVING, SAVED }

data class ReceiptArchiveUiState(
    val isLoading: Boolean = true,
    val missing: Boolean = false,
    val phase: ReceiptPhase = ReceiptPhase.IDLE,
    val placeName: String = "",
    /** 已经存过的小票照片（相对路径），换新照片前显示它。 */
    val savedPhotoPath: String? = null,
    /** 这次新拍的小票（还没存进数据库）。 */
    val newPhoto: NewPhoto? = null,
    val reading: ScannedReceipt? = null,
    val model: ModelUse? = null,
    val error: ScanError? = null,
    /** 实付金额的币种；加载前为 [CurrencyChoice] 的兜底值，界面不显示。 */
    val currency: String = LAST_RESORT_CURRENCY,
    /** 币种从哪里来的（界面显示“来自：…”）；用户自己选的是 MANUAL。 */
    val currencySource: CurrencySource? = null,
    /** 币种选择器里“最近用过”。 */
    val recentCurrencies: List<String> = emptyList(),
    /** 实付金额输入框里的字。 */
    val totalText: String = "",
    /** 按点单算的合计，用来显示“比点单多”。 */
    val orderedTotal: Money? = null,
) {
    val total: Money? get() = parseMoneyInput(totalText, currency)

    /** 比点单多出来的钱（多半是税）。 */
    val extra: Money? get() = receiptExtra(total, orderedTotal)

    /** 输入了字但解析不出来。 */
    val totalInvalid: Boolean get() = totalText.isNotBlank() && total == null
}

/** 小票存档（设计稿 19）：拍店家的纸质小票让模型读金额，或者不拍照只填实付。 */
class ReceiptArchiveViewModel(
    private val logId: String,
    observeMeal: ObserveMealUseCase,
    private val addPhoto: AddMealPhotoUseCase,
    private val readReceipt: ReadReceiptUseCase,
    private val saveReceipt: SaveReceiptUseCase,
    private val discardPhotos: DiscardPhotosUseCase,
    private val defaultCurrency: DefaultCurrencyUseCase,
    private val currencyPrefs: CurrencyPreferencesUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(ReceiptArchiveUiState())
    val uiState: StateFlow<ReceiptArchiveUiState> = combine(state, currencyPrefs.recent()) { s, recent ->
        s.copy(recentCurrencies = recent)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), state.value)

    /** 这次打开页面后用户自己选过币种：小票读出来的不再覆盖它。 */
    private var userPickedCurrency = false
    private var lastBytes: ByteArray? = null
    private var targetLang: String = "zh-CN"

    init {
        viewModelScope.launch {
            val detail = observeMeal(logId).first()
            if (detail == null) {
                state.update { it.copy(isLoading = false, missing = true) }
                return@launch
            }
            val log = detail.log
            val ordered = MealEditor.from(log).orderedTotal
            // 存过的实付 → 点的菜的币种 → 这家店 → 店铺国家 → 备用币种 → 手机地区。
            val choice = defaultCurrency(
                placeId = log.place.id,
                manual = log.paidTotal?.currency,
                menu = ordered?.currency ?: log.items.firstNotNullOfOrNull { item -> item.price?.currency },
            )
            state.update {
                it.copy(
                    isLoading = false,
                    placeName = log.place.name,
                    savedPhotoPath = log.photos.lastOrNull { p -> p.kind == PhotoKind.RECEIPT }?.path,
                    currency = choice.code,
                    currencySource = choice.source,
                    totalText = log.paidTotal?.let(::moneyInputText).orEmpty(),
                    orderedTotal = ordered,
                )
            }
        }
    }

    fun onPhoto(sourceUri: String, lang: String) {
        targetLang = lang
        val previous = state.value.newPhoto
        state.update { it.copy(phase = ReceiptPhase.PROCESSING, error = null) }
        viewModelScope.launch {
            try {
                val stored = addPhoto(sourceUri, PhotoKind.RECEIPT)
                previous?.let { discardPhotos(listOf(it.path)) }
                lastBytes = stored.jpegBytes
                state.update { it.copy(newPhoto = stored.photo) }
                read(stored.jpegBytes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ScanException) {
                state.update { it.copy(phase = ReceiptPhase.FAILED, error = e.error) }
            } catch (e: Exception) {
                state.update { it.copy(phase = ReceiptPhase.FAILED, error = ScanError.UNKNOWN) }
            }
        }
    }

    fun retryReading() {
        val bytes = lastBytes ?: return
        viewModelScope.launch { read(bytes) }
    }

    private suspend fun read(bytes: ByteArray) {
        state.update { it.copy(phase = ReceiptPhase.READING, error = null) }
        try {
            val result = readReceipt(bytes, targetLang)
            val receipt = result.receipt
            state.update {
                val fromReceipt = receipt.currency?.takeIf { code -> !userPickedCurrency && isKnownCurrency(code) }
                val currency = fromReceipt ?: it.currency
                it.copy(
                    phase = ReceiptPhase.READ,
                    reading = receipt,
                    model = result.model,
                    currency = currency,
                    currencySource = if (fromReceipt != null) CurrencySource.RECEIPT else it.currencySource,
                    totalText = receipt.totalMinor?.let { t -> moneyInputText(Money(t, currency)) } ?: it.totalText,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ScanException) {
            // 读不出来也能手动填金额，照片照样存。
            state.update { it.copy(phase = ReceiptPhase.FAILED, error = e.error) }
        } catch (e: Exception) {
            state.update { it.copy(phase = ReceiptPhase.FAILED, error = ScanError.UNKNOWN) }
        }
    }

    fun setTotalText(text: String) = state.update { it.copy(totalText = text) }

    /** 用户在选择器里换了币种。已经填的数字不动，按新币种的小数位重新校验。 */
    fun setCurrency(code: String) {
        if (!isKnownCurrency(code)) return
        userPickedCurrency = true
        state.update { it.copy(currency = code.uppercase(), currencySource = CurrencySource.MANUAL) }
    }

    fun save() {
        val s = state.value
        if (s.totalInvalid || s.phase == ReceiptPhase.SAVING) return
        state.update { it.copy(phase = ReceiptPhase.SAVING) }
        viewModelScope.launch {
            try {
                saveReceipt(logId, s.newPhoto, s.total)
                s.total?.let { currencyPrefs.remember(it.currency) }
                state.update { it.copy(phase = ReceiptPhase.SAVED, newPhoto = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state.update { it.copy(phase = ReceiptPhase.FAILED, error = ScanError.UNKNOWN) }
            }
        }
    }

    /**
     * 不存就走（返回、手势、通知深链清栈都算）：删掉这次新拍的照片。
     * 存好后 newPhoto 已是 null；正在保存时不删，照片可能已经写进数据库。
     */
    override fun onCleared() {
        val s = state.value
        val photo = s.newPhoto ?: return
        if (s.phase != ReceiptPhase.SAVING) discardPhotos.inBackground(listOf(photo.path))
    }
}
