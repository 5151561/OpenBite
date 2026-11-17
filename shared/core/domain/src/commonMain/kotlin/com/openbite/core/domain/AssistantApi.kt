package com.openbite.core.domain

import com.openbite.core.data.DiningLogRepository
import com.openbite.core.data.DraftRepository
import com.openbite.core.data.MealRepository
import com.openbite.core.data.MilestoneRepository
import com.openbite.core.data.NewDraftItem
import com.openbite.core.data.NewDraftLog
import com.openbite.core.data.PlaceRepository
import com.openbite.core.model.DiningLog
import com.openbite.core.model.DiningStatus
import com.openbite.core.model.Milestone
import com.openbite.core.model.MilestoneType
import com.openbite.core.model.Money
import com.openbite.core.model.Place
import com.openbite.core.model.Recurrence
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/*
 * 给手机上的 AI 助手用的接口（Android：AppFunctions；以后 iOS：App Intents）。
 * 4 个只读，1 个只追加；不改、不删任何已有数据。
 * 返回值里没有照片、文件路径、小票（照片和实付金额）、API Key。所有输入都校验，结果有条数上限。
 */

const val ASSISTANT_MAX_LOGS = 50
const val ASSISTANT_MAX_PLACES = 50
const val ASSISTANT_MAX_DISHES = 300
const val ASSISTANT_MAX_ITEMS = 30
const val ASSISTANT_MAX_QTY = 20
const val ASSISTANT_MAX_NOTE = 500
const val ASSISTANT_DEFAULT_DAYS = 30
const val ASSISTANT_MAX_DAYS = 366
const val ASSISTANT_DEFAULT_RADIUS_KM = 2.0
const val ASSISTANT_MAX_RADIUS_KM = 50.0
private const val PREVIOUS_MEALS_PER_MILESTONE = 5

enum class AssistantErrorKind { INVALID_ARGUMENT, NOT_FOUND }

class AssistantException(val kind: AssistantErrorKind, message: String) : Exception(message)

private fun invalidArg(message: String): Nothing = throw AssistantException(AssistantErrorKind.INVALID_ARGUMENT, message)
private fun notFound(message: String): Nothing = throw AssistantException(AssistantErrorKind.NOT_FOUND, message)

data class AssistantDish(
    val dishId: String?,
    val nameOriginal: String,
    val nameTranslated: String?,
    val qty: Int,
    /** 菜单价格（小票上的实付不返回）。 */
    val price: Money?,
    val rating: Int?,
    val comment: String?,
)

data class AssistantMeal(
    val logId: String,
    val placeId: String,
    val placeName: String,
    val localDate: LocalDate,
    val localTime: LocalTime,
    val isDraft: Boolean,
    val createdByAssistant: Boolean,
    val milestoneId: String?,
    val milestoneTitle: String?,
    /** 这一餐的评分：总评分，没有就是菜品平均分。 */
    val rating: Double?,
    val note: String?,
    val companions: List<String>,
    val dishes: List<AssistantDish>,
    /** 按菜单价格算的合计，缺价格或币种不一时为 null。 */
    val menuTotal: Money?,
)

data class AssistantMilestone(
    val milestoneId: String,
    val title: String,
    val type: MilestoneType,
    val firstDate: LocalDate,
    val nextDate: LocalDate,
    val daysUntil: Int,
    val yearsSince: Int,
    val recurring: Boolean,
    val people: List<String>,
    /** 以前为这个纪念日吃的饭，最近的在前，最多 5 顿。 */
    val previousMeals: List<AssistantMeal>,
)

data class AssistantPlace(
    val placeId: String,
    val name: String,
    /** 只到区一级。 */
    val area: String?,
    val lat: Double?,
    val lon: Double?,
    val tags: List<String>,
    val visitCount: Int,
    val lastVisit: LocalDate?,
    /** 给了位置才有：离那个点多少公里。 */
    val distanceKm: Double?,
)

data class AssistantMenuDish(
    val dishId: String,
    val category: String,
    val nameOriginal: String,
    val nameTranslated: String,
    val price: Money?,
    /** 模型推测的过敏原代码，仅供参考。 */
    val allergens: List<String>,
    val timesOrdered: Int,
    val averageRating: Double?,
)

data class AssistantMenu(val place: AssistantPlace, val menuLanguage: String?, val dishes: List<AssistantMenuDish>)

/** 新记录里的一道菜：[dishId] 是这家店菜单里的菜；没有菜单 id 时用 [name]。 */
data class AssistantNewItem(val dishId: String?, val name: String?, val qty: Int)

/** 两点间的球面距离（公里）。 */
fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = (lat2 - lat1) * PI / 180
    val dLon = (lon2 - lon1) * PI / 180
    val a = sin(dLat / 2) * sin(dLat / 2) + cos(lat1 * PI / 180) * cos(lat2 * PI / 180) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * asin(min(1.0, sqrt(a)))
}

class AssistantApi(
    private val logs: DiningLogRepository,
    private val places: PlaceRepository,
    private val milestones: MilestoneRepository,
    private val drafts: DraftRepository,
    /** 只用来读同行人名字。 */
    private val persons: MealRepository,
    private val today: TodayProvider,
    private val ids: IdGenerator,
    private val now: NowProvider,
    private val zones: TimeZoneProvider,
) {
    /** 未来 [daysAhead] 天内（含今天）的纪念日，每个带上以前为它吃的饭。[daysAhead] 为 0 时按 30 天，负数是错误。 */
    // iOS（Swift）只能接住 @Throws 里列出的异常，别的会让进程直接终止。
    @Throws(AssistantException::class, CancellationException::class)
    suspend fun upcomingMilestones(daysAhead: Int): List<AssistantMilestone> {
        if (daysAhead < 0) invalidArg("daysAhead must be between 1 and $ASSISTANT_MAX_DAYS, or 0 for the default.")
        val days = if (daysAhead == 0) ASSISTANT_DEFAULT_DAYS else daysAhead
        if (days > ASSISTANT_MAX_DAYS) invalidArg("daysAhead must be between 1 and $ASSISTANT_MAX_DAYS.")
        val all = milestones.observeMilestones().first()
        val allLogs = logs.observeLogs().first()
        val names = persons.observePersons().first().associate { it.id to it.displayName }
        return upcomingMilestones(all, today.today(), days).map { up ->
            val m = up.milestone
            val people = milestones.milestone(m.id)?.personIds.orEmpty()
            AssistantMilestone(
                milestoneId = m.id,
                title = m.title,
                type = m.type,
                firstDate = m.date,
                nextDate = up.nextDate,
                daysUntil = up.daysUntil,
                yearsSince = up.yearsSince,
                recurring = m.recurrence == Recurrence.YEARLY,
                people = people.mapNotNull(names::get),
                previousMeals = allLogs.filter { it.milestoneId == m.id && it.localDate < up.nextDate }
                    .sortedByDescending { it.visitedAt }
                    .take(PREVIOUS_MEALS_PER_MILESTONE)
                    .map { it.toAssistant(all) },
            )
        }
    }

    /**
     * 查用餐记录，最新的在前，最多 50 条。条件都是可选的，同时给就要同时满足：
     * [milestoneId] 标了这个纪念日；[personName] 同行人名字（不分大小写、完全一致）；[dateFrom]..[dateTo] 当地日期（含两端）。
     */
    // iOS（Swift）只能接住 @Throws 里列出的异常，别的会让进程直接终止。
    @Throws(AssistantException::class, CancellationException::class)
    suspend fun queryDiningLogs(milestoneId: String?, personName: String?, dateFrom: LocalDate?, dateTo: LocalDate?): List<AssistantMeal> {
        if (dateFrom != null && dateTo != null && dateFrom > dateTo) invalidArg("dateFrom must not be after dateTo.")
        val all = milestones.observeMilestones().first()
        val milestone = milestoneId?.trim()?.takeIf { it.isNotEmpty() }
        if (milestone != null && all.none { it.id == milestone }) notFound("No milestone with id $milestone.")
        val person = personName?.trim()?.takeIf { it.isNotEmpty() }
        if (person != null && person.length > 40) invalidArg("personName is too long.")
        return logs.observeLogs().first()
            .asSequence()
            .filter { milestone == null || it.milestoneId == milestone }
            .filter { person == null || it.companions.any { c -> c.displayName.equals(person, ignoreCase = true) } }
            .filter { dateFrom == null || it.localDate >= dateFrom }
            .filter { dateTo == null || it.localDate <= dateTo }
            .sortedByDescending { it.visitedAt }
            .take(ASSISTANT_MAX_LOGS)
            .map { it.toAssistant(all) }
            .toList()
    }

    /**
     * 找店铺。给了 [lat]/[lon]（两个都要给）就只返回 [radiusKm] 公里内的、近的在前（默认 2 公里，最多 50）；
     * 给了 [tags] 就只返回带全部这些标签的。都不给时返回最近去过的店。最多 50 家。
     */
    // iOS（Swift）只能接住 @Throws 里列出的异常，别的会让进程直接终止。
    @Throws(AssistantException::class, CancellationException::class)
    suspend fun searchPlaces(lat: Double?, lon: Double?, radiusKm: Double?, tags: List<String>): List<AssistantPlace> {
        if ((lat == null) != (lon == null)) invalidArg("lat and lon must be given together.")
        if (lat != null && (!lat.isFinite() || lat !in -90.0..90.0)) invalidArg("lat must be between -90 and 90.")
        if (lon != null && (!lon.isFinite() || lon !in -180.0..180.0)) invalidArg("lon must be between -180 and 180.")
        // 只有 null 和 0 走默认值；负数、NaN、无穷大是传错了，告诉助手而不是悄悄换成 2 公里。
        if (radiusKm != null && (radiusKm.isNaN() || radiusKm < 0)) invalidArg("radiusKm must be positive, or null or 0 for the default.")
        val radius = radiusKm?.takeIf { it > 0 } ?: ASSISTANT_DEFAULT_RADIUS_KM
        if (!radius.isFinite() || radius > ASSISTANT_MAX_RADIUS_KM) invalidArg("radiusKm must be at most $ASSISTANT_MAX_RADIUS_KM.")
        if (tags.size > 10) invalidArg("At most 10 tags.")
        if (tags.any { it.trim().length > MAX_TAG_LENGTH }) invalidArg("Each tag must be at most $MAX_TAG_LENGTH characters.")
        val wanted = tags.map { it.trim() }.filter { it.isNotEmpty() }
        val allLogs = logs.observeLogs().first()
        val results = places.observePlaces().first()
            .filter { place -> wanted.all { it in place.tags } }
            .map { it.toAssistant(allLogs, lat, lon) }
            .filter { lat == null || (it.distanceKm != null && it.distanceKm <= radius) }
        val sorted = if (lat != null) {
            results.sortedBy { it.distanceKm }
        } else {
            results.sortedWith(compareByDescending<AssistantPlace> { it.lastVisit }.thenBy { it.name })
        }
        return sorted.take(ASSISTANT_MAX_PLACES)
    }

    /** 一家店的电子菜单（最多 300 道），每道菜带点过几次和平均分。 */
    // iOS（Swift）只能接住 @Throws 里列出的异常，别的会让进程直接终止。
    @Throws(AssistantException::class, CancellationException::class)
    suspend fun getPlaceMenu(placeId: String): AssistantMenu {
        val id = placeId.trim().takeIf { it.isNotEmpty() } ?: invalidArg("placeId is required.")
        val place = places.observePlace(id).first() ?: notFound("No place with id $id.")
        val allLogs = logs.observeLogs().first()
        val menu = places.observeMenu(id).first()
        val stats = dishStats(allLogs.filter { it.place.id == id }, menu).filter { it.dishId != null }.associateBy { it.dishId }
        val language = places.observeScans(id).first().maxByOrNull { it.takenAt }?.sourceLang
        return AssistantMenu(
            place = place.toAssistant(allLogs, null, null),
            menuLanguage = language,
            dishes = menu.take(ASSISTANT_MAX_DISHES).map { dish ->
                val s = stats[dish.id]
                AssistantMenuDish(dish.id, dish.category, dish.nameOriginal, dish.nameTranslated, dish.price, dish.allergens, s?.timesOrdered ?: 0, s?.averageRating)
            },
        )
    }

    /**
     * 新增一条用餐记录（草稿，标成助手写的），时间是现在。只追加，不改任何已有记录。返回新记录 id。
     * [items] 1–30 道，每道 1–20 份；[dishId] 必须是这家店菜单里的菜。
     */
    @OptIn(ExperimentalTime::class)
    // iOS（Swift）只能接住 @Throws 里列出的异常，别的会让进程直接终止。
    @Throws(AssistantException::class, CancellationException::class)
    suspend fun createDiningLog(placeId: String, items: List<AssistantNewItem>, milestoneId: String?, note: String?): String {
        val id = placeId.trim().takeIf { it.isNotEmpty() } ?: invalidArg("placeId is required.")
        places.observePlace(id).first() ?: notFound("No place with id $id.")
        if (items.isEmpty()) invalidArg("At least one item is required.")
        if (items.size > ASSISTANT_MAX_ITEMS) invalidArg("At most $ASSISTANT_MAX_ITEMS items.")
        val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanNote != null && cleanNote.length > ASSISTANT_MAX_NOTE) invalidArg("note is longer than $ASSISTANT_MAX_NOTE characters.")
        if (cleanNote != null && cleanNote.any { it.code < 0x20 && it != '\n' }) invalidArg("note contains control characters.")
        val milestone = milestoneId?.trim()?.takeIf { it.isNotEmpty() }
        if (milestone != null && milestones.milestone(milestone) == null) notFound("No milestone with id $milestone.")
        val menu = places.observeMenu(id).first().associateBy { it.id }
        val draftItems = items.map { item ->
            if (item.qty !in 1..ASSISTANT_MAX_QTY) invalidArg("qty must be between 1 and $ASSISTANT_MAX_QTY.")
            val dishId = item.dishId?.trim()?.takeIf { it.isNotEmpty() }
            if (dishId != null) {
                val dish = menu[dishId] ?: notFound("Dish $dishId is not on this place's menu.")
                NewDraftItem(ids.newId(), dish.id, dish.nameOriginal, dish.nameTranslated.takeIf { it.isNotBlank() }, dish.price, item.qty)
            } else {
                val name = item.name?.trim()?.takeIf { it.isNotEmpty() } ?: invalidArg("Each item needs a dishId or a name.")
                if (name.length > MAX_DISH_NAME || name.any { it.code < 0x20 }) invalidArg("Item name is invalid.")
                NewDraftItem(ids.newId(), null, name, null, null, item.qty)
            }
        }
        val at = now.nowMillis()
        val zone = zones.current()
        val logId = ids.newId()
        drafts.createDraft(
            newPlace = null,
            log = NewDraftLog(
                id = logId,
                placeId = id,
                visitedAt = at,
                timezone = zone.id,
                localDate = Instant.fromEpochMilliseconds(at).toLocalDateTime(zone).date.toString(),
                items = draftItems,
                createdAt = at,
                milestoneId = milestone,
                note = cleanNote,
                byAssistant = true,
            ),
        )
        return logId
    }

    @OptIn(ExperimentalTime::class)
    private fun DiningLog.toAssistant(allMilestones: List<Milestone>): AssistantMeal {
        val zone = runCatching { TimeZone.of(timezone) }.getOrDefault(TimeZone.UTC)
        return AssistantMeal(
            logId = id,
            placeId = place.id,
            placeName = place.name,
            localDate = localDate,
            localTime = Instant.fromEpochMilliseconds(visitedAt).toLocalDateTime(zone).time,
            isDraft = status == DiningStatus.DRAFT,
            createdByAssistant = source == com.openbite.core.model.LogSource.ASSISTANT,
            milestoneId = milestoneId,
            milestoneTitle = allMilestones.firstOrNull { it.id == milestoneId }?.title,
            rating = mealRating(this),
            note = note,
            companions = companions.map { it.displayName },
            dishes = items.map { AssistantDish(it.dishId, it.name, it.nameTranslated, it.qty, it.price, it.rating, it.comment) },
            menuTotal = totalOf(this),
        )
    }

    private fun Place.toAssistant(allLogs: List<DiningLog>, fromLat: Double?, fromLon: Double?): AssistantPlace {
        val visits = allLogs.filter { it.place.id == id }
        val placeLat = lat
        val placeLon = lon
        val distance = if (fromLat != null && fromLon != null && placeLat != null && placeLon != null) {
            distanceKm(fromLat, fromLon, placeLat, placeLon)
        } else {
            null
        }
        return AssistantPlace(id, name, shortArea(address), lat, lon, tags, visits.size, visits.maxOfOrNull { it.localDate }, distance)
    }
}
