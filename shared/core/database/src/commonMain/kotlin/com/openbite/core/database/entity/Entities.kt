package com.openbite.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.ForeignKey.Companion.SET_NULL
import androidx.room.Index
import androidx.room.PrimaryKey

// 约定：主键 UUID 字符串；时间存 UTC 毫秒；价格存最小货币单位整数；照片只存相对路径。

@Entity(tableName = "place", indices = [Index("lat", "lon")])
data class PlaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val lat: Double?,
    val lon: Double?,
    val address: String?,
    val createdAt: Long,
)

@Entity(
    tableName = "place_tag",
    primaryKeys = ["placeId", "tag"],
    foreignKeys = [ForeignKey(PlaceEntity::class, ["id"], ["placeId"], onDelete = CASCADE)],
)
data class PlaceTagEntity(
    val placeId: String,
    val tag: String,
)

@Entity(
    tableName = "menu_scan",
    indices = [Index("placeId")],
    foreignKeys = [ForeignKey(PlaceEntity::class, ["id"], ["placeId"], onDelete = CASCADE)],
)
data class MenuScanEntity(
    @PrimaryKey val id: String,
    val placeId: String,
    /** 相对路径，已去 EXIF。 */
    val photoPath: String,
    val model: String,
    val sourceLang: String?,
    val targetLang: String,
    /** 模型原始输出，便于排查和重放。 */
    val rawJson: String,
    val createdAt: Long,
)

@Entity(
    tableName = "dish",
    indices = [Index("placeId"), Index("scanId")],
    foreignKeys = [
        ForeignKey(PlaceEntity::class, ["id"], ["placeId"], onDelete = CASCADE),
        ForeignKey(MenuScanEntity::class, ["id"], ["scanId"], onDelete = SET_NULL),
    ],
)
data class DishEntity(
    @PrimaryKey val id: String,
    val placeId: String,
    val scanId: String?,
    val category: String,
    val nameOriginal: String,
    val nameTranslated: String,
    val priceMinor: Long?,
    /** ISO 4217，如 "JPY"。 */
    val currency: String?,
    /** JSON 数组字符串，如 ["peanut","shellfish"]。 */
    val allergensJson: String?,
)

@Entity(
    tableName = "person",
)
data class PersonEntity(
    @PrimaryKey val id: String,
    val displayName: String,
)

@Entity(tableName = "milestone")
data class MilestoneEntity(
    @PrimaryKey val id: String,
    val title: String,
    /** "anniversary" | "birthday" | "celebration" | "other" */
    val type: String,
    /** 首次发生的日期，"2024-11-27"。 */
    val date: String,
    /** "yearly" | "none" */
    val recurrence: String,
    val remindDaysBefore: Int,
)

@Entity(
    tableName = "milestone_person",
    primaryKeys = ["milestoneId", "personId"],
    indices = [Index("personId")],
    foreignKeys = [
        ForeignKey(MilestoneEntity::class, ["id"], ["milestoneId"], onDelete = CASCADE),
        ForeignKey(PersonEntity::class, ["id"], ["personId"], onDelete = CASCADE),
    ],
)
data class MilestonePersonEntity(val milestoneId: String, val personId: String)

@Entity(
    tableName = "dining_log",
    indices = [Index("placeId"), Index("localDate"), Index("milestoneId"), Index("status")],
    foreignKeys = [
        ForeignKey(PlaceEntity::class, ["id"], ["placeId"], onDelete = CASCADE),
        ForeignKey(MilestoneEntity::class, ["id"], ["milestoneId"], onDelete = SET_NULL),
    ],
)
data class DiningLogEntity(
    @PrimaryKey val id: String,
    val placeId: String,
    /** UTC 毫秒。 */
    val visitedAt: Long,
    /** IANA 时区，"Asia/Tokyo"。 */
    val timezone: String,
    /** 当地日期 "2025-11-27"，纪念日按它匹配。 */
    val localDate: String,
    /** "draft" | "done" */
    val status: String,
    val milestoneId: String?,
    val note: String?,
    /** "user" | "assistant"，区分助手写入的记录。 */
    val createdBy: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "dining_log_item",
    indices = [Index("logId"), Index("dishId")],
    foreignKeys = [
        ForeignKey(DiningLogEntity::class, ["id"], ["logId"], onDelete = CASCADE),
        ForeignKey(DishEntity::class, ["id"], ["dishId"], onDelete = SET_NULL),
    ],
)
data class DiningLogItemEntity(
    @PrimaryKey val id: String,
    val logId: String,
    val dishId: String?,
    val nameSnapshot: String,
    val priceMinorSnapshot: Long?,
    val currencySnapshot: String?,
    val qty: Int,
    /** 1-5 */
    val rating: Int?,
    val comment: String?,
)

@Entity(
    tableName = "dining_log_person",
    primaryKeys = ["logId", "personId"],
    indices = [Index("personId")],
    foreignKeys = [
        ForeignKey(DiningLogEntity::class, ["id"], ["logId"], onDelete = CASCADE),
        ForeignKey(PersonEntity::class, ["id"], ["personId"], onDelete = CASCADE),
    ],
)
data class DiningLogPersonEntity(val logId: String, val personId: String)

@Entity(
    tableName = "photo",
    indices = [Index("logId")],
    foreignKeys = [ForeignKey(DiningLogEntity::class, ["id"], ["logId"], onDelete = CASCADE)],
)
data class PhotoEntity(
    @PrimaryKey val id: String,
    val logId: String,
    /** 相对 App 私有目录的路径，iOS 沙盒绝对路径会变。 */
    val path: String,
    val takenAt: Long?,
)
