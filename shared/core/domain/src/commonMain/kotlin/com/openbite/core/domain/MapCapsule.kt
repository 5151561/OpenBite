package com.openbite.core.domain

import com.openbite.core.data.DiningLogRepository
import com.openbite.core.data.MilestoneRepository
import com.openbite.core.data.PlaceRepository
import com.openbite.core.model.DiningLog
import com.openbite.core.model.Glyph
import com.openbite.core.model.MemorySummary
import com.openbite.core.model.Milestone
import com.openbite.core.model.MilestoneHighlight
import com.openbite.core.model.Place
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** 地图上的筛选。 */
enum class MapFilter { ALL, MILESTONE, DATE, LATE_NIGHT }

/** 筛选用的店铺标签（和种子数据、设计稿一致）。 */
object MapTags {
    const val DATE = "适合约会"
    const val LATE_NIGHT = "深夜食堂"
}

/** 地图上的一个图钉。只有有经纬度的店铺才有。 */
data class MapPin(
    val place: Place,
    val lat: Double,
    val lon: Double,
    val glyph: Glyph,
    /** 在这里过过纪念日（和店铺列表的“纪念日”标签同一规则）。 */
    val hasMilestoneMeal: Boolean,
    val visitCount: Int,
)

fun mapPins(places: List<Place>, logs: List<DiningLog>): List<MapPin> {
    val byPlace = logs.groupBy { it.place.id }
    return places.mapNotNull { place ->
        val lat = place.lat ?: return@mapNotNull null
        val lon = place.lon ?: return@mapNotNull null
        val visits = byPlace[place.id].orEmpty()
        MapPin(place, lat, lon, placeGlyph(place.name), visits.any { it.milestoneId != null }, visits.size)
    }
}

fun MapPin.matches(filter: MapFilter): Boolean = when (filter) {
    MapFilter.ALL -> true
    MapFilter.MILESTONE -> hasMilestoneMeal
    MapFilter.DATE -> MapTags.DATE in place.tags
    MapFilter.LATE_NIGHT -> MapTags.LATE_NIGHT in place.tags
}

/** 搜索框：店名或标签包含关键词（忽略大小写和首尾空白）。空关键词不过滤。 */
fun MapPin.matches(query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return place.name.contains(q, ignoreCase = true) || place.tags.any { it.contains(q, ignoreCase = true) }
}

fun filterPins(pins: List<MapPin>, filter: MapFilter, query: String): List<MapPin> =
    pins.filter { it.matches(filter) && it.matches(query) }

/** 每个筛选下有几家店（不受搜索词影响），给筛选按钮上的数字用。 */
fun filterCounts(pins: List<MapPin>): Map<MapFilter, Int> = MapFilter.entries.associateWith { f -> pins.count { it.matches(f) } }

enum class CapsuleKind {
    /** 最近要到的纪念日，上一次为它吃的那顿饭。 */
    UPCOMING_MILESTONE,

    /** 选中的店里最近一次纪念日的那顿饭。 */
    PLACE_MILESTONE,

    /** 选中的店里最近一次来吃的。 */
    PLACE_RECENT,
}

/** 地图底部的“时间胶囊”卡片。 */
data class TimeCapsule(
    val kind: CapsuleKind,
    val placeId: String,
    val placeName: String,
    val visitCount: Int,
    val memory: MemorySummary,
    val milestone: Milestone?,
    /** 那顿饭是第几周年（首次那年为 0）。 */
    val milestoneYears: Int,
    /** [CapsuleKind.UPCOMING_MILESTONE] 时：那次纪念日离现在几年（去年是 1）。 */
    val yearsAgo: Int = 0,
)

/** 默认卡片：最近要到、而且上次那顿饭的店在地图上的纪念日。 */
fun defaultCapsule(highlights: List<MilestoneHighlight>, pins: List<MapPin>): TimeCapsule? {
    val pinsById = pins.associateBy { it.place.id }
    val highlight = highlights.firstOrNull { h -> h.previous?.placeId?.let(pinsById::containsKey) == true } ?: return null
    val memory = highlight.previous!!
    val milestone = highlight.upcoming.milestone
    return TimeCapsule(
        kind = CapsuleKind.UPCOMING_MILESTONE,
        placeId = memory.placeId,
        placeName = memory.placeName,
        visitCount = pinsById.getValue(memory.placeId).visitCount,
        memory = memory,
        milestone = milestone,
        milestoneYears = memory.localDate.year - milestone.date.year,
        yearsAgo = (highlight.upcoming.nextDate.year - memory.localDate.year).coerceAtLeast(1),
    )
}

/** 点了某家店：最近一次纪念日那顿；没有就最近一次（草稿也算）。这家店没来吃过返回 null。 */
fun placeCapsule(pin: MapPin, memories: List<MemorySummary>, milestones: List<Milestone>): TimeCapsule? {
    val here = memories.filter { it.placeId == pin.place.id }.sortedByDescending { it.localDate }
    val byId = milestones.associateBy { it.id }
    val milestoneMeal = here.firstOrNull { !it.isDraft && it.milestoneId?.let(byId::containsKey) == true }
    val memory = milestoneMeal ?: here.firstOrNull() ?: return null
    val milestone = memory.milestoneId?.let(byId::get)
    return TimeCapsule(
        kind = if (milestoneMeal != null) CapsuleKind.PLACE_MILESTONE else CapsuleKind.PLACE_RECENT,
        placeId = pin.place.id,
        placeName = pin.place.name,
        visitCount = pin.visitCount,
        memory = memory,
        milestone = milestone,
        milestoneYears = milestone?.let { memory.localDate.year - it.date.year } ?: 0,
    )
}

/**
 * 当前该显示哪张卡片：没选店或选的就是默认卡片那家时显示默认卡片，否则显示选中那家的。
 */
fun capsuleFor(
    selectedPlaceId: String?,
    pins: List<MapPin>,
    memories: List<MemorySummary>,
    milestones: List<Milestone>,
    highlights: List<MilestoneHighlight>,
): TimeCapsule? {
    val default = defaultCapsule(highlights, pins)
    if (selectedPlaceId == null || selectedPlaceId == default?.placeId) return default
    val pin = pins.firstOrNull { it.place.id == selectedPlaceId } ?: return default
    return placeCapsule(pin, memories, milestones)
}

/** 地图要用的全部数据。 */
data class MapData(
    val pins: List<MapPin>,
    val memories: List<MemorySummary>,
    val milestones: List<Milestone>,
    val highlights: List<MilestoneHighlight>,
    /** 没有经纬度、不在地图上的店铺数。 */
    val placesWithoutLocation: Int,
)

class ObserveMapUseCase(
    private val places: PlaceRepository,
    private val logs: DiningLogRepository,
    private val milestones: MilestoneRepository,
    private val memories: ObserveMemoriesUseCase,
    private val highlights: ObserveMilestoneHighlightsUseCase,
) {
    operator fun invoke(): Flow<MapData> = combine(
        places.observePlaces(),
        logs.observeLogs(),
        milestones.observeMilestones(),
        memories(),
        highlights(daysAhead = 400),
    ) { allPlaces, allLogs, allMilestones, allMemories, allHighlights ->
        val pins = mapPins(allPlaces, allLogs)
        MapData(pins, allMemories, allMilestones, allHighlights, allPlaces.size - pins.size)
    }
}

class SetPlaceLocationUseCase(private val places: PlaceRepository) {
    /** 经纬度超出范围返回 false。 */
    suspend operator fun invoke(placeId: String, lat: Double, lon: Double): Boolean {
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return false
        places.setLocation(placeId, lat, lon)
        return true
    }
}

/** 标签：去掉首尾空白、合并空白，最长 [MAX_TAG_LENGTH] 个字。不合法返回 null。 */
fun normalizeTag(raw: String): String? = raw.trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() && it.length <= MAX_TAG_LENGTH }

const val MAX_TAG_LENGTH = 12

class AddPlaceTagUseCase(private val places: PlaceRepository) {
    suspend operator fun invoke(placeId: String, raw: String): Boolean {
        val tag = normalizeTag(raw) ?: return false
        places.addTag(placeId, tag)
        return true
    }
}

class RemovePlaceTagUseCase(private val places: PlaceRepository) {
    suspend operator fun invoke(placeId: String, tag: String) = places.removeTag(placeId, tag)
}

class ObserveAllTagsUseCase(private val places: PlaceRepository) {
    operator fun invoke(): Flow<List<String>> = places.observeAllTags()
}

/** 加标签时的建议：用过的标签里这家店还没有的，加上两个常用的。 */
fun tagSuggestions(allTags: List<String>, current: List<String>): List<String> =
    (allTags + listOf(MapTags.DATE, MapTags.LATE_NIGHT)).distinct().filterNot { it in current }
