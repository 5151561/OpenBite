package com.openbite.core.data

import com.openbite.core.database.entity.DiningLogItemEntity
import com.openbite.core.database.entity.DiningLogWithDetails
import com.openbite.core.database.entity.DishEntity
import com.openbite.core.database.entity.MenuScanEntity
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
)

internal fun DiningLogItemEntity.toModel() = DiningLogItem(
    id = id,
    dishId = dishId,
    name = nameSnapshot,
    price = priceMinorSnapshot?.let { amount -> currencySnapshot?.let { Money(amount, it) } },
    qty = qty,
    rating = rating,
    comment = comment,
)

internal fun DiningLogWithDetails.toModel() = DiningLog(
    id = log.id,
    place = place.toModel(),
    visitedAt = log.visitedAt,
    timezone = log.timezone,
    localDate = LocalDate.parse(log.localDate),
    status = DiningStatus.valueOf(log.status.uppercase()),
    milestoneId = log.milestoneId,
    note = log.note,
    source = LogSource.valueOf(log.createdBy.uppercase()),
    items = items.map { it.toModel() },
    companions = persons.map { Person(it.id, it.displayName) },
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

internal fun Place.toEntity(createdAt: Long) = PlaceEntity(id, name, lat, lon, address, createdAt)
