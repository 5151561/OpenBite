package com.openbite.core.domain

import com.openbite.core.model.Dish
import com.openbite.core.model.Money
import com.openbite.core.model.ScannedDish

/** 合并结果：[upserts] 原样交给数据库 upsert；不在里面的旧菜原样保留。 */
data class MenuMergeResult(
    val upserts: List<Dish>,
    val added: Int,
    val updated: Int,
    /** 这次没拍到、但还留在菜单上的旧菜数。 */
    val kept: Int,
)

/** 比较菜名用：去掉首尾空白，全角空格和连续空白算一个空格。 */
fun normalizeDishName(name: String): String = name.replace('\u3000', ' ').trim().replace(Regex("\\s+"), " ")

/**
 * 把一次识别合并进店铺现有的菜单（补拍 / 菜单换了）：
 * - 按原名（[normalizeDishName]）在同一家店里匹配；
 * - 匹配上的更新译名、分类、价格、过敏原，并改挂到这次的照片上；新结果里缺的字段保留旧值；
 * - 没匹配上的作为新菜加进来；
 * - 从不删除旧菜。被用餐记录引用的菜因此一定还在，没被引用、这次没拍到的也先留着。
 * 同一次识别里重复出现的菜只算第一次。
 */
fun mergeMenu(
    placeId: String,
    scanId: String,
    existing: List<Dish>,
    scanned: List<ScannedDish>,
    defaultCurrency: String?,
    newId: () -> String,
): MenuMergeResult {
    val byName = existing.associateBy { normalizeDishName(it.nameOriginal) }
    val seen = mutableSetOf<String>()
    var added = 0
    var updated = 0
    val upserts = scanned.mapNotNull { dish ->
        val key = normalizeDishName(dish.nameOriginal)
        if (key.isEmpty() || !seen.add(key)) return@mapNotNull null
        val currency = dish.currency?.uppercase() ?: defaultCurrency
        val price = dish.priceMinor?.takeIf { it >= 0 }?.let { amount -> currency?.let { Money(amount, it) } }
        val old = byName[key]
        if (old != null) {
            updated++
            old.copy(
                category = dish.category.ifBlank { old.category },
                nameTranslated = dish.nameTranslated.ifBlank { old.nameTranslated },
                price = price ?: old.price,
                allergens = dish.allergens.ifEmpty { old.allergens },
                scanId = scanId,
            )
        } else {
            added++
            Dish(
                id = newId(),
                placeId = placeId,
                category = dish.category.trim(),
                nameOriginal = key,
                nameTranslated = dish.nameTranslated.trim(),
                price = price,
                allergens = dish.allergens,
                scanId = scanId,
            )
        }
    }
    return MenuMergeResult(upserts, added, updated, kept = existing.size - updated)
}
