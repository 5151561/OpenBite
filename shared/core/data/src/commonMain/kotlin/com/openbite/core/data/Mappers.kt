package com.openbite.core.data

import com.openbite.core.database.entity.DiningLogItemEntity
import com.openbite.core.database.entity.DiningLogWithDetails
import com.openbite.core.database.entity.DishEntity
import com.openbite.core.database.entity.MenuScanEntity
import com.openbite.core.database.entity.PhotoEntity
import com.openbite.core.database.entity.PlaceEntity
import com.openbite.core.database.entity.MilestoneEntity
import com.openbite.core.database.entity.PlaceWithTags
import com.openbite.core.model.DiningLog
import com.openbite.core.model.DiningLogItem
import com.openbite.core.model.DiningStatus
import com.openbite.core.model.Dish
import com.openbite.core.model.MenuScan
import com.openbite.core.model.LogSource
import com.openbite.core.model.Milestone
import com.openbite.core.model.MilestoneType
import com.openbite.core.model.Money
import com.openbite.core.model.Person
import com.openbite.core.model.Photo
import com.openbite.core.model.PhotoKind
import com.openbite.core.model.Place
import com.openbite.core.model.Recurrence
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json

internal fun PlaceWithTags.toModel() = Place(
    id = place.id,
    name = place.name,
    lat = place.lat,
    lon = place.lon,
    address = place.address,
    tags = tags.map { it.tag },
    countryCode = place.countryCode,
)

internal fun DiningLogItemEntity.toModel() = DiningLogItem(
    id = id,
    dishId = dishId,
    name = nameSnapshot,
    price = priceMinorSnapshot?.let { amount -> currencySnapshot?.let { Money(amount, it) } },
    qty = qty,
    rating = rating,
    comment = comment,
    nameTranslated = nameTranslatedSnapshot,
)

internal fun DiningLogWithDetails.toModel() = DiningLog(
    id = log.id,
    place = place.toModel(),
    visitedAt = log.visitedAt,
    timezone = log.timezone,
    localDate = LocalDate.parse(log.localDate),
    status = diningStatusOf(log.status),
    milestoneId = log.milestoneId,
    note = log.note,
    source = LogSource.valueOf(log.createdBy.uppercase()),
    items = items.map { it.toModel() },
    companions = persons.map { Person(it.id, it.displayName) },
    rating = log.rating,
    paidTotal = log.totalMinor?.let { amount -> log.currency?.let { Money(amount, it) } },
    photos = photos.sortedBy { it.takenAt ?: 0L }.map { it.toModel() },
)

internal fun PhotoEntity.toModel() = Photo(
    id = id,
    path = path,
    kind = if (kind == "receipt") PhotoKind.RECEIPT else PhotoKind.MEAL,
    takenAt = takenAt,
)

internal fun MilestoneEntity.toModel() = Milestone(
    id = id,
    title = title,
    type = MilestoneType.valueOf(type.uppercase()),
    date = LocalDate.parse(date),
    recurrence = Recurrence.valueOf(recurrence.uppercase()),
    remindDaysBefore = remindDaysBefore,
)

private val json = Json { ignoreUnknownKeys = true }

internal fun DishEntity.toModel() = Dish(
    id = id,
    placeId = placeId,
    category = category,
    nameOriginal = nameOriginal,
    nameTranslated = nameTranslated,
    price = priceMinor?.let { amount -> currency?.let { Money(amount, it) } },
    allergens = allergensJson?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }.orEmpty(),
    scanId = scanId,
)

internal fun MenuScanEntity.toModel() = MenuScan(
    id = id,
    placeId = placeId,
    photoPath = photoPath,
    takenAt = createdAt,
    sourceLang = sourceLang,
)

internal fun Dish.toEntity() = DishEntity(
    id = id,
    placeId = placeId,
    scanId = scanId,
    category = category,
    nameOriginal = nameOriginal,
    nameTranslated = nameTranslated,
    priceMinor = price?.amountMinor,
    currency = price?.currency,
    allergensJson = json.encodeToString(allergens),
)

internal fun Place.toEntity(createdAt: Long) = PlaceEntity(id, name, lat, lon, address, createdAt, countryCode)

internal fun Milestone.toEntity() = MilestoneEntity(
    id = id,
    title = title,
    type = type.name.lowercase(),
    date = date.toString(),
    recurrence = recurrence.name.lowercase(),
    remindDaysBefore = remindDaysBefore,
)

// dining_log.status 列里存的字符串只出现在这里，对外一律用 [DiningStatus]。
internal fun diningStatusOf(column: String): DiningStatus = DiningStatus.valueOf(column.uppercase())

internal fun DiningStatus.toColumn(): String = name.lowercase()
