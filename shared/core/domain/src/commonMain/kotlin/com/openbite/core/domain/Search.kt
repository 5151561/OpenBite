package com.openbite.core.domain

import com.openbite.core.data.DiningLogRepository
import com.openbite.core.data.PlaceRepository
import com.openbite.core.model.MemorySummary
import com.openbite.core.model.Money
import com.openbite.core.model.PlaceSummary
import com.openbite.core.model.parseMoneyInput
import kotlinx.coroutines.flow.first

const val SEARCH_RESULT_LIMIT = 50
const val MAX_QUERY_LENGTH = 50

/**
 * 搜索词 -> SQL LIKE 模式：转义符用反斜杠，先转义反斜杠本身，再转义 % 和 _，两边加 %。
 * 空白（去掉首尾后）返回 null，不搜。
 */
fun likePattern(query: String): String? {
    val q = query.trim().take(MAX_QUERY_LENGTH)
    if (q.isEmpty()) return null
    val escaped = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    return "%$escaped%"
}

/** 搜记忆：店名、菜名、菜的短评、这一餐的短评、同行人、纪念日标题。最新的在前，最多 [SEARCH_RESULT_LIMIT] 条。 */
class SearchMemoriesUseCase(private val logs: DiningLogRepository, private val today: TodayProvider) {
    suspend operator fun invoke(query: String): List<MemorySummary> {
        val pattern = likePattern(query) ?: return emptyList()
        val ids = logs.searchIds(pattern, SEARCH_RESULT_LIMIT)
        if (ids.isEmpty()) return emptyList()
        // 第几次来要按全部记录算，所以先整体做摘要再挑。
        val byId = summarize(logs.observeLogs().first(), today.today()).associateBy { it.logId }
        return ids.mapNotNull(byId::get)
    }
}

/** 搜店铺：店名、地址、标签、菜名。按店铺列表的顺序（最近去过的在前）。 */
class SearchPlacesUseCase(private val places: PlaceRepository, private val list: ObservePlacesUseCase) {
    suspend operator fun invoke(query: String): List<PlaceSummary> {
        val pattern = likePattern(query) ?: return emptyList()
        val ids = places.searchIds(pattern, SEARCH_RESULT_LIMIT).toSet()
        if (ids.isEmpty()) return emptyList()
        return list().first().filter { it.place.id in ids }
    }
}

const val MAX_DISH_NAME = 120
const val MAX_DISH_CATEGORY = 60
private val CURRENCY_CODE = Regex("[A-Z]{3}")

/** 改一道菜时的输入（都是用户打的原始文字）。 */
data class DishEditInput(val nameTranslated: String, val category: String, val priceText: String, val currency: String)

enum class DishEditError { NAME_TOO_LONG, CATEGORY_TOO_LONG, BAD_CURRENCY, BAD_PRICE }

sealed interface DishEditResult {
    data class Valid(val nameTranslated: String, val category: String, val price: Money?) : DishEditResult
    data class Invalid(val error: DishEditError) : DishEditResult
}

/** 校验：价格可以留空（时价）；填了就按币种的小数位解析，币种必须是三个大写字母。 */
fun validateDishEdit(input: DishEditInput): DishEditResult {
    val name = input.nameTranslated.trim()
    val category = input.category.trim()
    val currency = input.currency.trim().uppercase()
    if (name.length > MAX_DISH_NAME) return DishEditResult.Invalid(DishEditError.NAME_TOO_LONG)
    if (category.length > MAX_DISH_CATEGORY) return DishEditResult.Invalid(DishEditError.CATEGORY_TOO_LONG)
    if (input.priceText.isBlank()) return DishEditResult.Valid(name, category, null)
    if (!CURRENCY_CODE.matches(currency)) return DishEditResult.Invalid(DishEditError.BAD_CURRENCY)
    val price = parseMoneyInput(input.priceText, currency) ?: return DishEditResult.Invalid(DishEditError.BAD_PRICE)
    return DishEditResult.Valid(name, category, price)
}

/** 原地改菜品（用餐记录里存的是快照，不受影响）。 */
class UpdateDishUseCase(private val places: PlaceRepository) {
    suspend operator fun invoke(dishId: String, input: DishEditInput): DishEditResult {
        val result = validateDishEdit(input)
        if (result is DishEditResult.Valid) places.updateDish(dishId, result.nameTranslated, result.category, result.price)
        return result
    }
}
