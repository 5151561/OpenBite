package com.openbite.feature.place

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openbite.core.domain.AddPlaceTagUseCase
import com.openbite.core.domain.ObserveAllTagsUseCase
import com.openbite.core.domain.ObservePlaceDetailUseCase
import com.openbite.core.domain.RemovePlaceTagUseCase
import com.openbite.core.domain.tagSuggestions
import com.openbite.core.domain.ObservePlaceMenuUseCase
import com.openbite.core.domain.ObservePlacesUseCase
import com.openbite.core.domain.TodayProvider
import com.openbite.core.model.PlaceDetail
import com.openbite.core.model.PlaceMenu
import com.openbite.core.model.PlaceSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
)

class PlaceDetailViewModel(
    private val placeId: String,
    observeDetail: ObservePlaceDetailUseCase,
    todayProvider: TodayProvider,
    observeAllTags: ObserveAllTagsUseCase,
    private val addTag: AddPlaceTagUseCase,
    private val removeTag: RemovePlaceTagUseCase,
) : ViewModel() {
    val uiState: StateFlow<PlaceDetailUiState> = combine(observeDetail(placeId), observeAllTags()) { detail, tags ->
        PlaceDetailUiState(
            isLoading = false,
            detail = detail,
            currentYear = todayProvider.today().year,
            tagSuggestions = detail?.let { tagSuggestions(tags, it.place.tags) }.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaceDetailUiState())

    /** 加标签：空的或太长返回 false，界面提示。 */
    fun addTag(raw: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch { onResult(addTag(placeId, raw)) }
    }

    fun removeTag(tag: String) {
        viewModelScope.launch { removeTag(placeId, tag) }
    }
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
) : ViewModel() {
    private val tab = MutableStateFlow(initialTab)
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
