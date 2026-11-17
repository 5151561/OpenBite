package com.openbite.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openbite.core.domain.MapData
import com.openbite.core.domain.MapFilter
import com.openbite.core.domain.MapPin
import com.openbite.core.domain.ObserveMapUseCase
import com.openbite.core.domain.SetPlaceLocationUseCase
import com.openbite.core.domain.TimeCapsule
import com.openbite.core.domain.capsuleFor
import com.openbite.core.domain.visibleSelection
import com.openbite.core.domain.defaultCapsule
import com.openbite.core.domain.filterCounts
import com.openbite.core.domain.filterPins
import com.openbite.core.model.Place
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MapUiState(
    val isLoading: Boolean = true,
    val filter: MapFilter = MapFilter.ALL,
    val query: String = "",
    val counts: Map<MapFilter, Int> = emptyMap(),
    /** 当前筛选下显示的图钉。 */
    val pins: List<MapPin> = emptyList(),
    /** 全部图钉（第一次打开时镜头要框住它们）。 */
    val allPins: List<MapPin> = emptyList(),
    val selectedPlaceId: String? = null,
    val capsule: TimeCapsule? = null,
    val placesWithoutLocation: Int = 0,
)

/**
 * 地图与时间胶囊（设计稿 06）。[focusPlaceId] 不为 null 时（从店铺页“在地图上看”进来）先选中这家店。
 * 否则选中默认卡片（最近的纪念日去年那顿饭）的店。
 */
class MapViewModel(focusPlaceId: String?, observeMap: ObserveMapUseCase) : ViewModel() {
    private val filter = MutableStateFlow(MapFilter.ALL)
    private val query = MutableStateFlow("")
    private val selected = MutableStateFlow(focusPlaceId)
    private var initialized = focusPlaceId != null

    private val data: StateFlow<MapData?> = observeMap().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val uiState: StateFlow<MapUiState> = combine(data, filter, query, selected) { d, f, q, s ->
        if (d == null) return@combine MapUiState(filter = f, query = q)
        if (!initialized) {
            initialized = true
            defaultCapsule(d.highlights, d.pins)?.let { selected.value = it.placeId }
        }
        val visible = filterPins(d.pins, f, q)
        // 选中的店被筛掉时，卡片跟着换成还看得见的店
        val current = visibleSelection(s ?: selected.value, visible, d.highlights)
        MapUiState(
            isLoading = false,
            filter = f,
            query = q,
            counts = filterCounts(d.pins),
            pins = visible,
            allPins = d.pins,
            selectedPlaceId = current,
            capsule = current?.let { capsuleFor(it, visible, d.memories, d.milestones, d.highlights) },
            placesWithoutLocation = d.placesWithoutLocation,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    fun selectFilter(value: MapFilter) {
        filter.value = value
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun selectPlace(placeId: String) {
        selected.value = placeId
    }
}

data class PickLocationUiState(
    val isLoading: Boolean = true,
    val place: Place? = null,
    /** 镜头一开始对准哪里：别的店的图钉，或者京都市中心。 */
    val otherPins: List<MapPin> = emptyList(),
    val saving: Boolean = false,
    val saved: Boolean = false,
)

/** 给没有位置的店铺在地图上选位置（十字准星对准后确认，或长按地图）。 */
class PickLocationViewModel(
    private val placeId: String,
    observeMap: ObserveMapUseCase,
    places: com.openbite.core.data.PlaceRepository,
    private val setLocation: SetPlaceLocationUseCase,
) : ViewModel() {
    private val state = MutableStateFlow(PickLocationUiState())
    val uiState: StateFlow<PickLocationUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val place = places.observePlace(placeId).first()
            val pins = observeMap().first().pins.filterNot { it.place.id == placeId }
            state.update { it.copy(isLoading = false, place = place, otherPins = pins) }
        }
    }

    fun confirm(lat: Double, lon: Double) {
        if (state.value.saving) return
        state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val ok = setLocation(placeId, lat, lon)
            state.update { it.copy(saving = false, saved = ok) }
        }
    }
}
