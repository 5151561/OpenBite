package com.openbite.core.domain

import com.openbite.core.model.Dish
import com.openbite.core.model.Money
import com.openbite.core.model.ScannedDish
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MenuMergeTest {
    private fun old(id: String, name: String, translated: String, price: Long?, scanId: String = "scan-1") =
        Dish(id, "p", "一品", name, translated, price?.let { Money(it, "JPY") }, listOf("soy"), scanId)

    private val existing = listOf(
        old("d-tsukune", "つくね", "鸡肉丸", 300),
        old("d-saba", "鯖の塩焼き", "盐烤鲭鱼", 780),
        // 被用餐记录引用、这次没拍到：必须保留。
        old("d-negima", "ねぎま", "葱鸡串", 280),
    )

    private fun ids(): () -> String {
        var n = 0
        return { "new-${++n}" }
    }

    @Test
    fun matchesByOriginalNameAndUpdatesTranslationAndPrice() {
        val result = mergeMenu(
            "p", "scan-2", existing,
            listOf(ScannedDish("焼き鳥", "つくね", "鸡肉丸串（蘸蛋黄）", 350, "JPY", listOf("egg"))),
            defaultCurrency = "JPY", newId = ids(),
        )
        val dish = result.upserts.single()
        assertEquals("d-tsukune", dish.id)
        assertEquals("鸡肉丸串（蘸蛋黄）", dish.nameTranslated)
        assertEquals(Money(350, "JPY"), dish.price)
        assertEquals(listOf("egg"), dish.allergens)
        assertEquals("焼き鳥", dish.category)
        assertEquals("scan-2", dish.scanId)
        assertEquals(1, result.updated)
        assertEquals(0, result.added)
    }

    @Test
    fun newDishesAreAddedWithNewIdsAndDefaultCurrency() {
        val result = mergeMenu("p", "scan-2", existing, listOf(ScannedDish("一品", "冷奴", "冷豆腐", 380)), "JPY", ids())
        val dish = result.upserts.single()
        assertEquals("new-1", dish.id)
        assertEquals("p", dish.placeId)
        assertEquals(Money(380, "JPY"), dish.price)
        assertEquals(1, result.added)
    }

    @Test
    fun neverDeletesAndKeepsMissingDishes() {
        val result = mergeMenu("p", "scan-2", existing, listOf(ScannedDish(nameOriginal = "鯖の塩焼き")), "JPY", ids())
        // upserts 只含这次拍到的；ねぎま 和 つくね 不在里面，数据库里原样保留。
        assertEquals(listOf("d-saba"), result.upserts.map { it.id })
        assertEquals(2, result.kept)
        assertTrue(result.upserts.none { it.id == "d-negima" })
    }

    @Test
    fun missingFieldsKeepOldValues() {
        val result = mergeMenu("p", "scan-2", existing, listOf(ScannedDish(nameOriginal = "鯖の塩焼き")), null, ids())
        val dish = result.upserts.single()
        assertEquals("盐烤鲭鱼", dish.nameTranslated)
        assertEquals(Money(780, "JPY"), dish.price)
        assertEquals(listOf("soy"), dish.allergens)
        assertEquals("一品", dish.category)
    }

    @Test
    fun nameMatchIgnoresWhitespaceAndDuplicatesCountOnce() {
        val result = mergeMenu(
            "p", "scan-2", existing,
            listOf(
                ScannedDish(nameOriginal = "鯖の塩焼き\u3000", priceMinor = 800),
                ScannedDish(nameOriginal = "生ビール", priceMinor = 550),
                ScannedDish(nameOriginal = " 生ビール ", priceMinor = 600),
                ScannedDish(nameOriginal = "  "),
            ),
            "JPY", ids(),
        )
        assertEquals(listOf("d-saba", "new-1"), result.upserts.map { it.id })
        assertEquals(Money(550, "JPY"), result.upserts[1].price)
        assertEquals("生ビール", result.upserts[1].nameOriginal)
    }

    @Test
    fun priceWithoutAnyCurrencyIsDropped() {
        val result = mergeMenu("p", "s", emptyList(), listOf(ScannedDish(nameOriginal = "水", priceMinor = 100)), null, ids())
        assertEquals(null, result.upserts.single().price)
    }

    @Test
    fun updatedDishIsTrimmedLikeNewDish() {
        val result = mergeMenu(
            "p", "scan-2", existing,
            listOf(
                ScannedDish(" 焼き鳥 ", "つくね", "  鸡肉丸串 ", 350, "JPY"),
                ScannedDish(" 焼き鳥 ", "皮", " 鸡皮串 ", 250, "JPY"),
            ),
            defaultCurrency = "JPY", newId = ids(),
        )
        val (updated, added) = result.upserts
        // 更新已有菜和新加的菜一样去掉首尾空白，界面分组不会因为空格分裂。
        assertEquals("焼き鳥", updated.category)
        assertEquals("鸡肉丸串", updated.nameTranslated)
        assertEquals("焼き鳥", added.category)
        assertEquals("鸡皮串", added.nameTranslated)
    }

    @Test
    fun blankFieldsWithOnlySpacesKeepOldValues() {
        val dish = mergeMenu(
            "p", "scan-2", existing,
            listOf(ScannedDish("  ", "つくね", "   ", null, null)),
            defaultCurrency = "JPY", newId = ids(),
        ).upserts.single()
        assertEquals("一品", dish.category)
        assertEquals("鸡肉丸", dish.nameTranslated)
        assertEquals(Money(300, "JPY"), dish.price)
    }

    @Test
    fun lowercaseCurrencyIsUppercasedAndNegativePriceDropped() {
        val result = mergeMenu(
            "p", "scan-2", emptyList(),
            listOf(
                ScannedDish("一品", "冷奴", "凉拌豆腐", 380, "jpy"),
                ScannedDish("一品", "枝豆", "毛豆", -1, "JPY"),
                ScannedDish("一品", "お通し", "小菜", 300, null),
            ),
            defaultCurrency = "JPY", newId = ids(),
        )
        val (tofu, edamame, otoshi) = result.upserts
        assertEquals(Money(380, "JPY"), tofu.price)
        assertEquals(null, edamame.price)
        // 没写币种用菜单抬头的。
        assertEquals(Money(300, "JPY"), otoshi.price)
    }
}
