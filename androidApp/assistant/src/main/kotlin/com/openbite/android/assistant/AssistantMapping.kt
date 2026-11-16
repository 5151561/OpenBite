package com.openbite.android.assistant

import com.openbite.core.domain.AssistantDish
import com.openbite.core.domain.AssistantMeal
import com.openbite.core.domain.AssistantMenu
import com.openbite.core.domain.AssistantMenuDish
import com.openbite.core.domain.AssistantMilestone
import com.openbite.core.domain.AssistantPlace
import com.openbite.core.model.Money
import kotlinx.datetime.toJavaLocalTime

// AssistantApi 的结果 → AppFunctions 的可序列化类型。只是换类型，不增不减字段。

internal fun Money.toFunction() = MoneyAmount(amountMinor, currency)

internal fun AssistantDish.toFunction() =
    DiningLogDish(dishId, nameOriginal, nameTranslated, qty, price?.toFunction(), rating, comment)

internal fun AssistantMeal.toFunction() = DiningLog(
    logId = logId,
    placeId = placeId,
    placeName = placeName,
    date = localDate.toJava(),
    time = localTime.toJavaLocalTime(),
    isDraft = isDraft,
    createdByAssistant = createdByAssistant,
    milestoneId = milestoneId,
    milestoneTitle = milestoneTitle,
    rating = rating,
    note = note,
    companions = companions,
    dishes = dishes.map { it.toFunction() },
    menuTotal = menuTotal?.toFunction(),
)

internal fun AssistantMilestone.toFunction() = UpcomingMilestone(
    milestoneId = milestoneId,
    title = title,
    type = type.name.lowercase(),
    firstDate = firstDate.toJava(),
    nextDate = nextDate.toJava(),
    daysUntil = daysUntil,
    yearsSince = yearsSince,
    recurring = recurring,
    people = people,
    previousMeals = previousMeals.map { it.toFunction() },
)

internal fun AssistantPlace.toFunction() =
    SavedPlace(placeId, name, area, lat, lon, tags, visitCount, lastVisit?.toJava(), distanceKm)

internal fun AssistantMenuDish.toFunction() =
    MenuDish(dishId, category, nameOriginal, nameTranslated, price?.toFunction(), allergens, timesOrdered, averageRating)

internal fun AssistantMenu.toFunction() = PlaceMenu(place.toFunction(), menuLanguage, dishes.map { it.toFunction() })
