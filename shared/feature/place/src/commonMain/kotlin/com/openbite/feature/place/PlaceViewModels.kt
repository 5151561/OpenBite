package com.openbite.feature.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openbite.core.domain.AddPlaceTagUseCase
import com.openbite.core.domain.DeletePlaceUseCase
import com.openbite.core.domain.PlaceDeletionSummaryUseCase
import com.openbite.core.domain.DishEditError
import com.openbite.core.domain.DishEditInput
import com.openbite.core.domain.DishEditResult
import com.openbite.core.domain.UpdateDishUseCase
import com.openbite.core.domain.ObserveAllTagsUseCase
import com.openbite.core.domain.ObservePlaceDetailUseCase
import com.openbite.core.domain.RemovePlaceTagUseCase
import com.openbite.core.domain.tagSuggestions
import com.openbite.core.domain.ObservePlaceMenuUseCase
import com.openbite.core.domain.ObservePlacesUseCase
import com.openbite.core.domain.TodayProvider
import com.openbite.core.model.PlaceDeletionSummary
import com.openbite.core.model.PlaceDetail
import com.openbite.core.model.PlaceMenu
import com.openbite.core.model.PlaceSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlacesUiState(
    val isLoading: Boolean = true,
    val places: List<PlaceSummary> = emptyList(),
)

class PlacesViewModel(observePlaces: ObservePlacesUseCase) : ViewModel() {
    val uiState: StateFlow<PlacesUiState> = observePlaces()
        .map { PlacesUiState(isLoading = false, places = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlacesUiState())
}

data class PlaceDetailUiState(
    val isLoading: Boolean = true,
    /** 加载完仍为 null 表示店铺已被删除。 */
    val detail: PlaceDetail? = null,
    val currentYear: Int = 0,
    /** 加标签时的建议：用过的、这家店还没有的。 */
    val tagSuggestions: List<String> = emptyList(),
    /** 不为 null：正在等用户确认删除，里面是会一起删掉多少东西。 */
    val confirmingDelete: PlaceDeletionSummary? = null,
    val deleting: Boolean = false,
    /** 删掉了：界面退回上一页。 */
    val deleted: Boolean = false,
    /** 删除失败，提示一次后调 [PlaceDetailViewModel.deleteErrorShown]。 */
    val deleteFailed: Boolean = false,
)

private data class DeleteState(
    val confirming: PlaceDeletionSummary? = null,
    val deleting: Boolean = false,
    val deleted: Boolean = false,
    val failed: Boolean = false,
)

class PlaceDetailViewModel(
    private val placeId: String,
    observeDetail: ObservePlaceDetailUseCase,
    todayProvider: TodayProvider,
    observeAllTags: ObserveAllTagsUseCase,
    private val addTag: AddPlaceTagUseCase,
    private val removeTag: RemovePlaceTagUseCase,
    private val deletionSummary: PlaceDeletionSummaryUseCase,
    private val deletePlace: DeletePlaceUseCase,
) : ViewModel() {
    private val delete = MutableStateFlow(DeleteState())

    val uiState: StateFlow<PlaceDetailUiState> = combine(observeDetail(placeId), observeAllTags(), delete) { detail, tags, d ->
        PlaceDetailUiState(
            isLoading = false,
            detail = detail,
            currentYear = todayProvider.today().year,
            tagSuggestions = detail?.let { tagSuggestions(tags, it.place.tags) }.orEmpty(),
            confirmingDelete = d.confirming,
            deleting = d.deleting,
            deleted = d.deleted,
            deleteFailed = d.failed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaceDetailUiState())

    /** 加标签：空的或太长返回 false，界面提示。 */
    fun addTag(raw: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch { onResult(addTag(placeId, raw)) }
    }

    fun removeTag(tag: String) {
        viewModelScope.launch { removeTag(placeId, tag) }
    }

    /** 第一步：查出会一起删掉多少东西，弹确认框。 */
    fun requestDelete() {
        if (delete.value.deleting || delete.value.deleted) return
        viewModelScope.launch {
            val summary = deletionSummary(placeId) ?: return@launch
            delete.update { it.copy(confirming = summary) }
        }
    }

    fun cancelDelete() = delete.update { it.copy(confirming = null) }

    /** 第二步：用户确认后才真的删。 */
    fun confirmDelete() {
        if (delete.value.confirming == null || delete.value.deleting) return
        delete.update { it.copy(confirming = null, deleting = true, failed = false) }
        viewModelScope.launch {
            try {
                deletePlace(placeId)
                // 店已经不在（比如别处先删了）也算删掉了，一样退回去。
                delete.update { it.copy(deleting = false, deleted = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                delete.update { it.copy(deleting = false, failed = true) }
            }
        }
    }

    fun deleteErrorShown() = delete.update { it.copy(failed = false) }
}

/** 菜单页的两个 Tab。 */
enum class MenuTab { DIGITAL, PHOTO }

data class PlaceMenuUiState(
    val isLoading: Boolean = true,
    val menu: PlaceMenu? = null,
    val tab: MenuTab = MenuTab.DIGITAL,
    /** 原照片 Tab 正在看第几张（从 0 开始）。 */
    val photoIndex: Int = 0,
    val currentYear: Int = 0,
)

class PlaceMenuViewModel(
    placeId: String,
    initialTab: MenuTab,
    observeMenu: ObservePlaceMenuUseCase,
    todayProvider: TodayProvider,
    private val updateDish: UpdateDishUseCase,
) : ViewModel() {
    private val tab = MutableStateFlow(initialTab)

    /** 改一道菜的译名、分类、价格。[onResult] 收到 null 表示存好了，否则是校验错误。 */
    fun updateDish(dishId: String, input: DishEditInput, onResult: (DishEditError?) -> Unit) {
        viewModelScope.launch {
            when (val result = updateDish.invoke(dishId, input)) {
                is DishEditResult.Valid -> onResult(null)
                is DishEditResult.Invalid -> onResult(result.error)
            }
        }
    }

    private val photoIndex = MutableStateFlow(0)

    val uiState: StateFlow<PlaceMenuUiState> =
        combine(observeMenu(placeId), tab, photoIndex) { menu, selectedTab, index ->
            val lastIndex = (menu?.photos?.size ?: 0) - 1
            PlaceMenuUiState(
                isLoading = false,
                menu = menu,
                tab = selectedTab,
                photoIndex = index.coerceIn(0, lastIndex.coerceAtLeast(0)),
                currentYear = todayProvider.today().year,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaceMenuUiState(tab = initialTab))

    fun selectTab(selected: MenuTab) {
        tab.value = selected
    }

    fun selectPhoto(index: Int) {
        photoIndex.value = index
    }
}
