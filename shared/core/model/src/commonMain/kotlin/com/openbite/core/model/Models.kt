package com.openbite.core.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** 价格一律用最小货币单位的整数（1280 日元 = 1280，12.50 美元 = 1250）。 */
@Serializable
data class Money(val amountMinor: Long, val currency: String)

@Serializable
data class Place(
    val id: String,
    val name: String,
    val lat: Double?,
    val lon: Double?,
    val address: String?,
    val tags: List<String> = emptyList(),
)

@Serializable
data class Dish(
    val id: String,
    val placeId: String,
    val category: String,
    val nameOriginal: String,
    val nameTranslated: String,
    val price: Money?,
    /** 模型推测，界面必须标注“仅供参考”。 */
    val allergens: List<String> = emptyList(),
    /** 来自哪次识别（哪张菜单照片）。 */
    val scanId: String? = null,
)

/** 一次菜单识别：一张已去 EXIF 的原照片。 */
@Serializable
data class MenuScan(
    val id: String,
    val placeId: String,
    /**
     * 照片相对路径。`seed/` 开头的是打包在 App 里的示例图（Android 读 assets），
     * 其余是 App 私有目录下的文件。
     */
    val photoPath: String,
    /** 拍摄时间，UTC 毫秒。 */
    val takenAt: Long,
)

@Serializable
enum class DiningStatus { DRAFT, DONE }

@Serializable
enum class LogSource { USER, ASSISTANT }

@Serializable
data class DiningLogItem(
    val id: String,
    val dishId: String?,
    val name: String,
    val price: Money?,
    val qty: Int,
    val rating: Int?,
    val comment: String?,
)

@Serializable
data class DiningLog(
    val id: String,
    val place: Place,
    /** UTC 毫秒时间戳。 */
    val visitedAt: Long,
    /** IANA 时区，如 "Asia/Tokyo"。 */
    val timezone: String,
    /** 当地日期，纪念日按它匹配。 */
    val localDate: LocalDate,
    val status: DiningStatus,
    val milestoneId: String?,
    val note: String?,
    val source: LogSource,
    val items: List<DiningLogItem>,
    /** 同行人。 */
    val companions: List<Person> = emptyList(),
)

@Serializable
enum class MilestoneType { ANNIVERSARY, BIRTHDAY, CELEBRATION, OTHER }

@Serializable
enum class Recurrence { YEARLY, NONE }

@Serializable
data class Milestone(
    val id: String,
    val title: String,
    val type: MilestoneType,
    /** 首次发生的日期。 */
    val date: LocalDate,
    val recurrence: Recurrence,
    val remindDaysBefore: Int = 7,
)

@Serializable
data class Person(val id: String, val displayName: String)

/** 一个即将到来的纪念日。 */
@Serializable
data class UpcomingMilestone(
    val milestone: Milestone,
    val nextDate: LocalDate,
    val daysUntil: Int,
    /** 第几年（首次发生那年为 0）。 */
    val yearsSince: Int,
)
