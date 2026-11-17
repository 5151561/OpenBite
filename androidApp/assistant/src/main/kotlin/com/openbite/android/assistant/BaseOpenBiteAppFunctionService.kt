package com.openbite.android.assistant

import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import com.openbite.core.domain.AssistantApi
import com.openbite.core.domain.AssistantErrorKind
import com.openbite.core.domain.AssistantException
import com.openbite.core.domain.AssistantNewItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import org.koin.android.ext.android.inject
import java.time.LocalDate

/*
 * Android 16+ 的系统助手接口。KSP 按 serviceName 生成具体的 Service（OpenBiteAppFunctionService），
 * 并把函数说明写进 assets/openbite_app_function_service.xml。
 * 这里只做类型转换和错误映射，规则都在 AssistantApi（:shared:core:domain，以后 iOS App Intents 也用它）。
 * 函数上的 KDoc 会原样给助手看，写英文。
 */
@RequiresApi(36)
@AppFunctionServiceEntryPoint(
    serviceName = "OpenBiteAppFunctionService",
    appFunctionXmlFileName = "openbite_app_function_service",
)
abstract class BaseOpenBiteAppFunctionService : AppFunctionService() {
    private val api: AssistantApi by inject()

    /**
     * Lists the user's milestones, such as anniversaries and birthdays, whose next occurrence is
     * within the coming days, soonest first, each with up to 5 earlier meals tagged with it.
     *
     * @param daysAhead How many days to look ahead, counting today, from 1 to 366. If 0, looks 30 days ahead. Negative values are rejected.
     * @return Upcoming milestones, soonest first. Empty if none fall in the range.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun upcomingMilestones(daysAhead: Int = 0): List<UpcomingMilestone> = call {
        upcomingMilestones(daysAhead).map { it.toFunction() }
    }

    /**
     * Searches the user's dining logs, most recent first, at most 50. All filters are optional and
     * combine with AND. If no filter is given, returns the 50 most recent logs.
     *
     * @param milestoneId Only logs tagged with this milestone ID.
     * @param personName Only logs where a companion has exactly this display name, ignoring case, up to 40 characters.
     * @param dateFrom Only logs on or after this local date.
     * @param dateTo Only logs on or before this local date. Must not be before dateFrom.
     * @return Matching dining logs, most recent first. Photos and receipts are not included.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun queryDiningLogs(
        milestoneId: String? = null,
        personName: String? = null,
        dateFrom: LocalDate? = null,
        dateTo: LocalDate? = null,
    ): List<DiningLog> = call {
        queryDiningLogs(milestoneId, personName, dateFrom?.toKotlinLocalDate(), dateTo?.toKotlinLocalDate()).map { it.toFunction() }
    }

    /**
     * Searches the places the user has saved, at most 50. If lat and lon are given, returns only
     * places within radiusKm of that point, nearest first. If tags are given, returns only places
     * that have all of them. Without a point, returns the most recently visited places first.
     *
     * @param lat Latitude of the search point, -90 to 90. Requires lon.
     * @param lon Longitude of the search point, -180 to 180. Requires lat.
     * @param radiusKm Search radius in kilometres, up to 50. If null or 0, uses 2. Negative values are rejected.
     * @param tags Tags the place must all have, for example "适合约会", at most 10, each up to 12 characters.
     * @return Matching places.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun searchPlaces(
        lat: Double? = null,
        lon: Double? = null,
        radiusKm: Double? = null,
        tags: List<String> = emptyList(),
    ): List<SavedPlace> = call {
        searchPlaces(lat, lon, radiusKm, tags).map { it.toFunction() }
    }

    /**
     * Gets the menu of a saved place, in menu order, with how many portions of each dish the user
     * has ordered and the user's average rating.
     *
     * @param placeId ID of the place.
     * @return The place and its menu.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getPlaceMenu(placeId: String): PlaceMenu = call {
        getPlaceMenu(placeId).toFunction()
    }

    /**
     * Creates a new dining log at a saved place, dated now, as a draft marked as created by the
     * assistant. Does not change or delete any existing data.
     *
     * @param placeId ID of the place.
     * @param items Dishes ordered, 1 to 30. Each needs a dishId from that place's menu or a name.
     * @param milestoneId ID of a milestone this meal celebrates, or null.
     * @param note Note on the meal, up to 500 characters, or null.
     * @return The new draft dining log: its ID, place ID and number of dishes.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun createDiningLog(
        placeId: String,
        items: List<NewDiningLogItem>,
        milestoneId: String? = null,
        note: String? = null,
    ): CreatedDiningLog = call {
        val logId = createDiningLog(placeId, items.map { AssistantNewItem(it.dishId, it.name, it.quantity) }, milestoneId, note)
        CreatedDiningLog(logId = logId, placeId = placeId.trim(), dishCount = items.size, isDraft = true)
    }

    /** 在 IO 线程跑，并把 AssistantApi 的错误换成 AppFunctions 的异常；其他异常不外泄细节。 */
    private suspend fun <T> call(block: suspend AssistantApi.() -> T): T = withContext(Dispatchers.IO) {
        try {
            api.block()
        } catch (e: AssistantException) {
            throw when (e.kind) {
                AssistantErrorKind.INVALID_ARGUMENT -> AppFunctionInvalidArgumentException(e.message)
                AssistantErrorKind.NOT_FOUND -> AppFunctionElementNotFoundException(e.message)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AppFunctionAppUnknownException("OpenBite could not read or write its data.")
        }
    }
}

internal fun kotlinx.datetime.LocalDate.toJava(): LocalDate = toJavaLocalDate()
