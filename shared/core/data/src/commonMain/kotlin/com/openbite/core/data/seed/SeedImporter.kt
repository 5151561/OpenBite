package com.openbite.core.data.seed

import com.openbite.core.data.platform.SeedSource
import com.openbite.core.database.OpenBiteDatabase
import com.openbite.core.database.entity.DiningLogEntity
import com.openbite.core.database.entity.DiningLogItemEntity
import com.openbite.core.database.entity.DiningLogPersonEntity
import com.openbite.core.database.entity.DishEntity
import com.openbite.core.database.entity.MenuScanEntity
import com.openbite.core.database.entity.MilestoneEntity
import com.openbite.core.database.entity.MilestonePersonEntity
import com.openbite.core.database.entity.PersonEntity
import com.openbite.core.database.entity.PlaceEntity
import com.openbite.core.database.entity.PlaceTagEntity
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.ExperimentalTime

/** 首次启动导入种子数据，让评委不填 API Key 也能体验全部功能。数据库非空时跳过。 */
class SeedImporter(
    private val db: OpenBiteDatabase,
    private val source: SeedSource,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** 返回是否真的导入了数据。 */
    suspend fun importIfEmpty(): Boolean {
        if (db.placeDao().count() > 0) return false
        val seed = json.decodeFromString<SeedData>(source.read())
        write(seed)
        return true
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun write(seed: SeedData) {
        val now = 0L // 种子数据的 createdAt 固定为 0，方便识别
        db.seedDao().insertAll(
            persons = seed.persons.map { PersonEntity(it.id, it.displayName) },
            milestones = seed.milestones.map {
                MilestoneEntity(it.id, it.title, it.type, it.date, it.recurrence, it.remindDaysBefore)
            },
            milestonePersons = seed.milestones.flatMap { m ->
                m.personIds.map { MilestonePersonEntity(m.id, it) }
            },
            places = seed.places.map { PlaceEntity(it.id, it.name, it.lat, it.lon, it.address, now) },
            placeTags = seed.places.flatMap { p -> p.tags.map { PlaceTagEntity(p.id, it) } },
            menuScans = seed.places.flatMap { p ->
                p.scans.map {
                    MenuScanEntity(
                        id = it.id,
                        placeId = p.id,
                        photoPath = it.photoPath,
                        model = SEED_MODEL,
                        sourceLang = it.sourceLang,
                        targetLang = it.targetLang,
                        rawJson = "{}",
                        // createdAt 就是拍摄时间。
                        createdAt = LocalDateTime.parse(it.takenAtLocal).toInstant(TimeZone.of(it.timezone)).toEpochMilliseconds(),
                    )
                }
            },
            dishes = seed.places.flatMap { p ->
                p.dishes.map {
                    DishEntity(
                        id = it.id,
                        placeId = p.id,
                        scanId = it.scanId,
                        category = it.category,
                        nameOriginal = it.nameOriginal,
                        nameTranslated = it.nameTranslated,
                        priceMinor = it.priceMinor,
                        currency = it.currency,
                        allergensJson = json.encodeToString(it.allergens),
                    )
                }
            },
            logs = seed.logs.map {
                val local = LocalDateTime.parse(it.visitedAtLocal)
                val visitedAt = local.toInstant(TimeZone.of(it.timezone)).toEpochMilliseconds()
                DiningLogEntity(
                    id = it.id,
                    placeId = it.placeId,
                    visitedAt = visitedAt,
                    timezone = it.timezone,
                    localDate = local.date.toString(),
                    status = it.status,
                    milestoneId = it.milestoneId,
                    note = it.note,
                    createdBy = "user",
                    createdAt = now,
                    updatedAt = now,
                )
            },
            logItems = seed.logs.flatMap { log ->
                log.items.mapIndexed { index, item ->
                    DiningLogItemEntity(
                        id = "${log.id}-item-$index",
                        logId = log.id,
                        dishId = item.dishId,
                        nameSnapshot = item.name,
                        priceMinorSnapshot = item.priceMinor,
                        currencySnapshot = item.currency,
                        qty = item.qty,
                        rating = item.rating,
                        comment = item.comment,
                    )
                }
            },
            logPersons = seed.logs.flatMap { log -> log.personIds.map { DiningLogPersonEntity(log.id, it) } },
        )
    }
}

/** 示例菜单的 model 字段，表示“预先识别好、不是现场调模型”。 */
private const val SEED_MODEL = "seed"

@Serializable
internal data class SeedData(
    val persons: List<SeedPerson> = emptyList(),
    val milestones: List<SeedMilestone> = emptyList(),
    val places: List<SeedPlace> = emptyList(),
    val logs: List<SeedLog> = emptyList(),
)

@Serializable
internal data class SeedPerson(val id: String, val displayName: String)

@Serializable
internal data class SeedMilestone(
    val id: String,
    val title: String,
    val type: String,
    val date: String,
    val recurrence: String = "yearly",
    val remindDaysBefore: Int = 7,
    val personIds: List<String> = emptyList(),
)

@Serializable
internal data class SeedPlace(
    val id: String,
    val name: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val address: String? = null,
    val tags: List<String> = emptyList(),
    val scans: List<SeedScan> = emptyList(),
    val dishes: List<SeedDish> = emptyList(),
)

/**
 * 一张菜单原照片。[photoPath] 以 `seed/` 开头，指向打包在 App 里的示例图。
 * 示例图是按设计稿画的手写菜单，不是网上的照片。
 */
@Serializable
internal data class SeedScan(
    val id: String,
    val photoPath: String,
    /** 当地拍摄时间，如 "2026-11-17T19:31"。 */
    val takenAtLocal: String,
    val timezone: String = "Asia/Tokyo",
    val sourceLang: String? = "ja",
    val targetLang: String = "zh-CN",
)

@Serializable
internal data class SeedDish(
    val id: String,
    /** 来自哪张菜单照片（[SeedScan.id]）。 */
    val scanId: String? = null,
    val category: String,
    val nameOriginal: String,
    val nameTranslated: String,
    val priceMinor: Long? = null,
    val currency: String? = null,
    val allergens: List<String> = emptyList(),
)

@Serializable
internal data class SeedLog(
    val id: String,
    val placeId: String,
    /** 当地时间，如 "2025-11-27T19:30"。 */
    val visitedAtLocal: String,
    val timezone: String,
    val status: String = "done",
    val milestoneId: String? = null,
    val note: String? = null,
    val personIds: List<String> = emptyList(),
    val items: List<SeedLogItem> = emptyList(),
)

@Serializable
internal data class SeedLogItem(
    val dishId: String? = null,
    val name: String,
    val priceMinor: Long? = null,
    val currency: String? = null,
    val qty: Int = 1,
    val rating: Int? = null,
    val comment: String? = null,
)
