package com.openbite.feature.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openbite.core.data.PlaceRepository
import com.openbite.core.domain.ExportPlaceUseCase
import com.openbite.core.domain.ExportedFile
import com.openbite.core.domain.ImportError
import com.openbite.core.domain.ImportException
import com.openbite.core.domain.ImportPlaceUseCase
import com.openbite.core.domain.ImportPreview
import com.openbite.core.domain.MealCard
import com.openbite.core.domain.MealDetail
import com.openbite.core.domain.ObserveMealUseCase
import com.openbite.core.domain.OpenBitePackage
import com.openbite.core.domain.OpenSharedPlaceUseCase
import com.openbite.core.domain.ShareOptions
import com.openbite.core.domain.importPreview
import com.openbite.core.domain.mealCard
import com.openbite.core.domain.openBiteFileName
import com.openbite.core.domain.shortArea
import com.openbite.core.model.MenuScan
import com.openbite.core.model.Photo
import com.openbite.core.model.PhotoKind
import com.openbite.core.model.Place
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 分享卡片的底色（设计稿 16 的四个色块）。具体颜色由界面按主题给。 */
enum class CardColor { PRIMARY, PRIMARY_CONTAINER, TERTIARY_CONTAINER, NEUTRAL }

data class ShareMealUiState(
    val isLoading: Boolean = true,
    val missing: Boolean = false,
    /** 可以放进卡片的照片（这一餐的饭菜照片，不含小票）。 */
    val photos: List<Photo> = emptyList(),
    /** null：不放照片。 */
    val photoId: String? = null,
    val color: CardColor = CardColor.PRIMARY,
    val showPlace: Boolean = true,
    /** 金额和人数一起开关（设计稿默认不显示）。 */
    val showAmount: Boolean = false,
    val card: MealCard? = null,
) {
    val photoPath: String? get() = photos.firstOrNull { it.id == photoId }?.path
}

/** 分享这一餐（设计稿 16）。开关只影响这次分享，不存。 */
class ShareMealViewModel(logId: String, observeMeal: ObserveMealUseCase) : ViewModel() {
    private data class Options(val photoId: String?, val photoChosen: Boolean, val color: CardColor, val showPlace: Boolean, val showAmount: Boolean)

    private val options = MutableStateFlow(Options(null, false, CardColor.PRIMARY, showPlace = true, showAmount = false))

    val uiState: StateFlow<ShareMealUiState> = combine(observeMeal(logId), options) { detail: MealDetail?, o ->
        if (detail == null) return@combine ShareMealUiState(isLoading = false, missing = true)
        val photos = detail.log.photos.filter { it.kind == PhotoKind.MEAL }
        // 默认放第一张；用户选过（包括选“不放”）就按用户的。
        val photoId = if (o.photoChosen) o.photoId?.takeIf { id -> photos.any { it.id == id } } else photos.firstOrNull()?.id
        ShareMealUiState(
            isLoading = false,
            photos = photos,
            photoId = photoId,
            color = o.color,
            showPlace = o.showPlace,
            showAmount = o.showAmount,
            card = mealCard(detail.log, o.showPlace, o.showAmount),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShareMealUiState())

    fun selectPhoto(id: String?) = options.update { it.copy(photoId = id, photoChosen = true) }

    fun selectColor(color: CardColor) = options.update { it.copy(color = color) }

    fun setShowPlace(show: Boolean) = options.update { it.copy(showPlace = show) }

    fun setShowAmount(show: Boolean) = options.update { it.copy(showAmount = show) }
}

data class SharePlaceUiState(
    val isLoading: Boolean = true,
    val place: Place? = null,
    val area: String? = null,
    val dishCount: Int = 0,
    val photoCount: Int = 0,
    val options: ShareOptions = ShareOptions(),
    val fileName: String = "",
    val exporting: Boolean = false,
    /** 生成好的文件，界面拿去分享，分享完调 [SharePlaceViewModel.exportHandled]。 */
    val exported: ExportedFile? = null,
    /** “看看对方收到的样子”。 */
    val preview: ImportPreview? = null,
    val failed: Boolean = false,
)

/** 分享店铺（设计稿 17）：只有店铺和菜单，永远不带自己的记录。 */
class SharePlaceViewModel(
    private val placeId: String,
    places: PlaceRepository,
    private val export: ExportPlaceUseCase,
) : ViewModel() {
    private val options = MutableStateFlow(ShareOptions())
    private val transient = MutableStateFlow(SharePlaceUiState())

    val uiState: StateFlow<SharePlaceUiState> = combine(
        places.observePlace(placeId),
        places.observeMenu(placeId),
        places.observeScans(placeId),
        options,
        transient,
    ) { place, menu, scans: List<MenuScan>, o, t ->
        t.copy(
            isLoading = false,
            place = place,
            area = shortArea(place?.address),
            dishCount = menu.size,
            photoCount = scans.size,
            options = o,
            fileName = place?.let { openBiteFileName(it.name) }.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SharePlaceUiState())

    fun setOptions(value: ShareOptions) {
        options.value = value
    }

    fun export() {
        if (transient.value.exporting) return
        transient.update { it.copy(exporting = true, failed = false) }
        viewModelScope.launch {
            val file = runCatching { export(placeId, options.value) }.getOrNull()
            transient.update { it.copy(exporting = false, exported = file, failed = file == null) }
        }
    }

    fun exportHandled() = transient.update { it.copy(exported = null) }

    fun showPreview() {
        viewModelScope.launch {
            val manifest = export.manifest(placeId, options.value) ?: return@launch
            // 照片数按实际会带上的算（manifest 里只有名字）。
            val photos = if (options.value.photos) uiState.value.photoCount.coerceAtMost(10) else 0
            transient.update { it.copy(preview = importPreview(manifest).copy(photoCount = photos)) }
        }
    }

    fun hidePreview() = transient.update { it.copy(preview = null) }
}

enum class ImportPhase { READING, READY, IMPORTING, DONE, FAILED }

data class ImportUiState(
    val phase: ImportPhase = ImportPhase.READING,
    val preview: ImportPreview? = null,
    val error: ImportError? = null,
    val placeId: String? = null,
)

/** 导入店铺（设计稿 18）：先读文件、校验、给预览；点“导入”才写。 */
class ImportViewModel(
    private val uri: String,
    private val open: OpenSharedPlaceUseCase,
    private val importPlace: ImportPlaceUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = state.asStateFlow()
    private var pkg: OpenBitePackage? = null

    init {
        viewModelScope.launch {
            try {
                val opened = open(uri)
                pkg = opened
                state.value = ImportUiState(ImportPhase.READY, importPreview(opened.manifest))
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImportException) {
                state.value = ImportUiState(ImportPhase.FAILED, error = e.error)
            } catch (e: Exception) {
                state.value = ImportUiState(ImportPhase.FAILED, error = ImportError.READ_FAILED)
            }
        }
    }

    fun confirm() {
        val opened = pkg ?: return
        if (state.value.phase != ImportPhase.READY) return
        state.update { it.copy(phase = ImportPhase.IMPORTING) }
        viewModelScope.launch {
            try {
                val id = importPlace(opened)
                state.update { it.copy(phase = ImportPhase.DONE, placeId = id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImportException) {
                state.update { it.copy(phase = ImportPhase.FAILED, error = e.error) }
            } catch (e: Exception) {
                state.update { it.copy(phase = ImportPhase.FAILED, error = ImportError.SAVE_FAILED) }
            }
        }
    }
    /** 已经跳去新店铺页：清掉结果，重组时不再跳第二次。 */
    fun importHandled() = state.update { it.copy(placeId = null) }
}
