package com.openbite.android.assistant

import androidx.appfunctions.AppFunctionSerializable
import java.time.LocalDate
import java.time.LocalTime

// AppFunctions 的参数和返回类型。属性的 KDoc 会原样进 AppFunction 元数据给助手看，所以写英文、写清单位。

/** An amount of money from the menu. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MoneyAmount(
    /** Amount in the currency's smallest unit, for example 1280 for 1280 JPY or 1250 for 12.50 USD. */
    val amountMinor: Long,
    /** ISO 4217 currency code, for example "JPY". */
    val currency: String,
)

/** One dish ordered in a dining log. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class DiningLogDish(
    /** ID of the dish on the place's menu, or null if the dish is not linked to the menu. */
    val dishId: String?,
    /** Dish name as written on the menu. */
    val nameOriginal: String,
    /** Translated dish name, or null if there is none. */
    val nameTranslated: String?,
    /** Number of portions ordered. */
    val quantity: Int,
    /** Menu price of one portion, or null if unknown. */
    val price: MoneyAmount?,
    /** User's rating of the dish from 1 to 5, or null if not rated. */
    val rating: Int?,
    /** User's short comment on the dish, or null. */
    val comment: String?,
)

/** A meal the user ate at a place. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class DiningLog(
    /** ID of the dining log. */
    val logId: String,
    /** ID of the place where the meal was eaten. */
    val placeId: String,
    /** Name of the place. */
    val placeName: String,
    /** Local date of the meal at the place. */
    val date: LocalDate,
    /** Local time of the meal at the place. */
    val time: LocalTime,
    /** True if the user has not finished filling in this log yet. */
    val isDraft: Boolean,
    /** True if this log was created through createDiningLog rather than in the app. */
    val createdByAssistant: Boolean,
    /** ID of the milestone this meal celebrated, or null. */
    val milestoneId: String?,
    /** Title of the milestone this meal celebrated, or null. */
    val milestoneTitle: String?,
    /** Rating of the meal from 1 to 5; the average dish rating if the meal itself is not rated; null if nothing is rated. */
    val rating: Double?,
    /** User's note on the meal, or null. */
    val note: String?,
    /** Display names of the people the user ate with. */
    val companions: List<String>,
    /** Dishes ordered. */
    val dishes: List<DiningLogDish>,
    /** Sum of menu prices times quantities; null if a price is missing or the currencies differ. */
    val menuTotal: MoneyAmount?,
)

/** A milestone, such as an anniversary or a birthday, that falls within the requested days. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class UpcomingMilestone(
    /** ID of the milestone. */
    val milestoneId: String,
    /** Title of the milestone, for example "恋爱纪念日". */
    val title: String,
    /** Kind of milestone: "anniversary", "birthday", "celebration" or "other". */
    val type: String,
    /** Date the milestone first happened. */
    val firstDate: LocalDate,
    /** Date of the next occurrence, today or later. */
    val nextDate: LocalDate,
    /** Days from today to nextDate; 0 means today. */
    val daysUntil: Int,
    /** Full years from firstDate to nextDate; 0 in the first year. */
    val yearsSince: Int,
    /** True if the milestone repeats every year. */
    val recurring: Boolean,
    /** Display names of the people the milestone is about. */
    val people: List<String>,
    /** Up to 5 earlier meals tagged with this milestone, most recent first. */
    val previousMeals: List<DiningLog>,
)

/** A restaurant, cafe or other place the user has saved. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class SavedPlace(
    /** ID of the place. */
    val placeId: String,
    /** Name of the place. */
    val name: String,
    /** Area of the place down to the city or ward, or null if unknown. */
    val area: String?,
    /** Latitude of the place, or null if unknown. */
    val latitude: Double?,
    /** Longitude of the place, or null if unknown. */
    val longitude: Double?,
    /** Tags the user gave the place, for example "适合约会". */
    val tags: List<String>,
    /** Number of dining logs at this place. */
    val visitCount: Int,
    /** Local date of the most recent dining log at this place, or null if never logged. */
    val lastVisit: LocalDate?,
    /** Distance in kilometres from the requested point; null if no point was given or the place has no location. */
    val distanceKm: Double?,
)

/** One dish on a place's menu. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MenuDish(
    /** ID of the dish, used as dishId in createDiningLog. */
    val dishId: String,
    /** Menu section, for example "刺身". */
    val category: String,
    /** Dish name as written on the menu. */
    val nameOriginal: String,
    /** Translated dish name. */
    val nameTranslated: String,
    /** Menu price of one portion, or null if unknown. */
    val price: MoneyAmount?,
    /** Allergen codes guessed when the menu was read, for example "shellfish". Not verified. */
    val allergens: List<String>,
    /** Total portions of this dish across the user's dining logs. */
    val timesOrdered: Int,
    /** Average user rating of this dish from 1 to 5, or null if never rated. */
    val averageRating: Double?,
)

/** A place and its menu. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class PlaceMenu(
    /** The place. */
    val place: SavedPlace,
    /** BCP 47 language of the original menu, for example "ja", or null if unknown. */
    val menuLanguage: String?,
    /** Dishes in menu order, at most 300. */
    val dishes: List<MenuDish>,
)

/** A dish to put in a new dining log. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class NewDiningLogItem(
    /** ID of a dish from the place's menu. If null, name is used instead. */
    val dishId: String? = null,
    /** Dish name for a dish that is not on the menu, up to 120 characters. Ignored when dishId is set. */
    val name: String? = null,
    /** Number of portions, 1 to 20. */
    val quantity: Int,
)

/** The dining log that was created. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class CreatedDiningLog(
    /** ID of the new dining log. */
    val logId: String,
    /** ID of the place the log belongs to. */
    val placeId: String,
    /** Number of dishes in the new log. */
    val dishCount: Int,
    /** Always true: the log is saved as a draft that the user finishes in the app. */
    val isDraft: Boolean,
)
