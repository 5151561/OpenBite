package com.openbite.core.model

/** 点单菜单的状态：还在识别 / 正在存 / 已存好（有 dishId）/ 识别或保存失败。 */
enum class MenuStatus { SCANNING, SAVING, READY, FAILED }

/** 点单页上的一道菜。[key] 是 [normalizeDishName] 后的原名，识别中还没有 dishId 时靠它对应。 */
data class OrderDish(
    val key: String,
    val nameOriginal: String,
    val nameTranslated: String,
    val category: String,
    val price: Money?,
    /** 模型推测的过敏原代码。 */
    val allergens: List<String>,
    val dishId: String?,
)

data class OrderMenu(
    /** 已有店铺，或新店已存好；新店还没存时为 null。 */
    val placeId: String?,
    val placeName: String,
    val location: Pair<Double, Double>?,
    /** 菜单原文语言（BCP 47），决定小票用什么语言。 */
    val sourceLang: String?,
    val currency: String?,
    val dishes: List<OrderDish>,
    val status: MenuStatus,
    val error: ScanError? = null,
    val estimatedDishCount: Int? = null,
)

data class OrderLine(val dish: OrderDish, val qty: Int)
