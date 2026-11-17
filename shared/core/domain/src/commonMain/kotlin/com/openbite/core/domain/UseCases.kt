package com.openbite.core.domain

import com.openbite.core.data.DiningLogRepository
import com.openbite.core.data.MilestoneRepository
import com.openbite.core.data.PlaceRepository
import com.openbite.core.data.SampleDataRepository
import com.openbite.core.data.platform.PhotoStore
import com.openbite.core.data.platform.TempFiles
import com.openbite.core.model.DiningLog
import com.openbite.core.model.MemorySummary
import com.openbite.core.model.MilestoneHighlight
import com.openbite.core.model.PlaceDetail
import com.openbite.core.model.PlaceMenu
import com.openbite.core.model.PlaceSummary
import com.openbite.core.model.UpcomingMilestone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** 当前的当地日期。测试里换成固定日期。 */
fun interface TodayProvider {
    fun today(): LocalDate
}

@OptIn(ExperimentalTime::class)
class SystemTodayProvider : TodayProvider {
    override fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
}

class ObserveDiningLogsUseCase(private val repository: DiningLogRepository) {
    operator fun invoke(): Flow<List<DiningLog>> = repository.observeLogs()
}

class ObserveUpcomingMilestonesUseCase(
    private val repository: MilestoneRepository,
    private val todayProvider: TodayProvider,
) {
    operator fun invoke(daysAhead: Int = 30): Flow<List<UpcomingMilestone>> =
        repository.observeMilestones().map { upcomingMilestones(it, todayProvider.today(), daysAhead) }
}

/** 全部用餐记忆的摘要，最新的在前。 */
class ObserveMemoriesUseCase(
    private val repository: DiningLogRepository,
    private val todayProvider: TodayProvider,
) {
    operator fun invoke(): Flow<List<MemorySummary>> =
        repository.observeLogs().map { summarize(it, todayProvider.today()) }
}

/** 未来 [daysAhead] 天内的纪念日，每个都带上上一次为它吃的那顿饭。 */
class ObserveMilestoneHighlightsUseCase(
    private val milestones: ObserveUpcomingMilestonesUseCase,
    private val memories: ObserveMemoriesUseCase,
) {
    operator fun invoke(daysAhead: Int): Flow<List<MilestoneHighlight>> =
        combine(milestones(daysAhead), memories()) { upcoming, summaries -> highlight(upcoming, summaries) }
}

/** 载入示例数据。数据库里已有数据时不动，返回 false。 */
class LoadSampleDataUseCase(private val repository: SampleDataRepository) {
    suspend operator fun invoke(): Boolean = repository.loadSampleIfEmpty()
}

/** 清空全部记忆。界面必须先让用户确认。 */
class ClearAllDataUseCase(
    private val repository: SampleDataRepository,
    private val photos: PhotoStore,
    private val tempFiles: TempFiles,
) {
    suspend operator fun invoke() {
        repository.clearAll()
        // 数据库清掉后再删菜单原照片，免得留下孤儿文件。
        photos.deleteAll()
        // 相机原图和分享出去的临时文件也一起清。
        tempFiles.clearAll()
    }
}

/** 店铺列表。 */
class ObservePlacesUseCase(
    private val places: PlaceRepository,
    private val logs: DiningLogRepository,
    private val todayProvider: TodayProvider,
) {
    operator fun invoke(): Flow<List<PlaceSummary>> =
        combine(places.observePlaces(), logs.observeLogs(), places.observeCounts()) { all, allLogs, counts ->
            placeSummaries(all, allLogs, counts, todayProvider.today())
        }
}

/** 店铺详情；店铺被删掉时发 null。 */
class ObservePlaceDetailUseCase(
    private val places: PlaceRepository,
    private val logs: DiningLogRepository,
    private val milestones: MilestoneRepository,
    private val todayProvider: TodayProvider,
) {
    operator fun invoke(placeId: String): Flow<PlaceDetail?> = combine(
        places.observePlace(placeId),
        logs.observeLogs(),
        places.observeMenu(placeId),
        milestones.observeMilestones(),
    ) { place, allLogs, menu, all ->
        place?.let { placeDetail(it, allLogs, menu, all, todayProvider.today()) }
    }
}

/** 电子菜单和原照片；店铺被删掉时发 null。 */
class ObservePlaceMenuUseCase(
    private val places: PlaceRepository,
    private val logs: DiningLogRepository,
) {
    operator fun invoke(placeId: String): Flow<PlaceMenu?> = combine(
        places.observePlace(placeId),
        logs.observeLogs(),
        places.observeMenu(placeId),
        places.observeScans(placeId),
    ) { place, allLogs, menu, scans ->
        place?.let { placeMenu(it, allLogs, menu, scans) }
    }
}
